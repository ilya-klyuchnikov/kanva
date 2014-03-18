package kanva.test

import org.objectweb.asm.ClassReader
import kanva.declarations.ClassName
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import kanva.declarations.Method
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type

import kanva.util.createMethodNodeStub
import kanva.index.ClassSource
import kanva.validation.*
import kanva.context.Context

import org.spek.*

class NormalReturnOnNullSpek : Spek() {

    fun test(testClass: Class<*>, methodName: String, param: Int): Boolean {
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

    {
        given("Instructions.java") {

            val testClass = javaClass<data.Instructions>()


            on("exceptions1") {
                val methodName = "exceptions1"

                val result1 = test(testClass, methodName, 1)
                it("should detect first parameter") {
                    shouldEqual(false, result1)
                }

                val result2 = test(testClass, methodName, 2)
                it("should detect second param") {
                    shouldEqual(false, result2)
                }
            }

            on("exceptions2") {
                val methodName = "exceptions2"

                val result1 = test(testClass, methodName, 1)
                it("should detect first parameter") {
                    shouldEqual(false, result1)
                }
            }

        }

    }
}
