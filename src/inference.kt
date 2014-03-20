package kanva.inference

import java.util.Date

import java.io.File
import kanva.index.FileBasedClassSource
import kanva.context.Context

import kanva.declarations.*

import kanva.annotations.xml.writeAnnotationsToXmlByPackage
import kanva.dependencies.buildFunctionDependencyGraph
import kanva.dependencies.getTopologicallySortedStronglyConnectedComponents
import kanva.graphs.Node

fun main(args: Array<String>) {
    println(Date())
    val jarFile = File("/Users/lambdamix/code/kannotator/lib/jdk/jre-7u12-windows-rt.jar")
    inferSDK(jarFile)
    println(Date())
}

// field initialization
fun inferSDK(jarFile: File) {
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf())
    val dependencyGraph = buildFunctionDependencyGraph(context.index, context.classSource)
    val components: List<Set<Node<Method>>> = dependencyGraph.getTopologicallySortedStronglyConnectedComponents().reverse()


    // final fields with values - this is doesn't depend
    inferSimpleFields(context)
    // PARAMETERS
    inferParams(context, components)
    // RETURNS
    inferReturns(context, components)

    // initializers - seems that it is enough for now
    // TODO - fuse different passes into one
    inferFields(context, components)

    println(context.annotations.size())
    writeAnnotationsToXmlByPackage(context.annotations)
}
