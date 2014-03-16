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
import data.ShouldBeCollected

class NotNullCollectorSpek : Spek() {

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

    fun allMethodParamIndices(testClass: Class<*>, methodName: String): Set<Int> {
        for (m in testClass.getMethods()) {
            if (m.getName() == methodName) {
                val arity = m.getParameterTypes()!!.size
                return (if (m.isStatic()) 0..arity-1 else 1..arity).toSet()
            }
        }
        throw IllegalArgumentException("method $methodName not found")
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

    {
        given("Instructions.java") {
            val testClass = javaClass<data.Instructions>()
            on("invokeMethod") {
                val methodName = "invokeMethod"
                val result = collect(testClass, methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(testClass, methodName), result)
                }
            }

            on("arrayLength") {
                val methodName = "arrayLength"
                val result = collect(testClass, methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(testClass, methodName), result)
                }
            }

            on("arrayLoad") {
                val methodName = "arrayLoad"
                val result = collect(testClass, methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(testClass, methodName), result)
                }
            }

            on("arrayStore") {
                val methodName = "arrayStore"
                val result = collect(testClass, methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(testClass, methodName), result)
                }
            }

            on("monitor") {
                val methodName = "monitor"
                val result = collect(testClass, methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(testClass, methodName), result)
                }
            }

            on("fields") {
                val methodName = "fields"
                val result = collect(testClass, methodName)
                it("should collect all parameters") {
                    shouldEqual(allMethodParamIndices(testClass, methodName), result)
                }
            }
        }

        given("Methods.java") {
            val testClass = javaClass<data.Methods>()

            on("test01") {
                val methodName = "test01"
                val result = collect(testClass, methodName)
                it("should collect expected params") {
                    shouldEqual(expectedMethodParamIndices(testClass, methodName), result)
                }
            }

            on("test02") {
                val methodName = "test02"
                val result = collect(testClass, methodName)
                it("should collect expected params") {
                    shouldEqual(expectedMethodParamIndices(testClass, methodName), result)
                }
            }

            on("test03") {
                val methodName = "test03"
                val result = collect(testClass, methodName)
                it("should collect expected params") {
                    shouldEqual(expectedMethodParamIndices(testClass, methodName), result)
                }
            }
        }

    }
}
