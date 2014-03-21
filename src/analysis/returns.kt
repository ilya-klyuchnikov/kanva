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
import org.objectweb.asm.tree.FieldInsnNode
import kanva.declarations.ClassName
import kanva.declarations.getFieldPosition
import kanva.declarations.isStatic
import kanva.declarations.ParameterPosition

fun analyzeReturn(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode): RefDomain =
        ReturnAnalyzer(context, cfg, method, methodNode).analyze()

fun createIdRefValueStartFrame(context: Context, method: Method, methodNode: MethodNode): Frame<IdRefValue> {
    val startFrame = Frame<IdRefValue>(methodNode.maxLocals, methodNode.maxStack)
    val returnType = Type.getReturnType(methodNode.desc)

    val returnValue =
            if (returnType == Type.VOID_TYPE) null
            else IdRefValue(RefDomain.ANY, Type.getReturnType(methodNode.desc))

    startFrame.setReturn(returnValue)

    val methodPositions = PositionsForMethod(method)

    val shift = if (method.isStatic()) 0 else 1

    fun argDomain(i: Int): RefDomain {
        val argPosition = methodPositions.get(ParameterPosition(shift + i))
        return when (context.annotations[argPosition]) {
            Nullability.NOT_NULL ->
                RefDomain.NOTNULL
            else ->
                RefDomain.ANY
        }
    }

    val argsTypes = Type.getArgumentTypes(methodNode.desc)

    var local = 0
    if ((methodNode.access and Opcodes.ACC_STATIC) == 0) {
        startFrame.setLocal(local, IdRefValue(RefDomain.NOTNULL, Type.getObjectType(method.declaringClass.internal)))
        local++
    }
    for (i in 0..argsTypes.size - 1) {
        startFrame.setLocal(local, IdRefValue(argDomain(i), argsTypes[i]))
        local++
        if (argsTypes[i].getSize() == 2) {
            startFrame.setLocal(local, IdRefValue(RefDomain.ANY, null))
            local++
        }
    }

    while (local < methodNode.maxLocals) {
        startFrame.setLocal(local++, IdRefValue(RefDomain.ANY, null))
    }
    return startFrame
}


class ReturnAnalyzer(val context: Context, val cfg: Graph<Int>, val method: Method, val methodNode: MethodNode) {

    data class PendingState(val frame: Frame<IdRefValue>, val node: Node<Int>): Comparable<PendingState> {
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
                createIdRefValueStartFrame(context, method, methodNode)
        return iterate(startFrame, cfg.findNode(0)!!)
    }

    private fun iterate(startFrame: Frame<IdRefValue>, startNode: Node<Int>): RefDomain {
        var result: HashSet<TracedValue>? = null

        val queue = linkedListOf<PendingState>()
        var state: PendingState? = PendingState(startFrame, startNode)

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
            // TODO: extract into utilities
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
                if (returnVal is IdRefValue && returnVal.domain == RefDomain.NOTNULL) {
                    // continues
                } else {
                    return RefDomain.ANY
                }
            }

            for (nextNode in nextNodes) {
                if (nextNode.insnIndex > node.insnIndex) {
                    queue.addFirst(PendingState(nextFrame, nextNode))
                } else {
                    // TODO - this is speculation
                    // BUT: if this speculation says ANY - then it cannot be NotNull
                    // println("complex method: $method")
                    //return RefDomain.ANY
                }
            }

            state = queue.poll()
        }

        return RefDomain.NOTNULL
    }
}

// context is used for annotations usage
private class MyInterpreter(val context: Context): IdRefBasicInterpreter() {

    public override fun unaryOperation(insn: AbstractInsnNode, value: IdRefValue): IdRefValue? {
        val result = super.newOperation(insn)
        when (insn.getOpcode()) {
            // TODO - mark receiver as NotNull
            Opcodes.GETFIELD -> {
                val fieldInsn = insn as FieldInsnNode
                val field = context.index.findField(ClassName.fromInternalName(fieldInsn.owner), fieldInsn.name)
                if (field != null && context.annotations[getFieldPosition(field)] == Nullability.NOT_NULL) {
                    result.notNull()
                }
            }
        }
        return result
    }

    public override fun newOperation(insn: AbstractInsnNode): IdRefValue {
        val result = super.newOperation(insn)
        when (insn.getOpcode()) {
            Opcodes.GETSTATIC -> {
                val fieldInsn = insn as FieldInsnNode
                val field = context.index.findField(ClassName.fromInternalName(fieldInsn.owner), fieldInsn.name)
                if (field != null && context.annotations[getFieldPosition(field)] == Nullability.NOT_NULL) {
                    result.notNull()
                }
            }
        }
        return result
    }

    public override fun binaryOperation(insn: AbstractInsnNode, value1: IdRefValue, value2: IdRefValue): IdRefValue? {
        return super.binaryOperation(insn, value1, value1)
    }

    public override fun ternaryOperation(insn: AbstractInsnNode, value1: IdRefValue, value2: IdRefValue, value3: IdRefValue): IdRefValue? {
        return super.ternaryOperation(insn, value1, value2, value3)
    }

    public override fun naryOperation(insn: AbstractInsnNode, values: List<IdRefValue>): IdRefValue {
        val result = super.naryOperation(insn, values)

        // TODO - mark receiver as NotNull
        if (insn is MethodInsnNode) {
            val method = context.findMethodByMethodInsnNode(insn)
            if (method!=null) {
                val methodPositions = PositionsForMethod(method)
                if (context.annotations[methodPositions.get(RETURN_POSITION)] == Nullability.NOT_NULL) {
                    result.notNull()
                }
            }
        }

        return result
    }
}
