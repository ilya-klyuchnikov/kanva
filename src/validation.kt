package kanva.validation

import java.util.HashSet

import java.io.File
import java.util.Date
import java.util.HashMap

import org.objectweb.asm.Type
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

import kanva.context.*
import kanva.declarations.*
import kanva.graphs.*
import kanva.index.*
import kanva.util.*

fun main(args: Array<String>) {
    println(Date())
    val jarFile = File("/Users/lambdamix/code/kannotator/lib/jdk/jre-7u12-windows-rt.jar")
    val annotationsDir = File("/Users/lambdamix/code/kannotator/jdk-annotations-inferred")
    val errors = validateLib(jarFile, annotationsDir)

    println("ERRORS:")
    for (error in errors) {
        println(error)
    }

    println("${errors.size} errors")
    println(Date())
}

// validates external annotations against a jar-file
fun validateLib(jarFile: File, annotationsDir: File): Collection<AnnotationPosition> {
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf(annotationsDir))
    val errors = arrayListOf<AnnotationPosition>()

    class MethodNodesCollector(val className: ClassName): ClassVisitor(Opcodes.ASM4) {
        val methods = HashMap<Method, MethodNode>()
        public override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val method = Method(className, access, name, desc, signature)
            val methodNode = method.createMethodNodeStub()
            methods[method] = methodNode
            return methodNode
        }
    }

    context.classSource.forEach { classReader ->
        val collector = MethodNodesCollector(ClassName.fromInternalName(classReader.getClassName()))
        classReader.accept(collector, 0)

        for ((method, methodNode) in collector.methods) {
            validateMethod(context, method, methodNode, errors)
        }
    }
    return errors
}

fun validateMethod(
        context: Context,
        method: Method,
        methodNode: MethodNode,
        errorPositions: MutableCollection<AnnotationPosition>
) {
    if (method.access.isNative() || method.access.isAbstract()) {
        return
    }

    val cfg = buildCFG(method, methodNode)
    val nonNullParams = collectNotNullParams(context, cfg, method, methodNode)

    val positions = context.findNotNullParamPositions(method)
    if (positions.empty) {
        return
    }

    for (pos in positions) {
        if (!nonNullParams.contains(pos.index)) {
            val returnReachable = normalReturnOnNullReachable(context, cfg, method, methodNode, pos.index)
            if (returnReachable) {
                errorPositions.add(PositionsForMethod(method).get(pos))
            }
        }
    }
}

fun buildCFG(method: Method, methodNode: MethodNode): Graph<Int> =
        ControlFlowBuilder().buildCFG(method, methodNode)

fun collectNotNullParams(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode): Set<Int> {
    val called = NotNullParametersCollector(context, cfg, method, methodNode).collectNotNulls()
    return called.paramIndices()
}

fun normalReturnOnNullReachable(context: Context, cfg: Graph<Int>, method: Method, methodNode: MethodNode, nullParam: Int): Boolean {
    try {
        return ReachabilityAnalyzer(context, cfg, method, methodNode, nullParam).reachable()
    } catch (e : Throwable) {
        println("failure: $method")
        e.printStackTrace()
        return true
    }
}

private fun Set<TracedValue>.paramIndices(): Set<Int> =
        this.map{if (it.source is Arg) it.source.pos else null}.filterNotNull().toSet()

private class ControlFlowBuilder : Analyzer<BasicValue>(BasicInterpreter()) {
    private class CfgBuilder: GraphBuilder<Int, Int, GraphImpl<Int>>(true, true) {
        override fun newNode(data: Int) = DefaultNodeImpl<Int>(data)
        override fun newGraph() = GraphImpl<Int>(true)
    }

    private var builder = CfgBuilder()

    fun buildCFG(method: Method, methodNode: MethodNode): Graph<Int> {
        builder = CfgBuilder()
        analyze(method.declaringClass.internal, methodNode)
        return builder.graph
    }

    override protected fun newControlFlowEdge(insn: Int, successor: Int) {
        val fromNode = builder.getOrCreateNode(insn)
        val toNode = builder.getOrCreateNode(successor)
        builder.getOrCreateEdge(fromNode, toNode)
    }
}


abstract class Source(val asString: String) {
    override fun toString() = asString
}
private data class Arg(val pos: Int): Source("Arg($pos)")
private data object Unknown: Source("Unknown")
private data object ThisObject: Source("ThisObject")

data class TracedValue(val source: Source, tp: Type?) : BasicValue(tp) {
    override fun equals(other: Any?): Boolean =
            (other is TracedValue) && (source == other.source)

    override fun toString() = "TV($source)"
}

val Node<Int>.insnIndex: Int
    get() = data

data class PendingState(val frame: Frame<BasicValue>, val node: Node<Int>, val called: Set<TracedValue>): Comparable<PendingState> {
    override fun compareTo(other: PendingState): Int {
        return other.node.insnIndex - this.node.insnIndex
    }
}

class NotNullParametersCollector(val context: Context, val cfg: Graph<Int>, val m: Method, val method: MethodNode) {

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
                nextFrame.execute(insnNode, NotNullCollectingInterpreter(context, delta))
            }

            val calledSoFar = (called + delta).toSet()
            val nextNodes = node.successors

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

private class ReachabilityAnalyzer(
        val context: Context,
        val cfg: Graph<Int>,
        val method: Method,
        val methodNode: MethodNode,
        val paramIndex: Int) {

    fun reachable(): Boolean {
        if (cfg.nodes.empty) {
            return true
        }
        val startFrame = createStartFrame(method.declaringClass.internal, methodNode)
        return check(startFrame, cfg.findNode(0)!!, false)
    }

    private fun check(frame: Frame<BasicValue>, node: Node<Int>, nullArg: Boolean): Boolean {
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
                    executed.execute(insnNode, NotNullCollectingInterpreter(context, delta))
                    executed
                }

        val opcode = insnNode.getOpcode()
        val nextNodes = node.successors.filter { it.insnIndex > node.insnIndex }
        // TODO - cleanup null
        if (opcode == Opcodes.IFNONNULL && Frame(frame).pop() == TracedValue(Arg(paramIndex), null)) {
            return check(nextFrame, nextNodes.toList().first(), true)
        }

        if (opcode == Opcodes.IFNULL && Frame(frame).pop() == TracedValue(Arg(paramIndex), null)) {
            return check(nextFrame, nextNodes.toList().last(), true)
        }

        if (nextNodes.empty) {
            if (nullArg) {
                val result =  opcode != Opcodes.ATHROW
                println("result : $result")
                return result
            }
            return true
        }

        for (nextNode in nextNodes) {
            if (check(nextFrame, nextNode, nullArg)) {
                return true
            }
        }

        return false
    }

}

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
        startFrame.setLocal(local, TracedValue(Arg(i + shift), args[i]))
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
private class NotNullCollectingInterpreter(val context: Context, val called: HashSet<TracedValue>): BasicInterpreter() {

    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()

        if (value is TracedValue) {
            when (opCode) {
                Opcodes.GETFIELD,
                Opcodes.ARRAYLENGTH,
                Opcodes.CHECKCAST,
                Opcodes.MONITORENTER ->
                    called.add(value)
            }
        }

        if (opCode == Opcodes.CHECKCAST) {
            return value
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
                Opcodes.PUTFIELD ->
                    called.add(v1)
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
                Opcodes.SASTORE ->
                    called.add(v1)
            }
        }
        return super.ternaryOperation(insn, v1, v2, v3)
    }

    // TODO: generate extra constraints for inference - dependencies of called methods
    public override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
        val opcode = insn.getOpcode()

        if (opcode != Opcodes.INVOKESTATIC) {
            val receiver = values[0]
            if (receiver is TracedValue && receiver.source is Arg) {
                called.add(receiver)
            }
        }

        if (insn is MethodInsnNode) {
            val method = context.findMethodByMethodInsnNode(insn)
            if (method!=null) {
                for (position in context.findNotNullParamPositions(method)) {
                    val index = position.index
                    val value = values[index]
                    if (value is TracedValue && value.source is Arg) {
                        called.add(value)
                    }
                }
            }
        }

        return super.naryOperation(insn, values);
    }
}

