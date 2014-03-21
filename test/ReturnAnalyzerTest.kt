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
import kanva.context.Context
import kanva.index.ClassSource
import kanva.analysis.buildCFG
import kanva.analysis.analyzeReturn
import kanva.analysis.RefDomain
import org.junit.Test
import org.junit.Assert
import org.junit.Ignore

class ReturnAnalyzerTest {
    val testClass = javaClass<data.Returns>()

    fun inferNotNull(methodName: String): Boolean {
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

        return analyzeReturn(
                Context(object: ClassSource {override fun forEach(body: (ClassReader) -> Unit) {}}, listOf()),
                buildCFG(method!!, methodNode!!),
                method!!,
                methodNode!!
        ) == RefDomain.NOTNULL
    }

    Ignore
    Test
    fun simpleFor() {
        Assert.assertTrue("foreach", inferNotNull("simpleFor"))
    }
}