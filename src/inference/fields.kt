package kanva.inference

import kanva.analysis.buildCFG
import kanva.annotations.Nullability
import kanva.context.Context
import kanva.graphs.Node
import kanva.declarations.Method
import kanva.declarations.isConstructor
import kanva.declarations.isClassInitializer
import kanva.analysis.collectWrites
import kanva.declarations.getFieldPosition
import kanva.declarations.isFinal
import kanva.declarations.getType
import kanva.util.isPrimitiveOrVoidType
import kanva.declarations.Field
import kanva.analysis.RefDomain
import kanva.analysis.merge
import kanva.analysis.RefDomain.NOTNULL

/** simplest annotations */
fun inferSimpleFields(context: Context) {
    for ((field, fieldNode) in context.index.fields) {
        if (field.isFinal() && !field.getType().isPrimitiveOrVoidType() && field.value != null) {
            context.annotations[getFieldPosition(field)] = Nullability.NOT_NULL
        }
    }
}

fun inferFields(context: Context, components: List<Set<Node<Method>>>) {
    for (component in components) {
        val closure = closeConstructors(context, component)
        fixPointFieldsComponent(context, closure)
    }
    println("${context.annotations.size()} annotations inferred")
}

fun fixPointFieldsComponent(context: Context, component: Set<Method>) {
    while (stepFieldsComponent(context, component)){}
}


fun closeConstructors(context: Context, component: Set<Node<Method>>): Set<Method> {
    val result = hashSetOf<Method>()
    for (node in component) {
        val method = node.data
        when {
            method.isClassInitializer() -> {
                result.add(method)
            }
            method.isConstructor() -> {
                val className = method.declaringClass
                result.addAll(context.index.classes[className]!!.constructors)
            }
        }
    }
    return result
}


fun stepFieldsComponent(context: Context, component: Set<Method>): Boolean {

    val fieldWrites = hashMapOf<Field, RefDomain>()
    for (method in component) {
        val methodNode = context.index.methods[method]!!

        if (method.isClassInitializer() || method.isConstructor()) {
            val cfg = buildCFG(method, methodNode)
            val writes = collectWrites(context, cfg, method, methodNode)
            for ((field, domain) in writes) {
                val prevDomain = fieldWrites[field]
                if (prevDomain == null) {
                    fieldWrites[field] = domain
                } else {
                    fieldWrites[field] = merge(prevDomain, domain)
                }
            }
        }
    }
    var changed = false
    for ((field, domain) in fieldWrites) {
        val fieldPos = getFieldPosition(field)
        if (domain == RefDomain.NOTNULL && context.annotations[fieldPos] == null) {
            context.annotations[getFieldPosition(field)] = Nullability.NOT_NULL
            changed = true
        }
    }
    return changed
}