package kanva.analysis

import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.BasicValue
import kanva.context.Context
import kanva.declarations.Method
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Frame
import kanva.declarations.PositionsForMethod
import kanva.declarations.isStatic
import kanva.declarations.ParameterPosition
import kanva.annotations.Nullability
import org.objectweb.asm.Opcodes
import kanva.graphs.Node

/** abstract nullability values */
enum class RefDomain {
    ANY
    NOTNULL
    NULL
}

data class RefValue(val domain: RefDomain, tp: Type?) : BasicValue(tp) {
    override fun equals(other: Any?) =
            (other is RefValue) && (domain == other.domain)
    override fun hashCode() =
            domain.hashCode()
}

// takes into account inferred/existing annotations
fun createRefValueStartFrame(context: Context, method: Method, methodNode: MethodNode): Frame<BasicValue> {
    val startFrame = Frame<BasicValue>(methodNode.maxLocals, methodNode.maxStack)
    val returnType = Type.getReturnType(methodNode.desc)

    val returnValue =
            if (returnType == Type.VOID_TYPE) null
            else RefValue(RefDomain.ANY, Type.getReturnType(methodNode.desc))

    startFrame.setReturn(returnValue)

    val methodPositions = PositionsForMethod(method)

    val shift = if (method.isStatic()) 0 else 1

    fun argDomain(i: Int): RefDomain {
        val argPosition = methodPositions.get(ParameterPosition(shift + i))
        return when (context.annotations[argPosition]) {
            Nullability.NOT_NULL ->
                RefDomain.NOTNULL
            else ->
                RefDomain.ANY
        }
    }

    val argsTypes = Type.getArgumentTypes(methodNode.desc)

    var local = 0
    if ((methodNode.access and Opcodes.ACC_STATIC) == 0) {
        startFrame.setLocal(local, RefValue(RefDomain.NOTNULL, Type.getObjectType(method.declaringClass.internal)))
        local++
    }
    for (i in 0..argsTypes.size - 1) {
        startFrame.setLocal(local, RefValue(argDomain(i), argsTypes[i]))
        local++
        if (argsTypes[i].getSize() == 2) {
            startFrame.setLocal(local, RefValue(RefDomain.ANY, null))
            local++
        }
    }

    while (local < methodNode.maxLocals) {
        startFrame.setLocal(local++, RefValue(RefDomain.ANY, null))
    }
    return startFrame
}

val Node<Int>.insnIndex: Int
    get() = data
