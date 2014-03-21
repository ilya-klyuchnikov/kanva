package kanva.analysis

import kanva.context.Context
import kanva.graphs.Graph
import kanva.declarations.Method
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.BasicValue
import kanva.graphs.Node
import java.util.HashSet
import org.objectweb.asm.tree.AbstractInsnNode
import kanva.graphs.successors
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import kanva.context.findMethodByMethodInsnNode
import kanva.declarations.PositionsForMethod
import kanva.declarations.RETURN_POSITION
import kanva.annotations.Nullability

fun analyzeReturn(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode): RefDomain =
        ReturnAnalyzer(context, cfg, method, methodNode).analyze()

// iteration #0 - graph without cycles,
// only using new
class ReturnAnalyzer(val context: Context, val cfg: Graph<Int>, val method: Method, val methodNode: MethodNode) {

    data class PendingState(val frame: Frame<BasicValue>, val node: Node<Int>): Comparable<PendingState> {
        override fun compareTo(other: PendingState): Int {
            return other.node.insnIndex - this.node.insnIndex
        }
    }

    fun analyze(): RefDomain {
        if (cfg.nodes.empty) {
            return RefDomain.NULL
        }
        val returnType = Type.getReturnType(methodNode.desc)
        if (returnType.getSort() < Type.ARRAY) {
            return RefDomain.NULL
        }
        val startFrame =
                createRefValueStartFrame(context, method, methodNode)
        return iterate(startFrame, cfg.findNode(0)!!)
    }

    private fun iterate(startFrame: Frame<BasicValue>, startNode: Node<Int>): RefDomain {
        var result: HashSet<TracedValue>? = null

        val queue = linkedListOf<PendingState>()
        var state: PendingState? =
                PendingState(startFrame, startNode)

        var iterations = 0
        var completedPaths = 0

        while (state != null) {
            iterations ++

            val (frame, node) = state!!

            if (iterations > 500000) {
                println(iterations)
                println("${method}")
                println("result: $result")
                println("${node.insnIndex}")
                println("completedPaths: $completedPaths")
                println("queue size: ${queue.size}")
                println("graph: ${cfg.nodes.size}")
                // too BIG
                return RefDomain.NULL
            }

            val insnNode = methodNode.instructions[node.insnIndex]
            val insnType = insnNode.getType()
            val opcode = insnNode.getOpcode()
            // TODO: extract
            val isIdle =
                    (insnType == AbstractInsnNode.LABEL ||
                    insnType == AbstractInsnNode.LINE ||
                    insnType == AbstractInsnNode.FRAME)

            val nextFrame = Frame(frame)
            if (!isIdle) {
                nextFrame.execute(insnNode, MyInterpreter(context))
            }

            val nextNodes = node.successors

            if (nextNodes.empty && opcode != Opcodes.ATHROW) {
                completedPaths ++

                val returnVal =  Frame(frame).pop()
                if (returnVal is RefValue && returnVal.domain == RefDomain.NOTNULL) {
                    // continues
                } else {
                    return RefDomain.ANY
                }
            }

            for (nextNode in nextNodes) {
                if (nextNode.insnIndex > node.insnIndex) {
                    queue.addFirst(PendingState(nextFrame, nextNode))
                } else {
                    // TODO - more complex method is needed
                    println("complex method: $method")
                    return RefDomain.ANY
                }
            }

            state = queue.poll()
        }

        return RefDomain.NOTNULL
    }
}

// context is used for annotations usage
private class MyInterpreter(val context: Context): BasicInterpreter() {

    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
        when (opCode) {
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
        // TODO - more
        when (opCode) {
            Opcodes.NEW ->
                return RefValue(RefDomain.NOTNULL, Type.getObjectType(((insn as TypeInsnNode)).desc))
        }
        return super.newOperation(insn);
    }

    public override fun binaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
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