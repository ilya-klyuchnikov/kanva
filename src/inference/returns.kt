package kanva.inference

import kanva.annotations.Nullability
import kanva.context.Context
import kanva.graphs.Node
import kanva.declarations.Method
import kanva.graphs.successors
import kanva.declarations.*
import kanva.analysis.*

fun inferReturns(context: Context, components: List<Set<Node<Method>>>) {
    for (component in components) {
        fixPointReturnComponent(context, component)
    }
}

fun fixPointReturnComponent(context: Context, component: Set<Node<Method>>) {
    val single =
            when {
                component.size == 1 -> {
                    val node = component.first()
                    !node.successors.contains(node)
                }
                else ->
                    false
            }

    if (single) {
        stepMethodComponent(context, component)
    } else {
        while (stepMethodComponent(context, component)){}
    }
}


fun stepMethodComponent(context: Context, component: Set<Node<Method>>): Boolean {
    var changed = false
    for (node in component) {
        val method = node.data
        val returnPosition = PositionsForMethod(method).get(RETURN_POSITION)
        if (context.annotations[returnPosition] == null) {
            val methodNode = context.index.methods[method]!!
            val cfg = buildCFG(method, methodNode)
            val result = ReturnAnalyzer(context, cfg, method, methodNode).analyze()
            if (result == RefDomain.NOTNULL) {
                context.annotations[returnPosition] = Nullability.NOT_NULL
                changed = true
            }
        }

    }
    return changed
}
