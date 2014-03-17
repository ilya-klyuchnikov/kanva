package kanva.inference

import java.io.File
import kanva.index.FileBasedClassSource
import kanva.context.Context
import kanva.dependencies.buildFunctionDependencyGraph
import kanva.dependencies.getTopologicallySortedStronglyConnectedComponents
import kanva.graphs.Node

import kanva.validation.*
import kanva.declarations.*
import kanva.annotations.Nullability
import java.util.Date

fun inferLib(jarFile: File): Collection<AnnotationPosition> {
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf())
    val dependencyGraph = buildFunctionDependencyGraph(context.index, context.classSource)
    val components = dependencyGraph.getTopologicallySortedStronglyConnectedComponents().reverse()

    for (component in components) {
        processComponent(context, component)
    }

    println("${context.annotations.size()} annotations inferred")

    context.annotations.forEachPosition { pos, ann ->
        println(pos)
    }

    return listOf()
}

fun processComponent(context: Context, component: Set<Node<Method>>) {
    if (component.size == 1) {
        val method: Method = component.first().data
        val methodNode = context.index.methods[method]!!
        
        val cfg = buildCFG(method, methodNode)
        val notNulls = collectNotNullParams(context, cfg, method, methodNode)
        for (i in notNulls) {
            val methodPositions = PositionsForMethod(method)
            context.annotations[methodPositions.get(ParameterPosition(i))] = Nullability.NOT_NULL
        }
    } else {
        // brute force for now
        var changed = false
        do {
            changed = false
            for (node in component) {
                val method = node.data
                val methodNode = context.index.methods[method]!!
                val cfg = buildCFG(method, methodNode)
                val notNulls = collectNotNullParams(context, cfg, method, methodNode)
                for (i in notNulls) {
                    val methodPositions = PositionsForMethod(method)
                    if (context.annotations[methodPositions.get(ParameterPosition(i))] != Nullability.NOT_NULL) {
                        context.annotations[methodPositions.get(ParameterPosition(i))] = Nullability.NOT_NULL
                        changed = true
                    }
                }
            }
        } while(changed)
    }
}

fun main(args: Array<String>) {
    println(Date())
    val jarFile = File("/Users/lambdamix/code/kannotator/lib/jdk/jre-7u12-windows-rt.jar")
    inferLib(jarFile)
    println(Date())
}
