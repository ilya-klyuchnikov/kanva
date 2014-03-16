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
    val errors = validate(jarFile, annotationsDir)

    println("ERRORS:")
    for (error in errors) {
        println(error)
    }

    println("${errors.size} errors")
    println(Date())
}

// validates external annotations against a jar-file
fun validate(jarFile: File, annotationsDir: File): Collection<AnnotationPosition> {
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
            // TODO - WHY?
            if (!methodNode.name.startsWith("access$")) {
                validateMethod(context, method, methodNode, errors)
            }
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

    val positions = context.findNotNullParamPositions(method)
    if (positions.empty) {
        return
    }

    val cfg = buildCFG(method, methodNode)
    val nonNullParams = collectNotNullParams(context, cfg, methodNode)

    for (pos in positions) {
        if (!nonNullParams.contains(pos.index)) {
            val returnReachable = reachable(context, cfg, methodNode, pos.index)
            if (returnReachable) {
                errorPositions.add(PositionsForMethod(method).get(pos))
            }
        }
    }
}


fun buildCFG(method: Method, methodNode: MethodNode): Graph<Int, *> =
        ControlFlowBuilder().buildCFG(method, methodNode)

fun collectNotNullParams(context: Context, cfg: Graph<Int, *>, methodNode: MethodNode): Set<Int> {
    val called = NotNullParametersCollector(context, cfg, methodNode).collectNotNulls()
    return called.paramIndices()
}

fun reachable(context: Context, cfg: Graph<Int, *>, methodNode: MethodNode, nullParam: Int): Boolean =
        ReachabilityAnalyzer(context, cfg, methodNode, nullParam).reachable()

private fun Set<TracedValue>.paramIndices(): Set<Int> =
        this.map{if (it.source is Arg) it.source.pos else null}.filterNotNull().toSet()

private class ControlFlowBuilder : Analyzer<BasicValue>(BasicInterpreter()) {
    private class CfgBuilder: GraphBuilder<Int, Int, Nothing?, GraphImpl<Int, Nothing?>>(true, true) {
        override fun newNode(data: Int) = DefaultNodeImpl<Int, Nothing?>(data)
        override fun newGraph() = GraphImpl<Int, Nothing?>(true)
    }

    private var builder = CfgBuilder()

    fun buildCFG(method: Method, methodNode: MethodNode): Graph<Int, Nothing?> {
        builder = CfgBuilder()
        analyze(method.declaringClass.internal, methodNode)
        return builder.graph
    }

    override protected fun newControlFlowEdge(insn: Int, successor: Int) {
        val fromNode = builder.getOrCreateNode(insn)
        val toNode = builder.getOrCreateNode(successor)
        builder.getOrCreateEdge(null, fromNode, toNode)
    }
}


abstract class Source(val asString: String) {
    override fun toString() = asString
}
private data class Arg(val pos: Int): Source("Arg($pos)")
private data object Unknown: Source("Unknown")
private data object ThisObject: Source("ThisObject")

data class TracedValue(val source: Source) : BasicValue(null) {
    override fun equals(other: Any?): Boolean =
            (other is TracedValue) && (source == other.source)

    override fun toString() = "TV($source)"
}

val Node<Int, *>.insnIndex: Int
    get() = data

data class PendingState(val frame: Frame<BasicValue>, val node: Node<Int, *>, val called: Set<TracedValue>)

class NotNullParametersCollector(val context: Context, val cfg: Graph<Int, *>, val method: MethodNode) {

    fun collectNotNulls(): Set<TracedValue> {
        if (cfg.nodes.empty) {
            return setOf()
        }
        val startFrame = createStartFrame(method)
        return collectNotNulls(startFrame, cfg.findNode(0)!!)
    }

    private fun collectNotNulls(startFrame: Frame<BasicValue>, startNode: Node<Int, *>): Set<TracedValue> {
        var result: HashSet<TracedValue>? = null

        val queue = linkedListOf<PendingState>()
        var state: PendingState? =
                PendingState(startFrame, startNode, setOf())

        var iterations = 0

        while (state != null) {
            iterations ++

            val (frame, node, called) = state!!

            if (iterations mod 1000000 == 0) {
                println(iterations)
                println("result: $result")
                println("calledSoFar: $called")
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

private class ReachabilityAnalyzer(val context: Context, val cfg: Graph<Int, *>, val method: MethodNode, val paramIndex: Int) {

    fun reachable(): Boolean {
        if (cfg.nodes.empty) {
            return true
        }
        val startFrame = createStartFrame(method)
        return check(startFrame, cfg.findNode(0)!!)
    }

    // TODO - in the same style
    private fun check(frame: Frame<BasicValue>, node: Node<Int, *>): Boolean {
        val insnNode = method.instructions[node.insnIndex]
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
        val nextNodes = node.successors
        if (nextNodes.empty) {
            return opcode != Opcodes.ATHROW
        }

        if (opcode == Opcodes.IFNONNULL && Frame(frame).pop() == TracedValue(Arg(paramIndex))) {
            return check(nextFrame, nextNodes.toList()[0])
        }

        if (opcode == Opcodes.IFNULL && Frame(frame).pop() == TracedValue(Arg(paramIndex))) {
            return check(nextFrame, nextNodes.toList()[1])
        }

        for (nextNode in nextNodes) {
            if (nextNode.insnIndex > node.insnIndex && check(nextFrame, nextNode)) {
                return true
            }
        }

        return false
    }

}

private fun createStartFrame(method: MethodNode): Frame<BasicValue> {
    val startFrame = Frame<BasicValue>(method.maxLocals, method.maxStack)
    val returnType = Type.getReturnType(method.desc)
    startFrame.setReturn(if (returnType != Type.VOID_TYPE) TracedValue(Unknown) else null)

    val args = Type.getArgumentTypes(method.desc)
    var local = 0
    var shift = 0
    if ((method.access and Opcodes.ACC_STATIC) == 0) {
        startFrame.setLocal(local, TracedValue(ThisObject))
        local++
        shift = 1
    }
    for (i in 0..args.size - 1) {
        startFrame.setLocal(local, TracedValue(Arg(i + shift)))
        local++
        if (args[i].getSize() == 2) {
            startFrame.setLocal(local, TracedValue(Unknown))
            local++
        }
    }
    while (local < method.maxLocals) {
        startFrame.setLocal(local++, TracedValue(Unknown))
    }
    return startFrame
}

// collect TracedValue's that should be @NotNull
// a trace value should be @NotNull if internals of this value are accessed (method/field)
// or it is passed to as an argument to some method position which is marked as @NotNull
private class NotNullCollectingInterpreter(val context: Context, val called: HashSet<TracedValue>): BasicInterpreter() {

    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opcode = insn.getOpcode()

        if (value is TracedValue) {
            when (opcode) {
                Opcodes.GETFIELD, Opcodes.ARRAYLENGTH, Opcodes.CHECKCAST ->
                    called.add(value)
            }
        }

        if (opcode == Opcodes.CHECKCAST) {
            return value
        }

        return super.unaryOperation(insn, value);
    }

    public override fun binaryOperation(insn: AbstractInsnNode, value1: BasicValue, value2: BasicValue): BasicValue? {
        val opcode = insn.getOpcode()

        if (value1 is TracedValue) {
            when (opcode) {
                Opcodes.AALOAD, Opcodes.PUTFIELD ->
                    called.add(value1)
            }
        }
        return super.binaryOperation(insn, value1, value2)
    }

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

