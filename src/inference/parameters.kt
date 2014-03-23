package kanva.inference

import kanva.analysis.*
import kanva.annotations.*
import kanva.context.*
import kanva.declarations.*
import kanva.dependencies.*
import kanva.graphs.*

fun inferParams(context: Context, components: List<Set<Node<Method>>>) {
    for (component in components) {
        fixPointParamComponent(context, component)
    }
    println("${context.annotations.size()} annotations inferred")
}

fun fixPointParamComponent(context: Context, component: Set<Node<Method>>) {
    while (stepParamComponent(context, component)){}
}

fun stepParamComponent(context: Context, component: Set<Node<Method>>): Boolean {
    var changed = false
    for (node in component) {
        val method = node.data
        val methodNode = context.index.methods[method]!!
        val cfg = buildCFG(method, methodNode)
        val methodPositions = PositionsForMethod(method)
        val notNulls = collectNotNullParams(context, cfg, method, methodNode)

        val skip = if (method.isStatic()) 0 else 1
        for (i in notNulls) {
            if (context.annotations[methodPositions.get(ParameterPosition(i))] != Nullability.NOT_NULL) {
                context.annotations[methodPositions.get(ParameterPosition(i))] = Nullability.NOT_NULL
                changed = true
            }
        }
        // exceptions
        val indices = (skip .. (method.getArgumentTypes().size + skip - 1)).toList()
        for (i in indices) {
            if (i !in notNulls && context.annotations[methodPositions.get(ParameterPosition(i))] != Nullability.NOT_NULL) {
                val normalReturn = normalReturnOnNullReachable(context, cfg, method, methodNode, i)
                if (!normalReturn) {
                    context.annotations[methodPositions.get(ParameterPosition(i))] = Nullability.NOT_NULL
                    changed = true
                }
            }
        }
    }
    return changed
}