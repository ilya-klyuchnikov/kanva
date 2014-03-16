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
import kanva.validation.buildCFG
import kanva.validation.collectNotNullParams
import kanva.context.Context

import org.spek.*

class NotNullCollectorSpek : Spek() {
    val testClass = javaClass<data.Instructions>()

    fun collect(methodName: String): Set<Int> {
        val internalName = Type.getInternalName(testClass)
        var methodNode: MethodNode? = null
        var method: Method? = null

        object DummyClassSource: ClassSource {
            override fun forEach(body: (ClassReader) -> Unit) {}
        }

        val context = Context(DummyClassSource, listOf())
        ClassReader(testClass.getCanonicalName()).accept(object : ClassVisitor(Opcodes.ASM4) {
            public override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name == methodName) {
                    method = Method(ClassName.fromInternalName(internalName), access, name, desc, signature)
                    methodNode = method!!.createMethodNodeStub()
                    return methodNode
                }
                return null
            }
        }, 0)
        val cfg = buildCFG(method!!, methodNode!!)
        val notNullParams = collectNotNullParams(context, cfg, method!!, methodNode!!)
        return notNullParams
    }

    fun allMethodParamIndices(methodName: String): Set<Int> {
        for (m in testClass.getMethods()) {
            if (m.getName() == methodName) {
                val arity = m.getParameterTypes()!!.size
                return (if (m.isStatic()) 0..arity-1 else 1..arity).toSet()
            }
        }
        throw IllegalArgumentException("method $methodName not found")
    }

    {
        given("NotNullCollector") {

            on("invokeMethod") {
                val methodName = "invokeMethod"
                val result = collect(methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(methodName), result)
                }
            }

            on("arrayLength") {
                val methodName = "arrayLength"
                val result = collect(methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(methodName), result)
                }
            }

            on("arrayLoad") {
                val methodName = "arrayLoad"
                val result = collect(methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(methodName), result)
                }
            }

            on("arrayStore") {
                val methodName = "arrayStore"
                val result = collect(methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(methodName), result)
                }
            }

            on("monitor") {
                val methodName = "monitor"
                val result = collect(methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(methodName), result)
                }
            }

            on("fields") {
                val methodName = "fields"
                val result = collect(methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(methodName), result)
                }
            }
        }
    }
}
