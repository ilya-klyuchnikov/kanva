package kanva.analysis

import java.util.HashSet

import org.objectweb.asm.Type
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode

import kanva.context.*
import kanva.declarations.*
import kanva.graphs.*

fun collectNotNullParams(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode): Set<Int> {
    val called = NotNullParametersAnalyzer(context, cfg, method, methodNode).collectNotNulls()
    return called.paramIndices()
}

fun normalReturnOnNullReachable(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode, nullParam: Int): Boolean {
    return ThrowAnalyzer(context, cfg, method, methodNode, nullParam).normalReturnReachable()
}

private fun Set<TracedValue>.paramIndices(): Set<Int> =
        this.map{if (it.source is Param) it.source.pos else null}.filterNotNull().toSet()



abstract class Source(val asString: String) {
    override fun toString() = asString
}
private data class Param(val pos: Int): Source("Arg($pos)")
private data object Unknown: Source("Unknown")
private data object ThisObject: Source("ThisObject")

data class TracedValue(val source: Source, tp: Type?) : BasicValue(tp) {
    override fun equals(other: Any?): Boolean =
            other is TracedValue && source == other.source
    val str = "TV($source)"
    override fun toString() = str

    override fun hashCode(): Int = str.hashCode()
}

data class InstanceOfCheckValue(val ref: TracedValue, tp: Type?): BasicValue(tp) {
    override fun equals(other: Any?): Boolean =
            other is InstanceOfCheckValue && other.ref == ref
}


class NotNullParametersAnalyzer(val context: Context, val cfg: Graph<Int>, val m: Method, val method: MethodNode) {

    data class PendingState(val frame: Frame<BasicValue>, val node: Node<Int>, val called: Set<TracedValue>): Comparable<PendingState> {
        override fun compareTo(other: PendingState): Int {
            return other.node.insnIndex - this.node.insnIndex
        }
    }

    fun collectNotNulls(): Set<TracedValue> {
        if (cfg.nodes.empty) {
            return setOf()
        }
        val startFrame = createStartFrame(m.declaringClass.internal, method)
        return collectNotNulls(startFrame, cfg.findNode(0)!!)
    }

    private fun collectNotNulls(startFrame: Frame<BasicValue>, startNode: Node<Int>): Set<TracedValue> {
        var result: HashSet<TracedValue>? = null

        val queue = linkedListOf<PendingState>()
        var state: PendingState? =
                PendingState(startFrame, startNode, setOf())

        var iterations = 0
        var completedPaths = 0

        while (state != null) {
            iterations ++

            val (frame, node, called) = state!!

            if (iterations > 500000) {
                println(iterations)
                println("${m}")
                println("result: $result")
                println("${node.insnIndex}")
                println("calledSoFar: $called")
                println("completedPaths: $completedPaths")
                println("queue size: ${queue.size}")
                println("graph: ${cfg.nodes.size}")
                return setOf()
            }

            val insnNode = method.instructions[node.insnIndex]
            val insnType = insnNode.getType()
            val opcode = insnNode.getOpcode()
            val isIdle = (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME)

            val delta = hashSetOf<TracedValue>()
            val nextFrame = Frame(frame)
            if (!isIdle) {
                nextFrame.execute(insnNode, NotNullParametersCollectingInterpreter(context, delta))
            }

            val calledSoFar = (called + delta).toSet()
            val nextNodes = node.successors

            // todo - this is debatable
            if (nextNodes.empty && opcode != Opcodes.ATHROW) {
                completedPaths ++
                if (result == null) {
                    result = hashSetOf()
                    result!!.addAll(calledSoFar)
                } else {
                    result!!.retainAll(calledSoFar)
                }
            }

            // pruning
            if (result != null && result!!.empty)
                return hashSetOf()

            val curr = result
            val takeChildren = (curr == null) || curr.any { !calledSoFar.contains(it) }
            if (takeChildren) {
                for (nextNode in nextNodes) {
                    if (nextNode.insnIndex > node.insnIndex) {
                        queue.addFirst(PendingState(nextFrame, nextNode, calledSoFar))
                    }
                }
            }

            state = queue.poll()
        }
        return result ?: hashSetOf()
    }
}

// KAnnotator logic: when given param is null all paths result to exception
// and there is at least one path after null check
private class ThrowAnalyzer(
        val context: Context,
        val cfg: Graph<Int>,
        val method: Method,
        val methodNode: MethodNode,
        val paramIndex: Int
) {
    private class ReturnException() : Exception("Return found")

    class object {
        val RETURN_EXCEPTION = ReturnException()
    }

    /* returns true if there exists a normal path with ith param = null */
    fun normalReturnReachable(): Boolean {
        if (cfg.nodes.empty) {
            return true
        }
        val startFrame = createStartFrame(method.declaringClass.internal, methodNode)
        try {
            visit(startFrame, cfg.findNode(0)!!)
        } catch (e: ReturnException) {
            return true
        }
        return !nullTaken
    }

    var nullTaken = false
    var iterations = 0
    private fun visit(frame: Frame<BasicValue>, node: Node<Int>) {
        iterations ++
        if (iterations > 100000) {
            println("exception analyzer: $iterations iterations")
            throw RETURN_EXCEPTION
        }

        val insnNode = methodNode.instructions[node.insnIndex]
        val insnType = insnNode.getType()
        val transitInstr =
                (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME)
        val delta = hashSetOf<TracedValue>()
        val nextFrame =
                if (transitInstr) {
                    frame
                } else {
                    val executed = Frame(frame)
                    executed.execute(insnNode, NotNullParametersCollectingInterpreter(context, delta))
                    executed
                }

        if (delta.contains(TracedValue(Param(paramIndex), null))) {
            return
        }

        val opCode = insnNode.getOpcode()
        val nextNodes = node.successors.filter { it.insnIndex > node.insnIndex }


        if (opCode == Opcodes.IFNONNULL && Frame(frame).pop() == TracedValue(Param(paramIndex), null)) {
            nullTaken = true
            visit(nextFrame, nextNodes.toList().first())
        }
        else if (opCode == Opcodes.IFNULL && Frame(frame).pop() == TracedValue(Param(paramIndex), null)) {
            nullTaken = true
            visit(nextFrame, nextNodes.toList().last())
        }
        else if (opCode == Opcodes.IFEQ &&
                Frame(frame).pop() == InstanceOfCheckValue(TracedValue(Param(paramIndex), null), null)) {
            nullTaken = true
            visit(nextFrame, nextNodes.toList().last())
        }
        else if (opCode == Opcodes.IFNE &&
        Frame(frame).pop() == InstanceOfCheckValue(TracedValue(Param(paramIndex), null), null)) {
            // TODO - propagate that this is null!! - as with sets
            nullTaken = true
            visit(nextFrame, nextNodes.toList().first())
        }
        else if (nextNodes.notEmpty) {
            for (nextNode in nextNodes) {
                visit(nextFrame, nextNode)
            }
        }
        else if (opCode.isReturn()) {
            // pruning
            throw RETURN_EXCEPTION
        }
    }
}

fun Int.isReturn() =
    this == Opcodes.IRETURN ||
    this == Opcodes.LRETURN ||
    this == Opcodes.FRETURN ||
    this == Opcodes.DRETURN ||
    this == Opcodes.ARETURN ||
    this == Opcodes.RETURN

private fun createStartFrame(owner: String, method: MethodNode): Frame<BasicValue> {
    val startFrame = Frame<BasicValue>(method.maxLocals, method.maxStack)
    val returnType = Type.getReturnType(method.desc)

    val returnValue =
            if (returnType == Type.VOID_TYPE) null else TracedValue(Unknown, Type.getReturnType(method.desc))
    startFrame.setReturn(returnValue)

    val args = Type.getArgumentTypes(method.desc)
    var local = 0
    var shift = 0
    if ((method.access and Opcodes.ACC_STATIC) == 0) {
        startFrame.setLocal(local, TracedValue(ThisObject, Type.getObjectType(owner)))
        local++
        shift = 1
    }
    for (i in 0..args.size - 1) {
        startFrame.setLocal(local, TracedValue(Param(i + shift), args[i]))
        local++
        if (args[i].getSize() == 2) {
            startFrame.setLocal(local, TracedValue(Unknown, null))
            local++
        }
    }
    while (local < method.maxLocals) {
        startFrame.setLocal(local++, TracedValue(Unknown, null))
    }
    return startFrame
}

// collect TracedValue's that should be @NotNull
// a trace value should be @NotNull if internals of this value are accessed (method/field)
// or it is passed to as an argument to some method position which is marked as @NotNull
private class NotNullParametersCollectingInterpreter(
        val context: Context,
        val called: HashSet<TracedValue>
): BasicInterpreter() {

    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()

        if (value is TracedValue) {
            when (opCode) {
                Opcodes.GETFIELD,
                Opcodes.ARRAYLENGTH,
                Opcodes.MONITORENTER -> called.add(value)
            }
        }

        if (opCode == Opcodes.CHECKCAST) {
            return value
        }

        if (opCode == Opcodes.INSTANCEOF && value is TracedValue) {
            return InstanceOfCheckValue(value, Type.INT_TYPE)
        }

        return super.unaryOperation(insn, value);
    }

    public override fun binaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()

        if (v1 is TracedValue) {
            when (opCode) {
                Opcodes.IALOAD,
                Opcodes.LALOAD,
                Opcodes.FALOAD,
                Opcodes.DALOAD,
                Opcodes.AALOAD,
                Opcodes.BALOAD,
                Opcodes.CALOAD,
                Opcodes.SALOAD,
                Opcodes.PUTFIELD -> called.add(v1)
            }
        }
        return super.binaryOperation(insn, v1, v2)
    }

    public override fun ternaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue, v3: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()

        if (v1 is TracedValue) {
            when (opCode) {
                Opcodes.IASTORE,
                Opcodes.LASTORE,
                Opcodes.FASTORE,
                Opcodes.DASTORE,
                Opcodes.AASTORE,
                Opcodes.BASTORE,
                Opcodes.CASTORE,
                Opcodes.SASTORE -> called.add(v1)
            }
        }
        return super.ternaryOperation(insn, v1, v2, v3)
    }

    public override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
        val opcode = insn.getOpcode()

        if (opcode != Opcodes.INVOKESTATIC) {
            val receiver = values[0]
            if (receiver is TracedValue && receiver.source is Param) {
                called.add(receiver)
            }
        }

        if (insn is MethodInsnNode) {
            val method = context.findMethodByMethodInsnNode(insn)
            if (method!=null) {
                for (position in context.findNotNullParamPositions(method)) {
                    val index = position.index
                    val value = values[index]
                    if (value is TracedValue && value.source is Param) {
                        called.add(value)
                    }
                }
            }
        }

        return super.naryOperation(insn, values);
    }
}

