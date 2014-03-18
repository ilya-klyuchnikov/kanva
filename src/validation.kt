package kanva.validation

import java.util.Date
import java.io.File
import java.util.HashMap
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.tree.MethodNode

import kanva.context.*
import kanva.declarations.*
import kanva.index.*
import kanva.util.*
import kanva.analysis.*

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
