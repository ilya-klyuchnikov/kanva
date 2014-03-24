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
import kanva.analysis.normalReturnOnNullReachable
import kanva.context.Context
import kanva.index.ClassSource
import kanva.analysis.buildCFG
import org.junit.Assert
import org.junit.Test

class NormalReturnOnNullTest {
    val testClass = javaClass<data.Instructions>()

    fun reachable(methodName: String, param: Int): Boolean {
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

        return normalReturnOnNullReachable(
                Context(object: ClassSource {override fun forEach(body: (ClassReader) -> Unit) {}}, listOf()),
                buildCFG(method!!, methodNode!!),
                method!!,
                methodNode!!,
                param
        )
    }

    Test
    fun exceptions1() {
        Assert.assertFalse("exceptions1", reachable("exceptions1", 1))
        Assert.assertFalse("exceptions1", reachable("exceptions1", 2))
    }

    Test
    fun exceptions2() {
        Assert.assertFalse("exceptions2", reachable("exceptions2", 1))
    }

    Test
    fun instanceOf() {
        Assert.assertFalse("instanceOf", reachable("instanceOfException1", 1))
        Assert.assertFalse("instanceOf", reachable("instanceOfException2", 1))
    }
}
