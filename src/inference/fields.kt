package kanva.inference

import kanva.analysis.buildCFG
import kanva.annotations.Nullability
import kanva.context.Context
import kanva.graphs.Node
import kanva.declarations.Method
import kanva.declarations.isConstructor
import kanva.declarations.isClassInitializer
import kanva.analysis.collectNotNullFinalFields
import kanva.declarations.getFieldPosition
import kanva.graphs.successors
import kanva.inference.stepParamComponent

fun inferFields(context: Context, components: List<Set<Node<Method>>>) {
    for (component in components) {
        fixPointFieldsComponent(context, component)
    }
    println("${context.annotations.size()} annotations inferred")
}

fun fixPointFieldsComponent(context: Context, component: Set<Node<Method>>) {
    while (stepFieldsComponent(context, component)){}
}


fun stepFieldsComponent(context: Context, component: Set<Node<Method>>): Boolean {
    var changed = false
    for (node in component) {
        val method = node.data
        val methodNode = context.index.methods[method]!!
        // todo - as for constructors we should consider a family of constructors
        if (method.isConstructor() || method.isClassInitializer()) {
            val cfg = buildCFG(method, methodNode)
            val notFullFields = collectNotNullFinalFields(context, cfg, method, methodNode)
            for (field in notFullFields) {
                val fieldPos = getFieldPosition(field)
                if (context.annotations[fieldPos] == null) {
                    changed = true
                    context.annotations[getFieldPosition(field)] = Nullability.NOT_NULL
                }
            }
        }
    }
    return changed
}