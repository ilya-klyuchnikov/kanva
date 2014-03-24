package kanva.analysis

import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Frame

import kanva.context.*
import kanva.declarations.*
import kanva.graphs.*
import kanva.util.*
import kanva.annotations.Nullability
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import java.util.HashMap

fun collectWrites(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode): Map<Field, RefDomain> {
    val analyzer = FieldWritesAnalyzer(context, cfg, method, methodNode)
    return analyzer.analyze()
}

class FieldWritesAnalyzer(
        val context: Context,
        val cfg: Graph<Int>,
        val method: Method,
        val methodNode: MethodNode
) {

    val result: HashMap<Field, RefDomain> = hashMapOf()

    data class PendingState(
            // instruction node
            val node: Node<Int>,
            // frame state before instruction
            val frame: Frame<BasicValue>
    )

    // returns a set of fields that are set to not null in this method
    fun analyze(): Map<Field, RefDomain> {
        if (cfg.nodes.notEmpty) {
            val startFrame = createRefValueStartFrame(context, method, methodNode)
            iterate(startFrame, cfg.findNode(0)!!)
        }
        return result
    }

    private fun iterate(startFrame: Frame<BasicValue>, startNode: Node<Int>) {
        val queue = linkedListOf<PendingState>()
        var state: PendingState? =
                PendingState(startNode, startFrame)

        var iterations = 0
        var completedPaths = 0

        while (state != null) {
            iterations ++

            var (node, frame) = state!!

            if (iterations > 50000) {
                result.clear()
                return
            }

            val insnNode = methodNode.instructions[node.insnIndex]
            val insnType = insnNode.getType()
            val opcode = insnNode.getOpcode()
            val isIdle =
                    insnType == AbstractInsnNode.LABEL ||
                    insnType == AbstractInsnNode.LINE ||
                    insnType == AbstractInsnNode.FRAME

            val nextFrame = Frame(frame)
            if (!isIdle) {
                val interpreter = FieldWritesInterpreter(context)
                nextFrame.execute(insnNode, interpreter)
                val delta = interpreter.write
                if (delta != null) {
                    val oldDomain = result[delta.first]
                    if (oldDomain == null) {
                        result[delta.first] = delta.second
                    } else {
                        result[delta.first] = merge(oldDomain, delta.second)
                    }
                }
            }

            val nextNodes = node.successors

            if (nextNodes.empty && opcode.isReturn()) {
                completedPaths ++
            }

            for (nextNode in nextNodes) {
                if (nextNode.insnIndex > node.insnIndex) {
                    queue.addFirst(PendingState(nextNode, nextFrame))
                }
            }

            state = queue.poll()
        }
    }
}

fun merge(ref1: RefDomain, ref2: RefDomain): RefDomain {
    return if (ref1 == RefDomain.NOTNULL && ref2 == RefDomain.NOTNULL)
            RefDomain.NOTNULL
        else
            RefDomain.ANY
}

private class FieldWritesInterpreter(val context: Context): BasicInterpreter() {

    var write: Pair<Field, RefDomain>? = null

    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
        when (opCode) {
            Opcodes.PUTSTATIC -> {
                    val fieldInsn = insn as FieldInsnNode
                    val field = context.index.findField(
                            ClassName.fromInternalName(fieldInsn.owner),
                            fieldInsn.name
                    )
                    if (field != null && field.isFinal() && !field.getType().isPrimitiveOrVoidType()) {
                        if (value is RefValue && value.domain == RefDomain.NOTNULL) {
                            write = field to RefDomain.NOTNULL
                        } else {
                            write = field to RefDomain.ANY
                        }
                    }
                }
            // todo - we can call super and reuse results
            Opcodes.NEWARRAY ->
                when (((insn as IntInsnNode)).operand) {
                    Opcodes.T_BOOLEAN ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[Z"))
                    Opcodes.T_CHAR ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[C"))
                    Opcodes.T_BYTE ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[B"))
                    Opcodes.T_SHORT ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[S"))
                    Opcodes.T_INT ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[I"))
                    Opcodes.T_FLOAT ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[F"))
                    Opcodes.T_DOUBLE  ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[D"))
                    Opcodes.T_LONG ->
                        return RefValue(RefDomain.NOTNULL, Type.getType("[J"))
                }
            Opcodes.ANEWARRAY -> {
                val desc = ((insn as TypeInsnNode)).desc
                return RefValue(RefDomain.NOTNULL, Type.getType("[" + Type.getObjectType(desc)))
            }
        }

        return super.unaryOperation(insn, value);
    }

    public override fun newOperation(insn: AbstractInsnNode): BasicValue? {
        val opCode = insn.getOpcode()
        when (opCode) {
            Opcodes.NEW ->
                return RefValue(RefDomain.NOTNULL, Type.getObjectType(((insn as TypeInsnNode)).desc))
            Opcodes.GETSTATIC -> {
                val fieldInsn = insn as FieldInsnNode
                val field = context.index.findField(ClassName.fromInternalName(fieldInsn.owner),fieldInsn.name)
                if (field != null ) {
                    val pos = getFieldPosition(field)
                    if (context.annotations[pos] == Nullability.NOT_NULL) {
                        return RefValue(RefDomain.NOTNULL, Type.getType(fieldInsn.desc))
                    }
                }
            }
            Opcodes.LDC -> {
                val basicValue = super.newOperation(insn)!!
                return RefValue(RefDomain.NOTNULL, basicValue.getType())
            }
        }
        return super.newOperation(insn);
    }

    public override fun binaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
        when (opCode) {
            Opcodes.PUTFIELD -> {
                    val fieldInsn = insn as FieldInsnNode
                    val field = context.index.findField(ClassName.fromInternalName(fieldInsn.owner),fieldInsn.name)
                    if (field != null && field.isFinal() && !field.getType().isPrimitiveOrVoidType()) {
                        if (v2 is RefValue && v2.domain == RefDomain.NOTNULL) {
                            write = field to RefDomain.NOTNULL
                        } else {
                            write = field to RefDomain.ANY
                        }
                    }
                }
        }
        return super.binaryOperation(insn, v1, v2)
    }

    public override fun ternaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue, v3: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
        return super.ternaryOperation(insn, v1, v2, v3)
    }

    public override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
        val opcode = insn.getOpcode()

        if (insn is MethodInsnNode) {
            val method = context.findMethodByMethodInsnNode(insn)
            if (method!=null) {
                val methodPositions = PositionsForMethod(method)
                if (context.annotations[methodPositions.get(RETURN_POSITION)] == Nullability.NOT_NULL) {
                    return RefValue(RefDomain.NOTNULL, Type.getType(insn.desc))
                }
            }
        }

        if (opcode == Opcodes.MULTIANEWARRAY) {
            return RefValue(RefDomain.NOTNULL, Type.getType((insn as MultiANewArrayInsnNode).desc))
        }
        return super.naryOperation(insn, values);
    }
}