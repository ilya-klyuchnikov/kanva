package kanva.test

import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import kanva.declarations.Method
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.MethodVisitor
import kanva.declarations.ClassName
import kanva.util.createMethodNodeStub
import kanva.analysis.collectNotNullParams
import kanva.context.Context
import kanva.index.ClassSource
import kanva.analysis.buildCFG
import org.junit.Assert
import org.junit.Test
import data.ShouldBeCollected

class NotNullCollectorTest {
    val testClass = javaClass<data.Instructions>()

    fun collect(testClass: Class<*>, methodName: String): Set<Int> {
        val internalName = Type.getInternalName(testClass)
        var methodNode: MethodNode? = null
        var method: Method? = null

        ClassReader(testClass.getCanonicalName()).accept(object : ClassVisitor(Opcodes.ASM4) {
            public override fun visitMethod(
                    access: Int,
                    name: String,
                    desc: String,
                    signature: String?,
                    exceptions: Array<out String>?
            ): MethodVisitor? {
                if (name == methodName) {
                    method = Method(ClassName.fromInternalName(internalName), access, name, desc, signature)
                    methodNode = method!!.createMethodNodeStub()
                    return methodNode
                }
                return null
            }
        }, 0)

        return collectNotNullParams(
                Context(object: ClassSource {override fun forEach(body: (ClassReader) -> Unit) {}}, listOf()),
                buildCFG(method!!, methodNode!!),
                method!!,
                methodNode!!
        )
    }

    fun expectedMethodParamIndices(testClass: Class<*>, methodName: String): Set<Int> {
        for (m in testClass.getMethods()) {
            if (m.getName() == methodName) {
                val annotations = m.getParameterAnnotations()
                val shift = if (m.isStatic()) 0 else 1
                val result = hashSetOf<Int>()
                for ((i, anns) in annotations.withIndices()) {
                    if (anns.map { it.annotationType() }.toList().contains(javaClass<ShouldBeCollected>())) {
                        result.add(i + shift)
                    }
                }
                return result
            }
        }
        throw IllegalArgumentException("method $methodName not found")
    }

    fun doTest(methodName: String) {
        val result = collect(testClass, methodName)
        val expected = expectedMethodParamIndices(testClass, methodName)
        Assert.assertEquals(expected, result)
    }

    //// instructions

    Test
    fun invokeMethod() =
            doTest("invokeMethod")

    Test
    fun arrayLength() =
            doTest("arrayLength")

    Test
    fun arrayLoad() =
            doTest("arrayLoad")

    Test
    fun arrayStore() =
            doTest("arrayStore")

    Test
    fun monitor() =
            doTest("monitor")

    Test
    fun fields() =
            doTest("fields")

    //// pure Branching

    Test
    fun pureBranching01() =
            doTest("pureBranching01")

    Test
    fun pureBranching02() =
            doTest("pureBranching02")

    Test
    fun pureBranching03() =
            doTest("pureBranching03")

}
