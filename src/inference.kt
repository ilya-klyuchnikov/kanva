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
import kanva.annotations.Annotations
import java.util.ArrayList
import java.util.HashSet
import java.util.HashMap
import java.io.FileWriter
import kanva.annotations.xml.writeAnnotationsToXML

fun inferLib(jarFile: File) {
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf())
    val dependencyGraph = buildFunctionDependencyGraph(context.index, context.classSource)
    val components = dependencyGraph.getTopologicallySortedStronglyConnectedComponents().reverse()

    for (component in components) {
        processComponent(context, component)
    }
    println("${context.annotations.size()} annotations inferred")
    writeAnnotationsToXmlByPackage(File("kanva-annotations"), context.annotations)
}

fun processComponent(context: Context, component: Set<Node<Method>>) {
    if (component.size == 1) {
        val method: Method = component.first().data
        val methodNode = context.index.methods[method]!!
        val methodPositions = PositionsForMethod(method)

        val cfg = buildCFG(method, methodNode)
        val notNulls = collectNotNullParams(context, cfg, method, methodNode)

        val skip = if (method.isStatic()) 0 else 1
        for (i in notNulls) {
            context.annotations[methodPositions.get(ParameterPosition(i))] = Nullability.NOT_NULL
        }
        // exceptions
        val indices = (skip .. (method.getArgumentTypes().size + skip - 1)).toList()
        for (i in indices) {
            if (i !in notNulls) {
                val normalReturn = normalReturnOnNullReachable(context, cfg, method, methodNode, i)
                if (!normalReturn) {
                    context.annotations[methodPositions.get(ParameterPosition(i))] = Nullability.NOT_NULL
                }
            }
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
        } while(changed)
    }
}

fun writeAnnotationsToXmlByPackage(
    destination: File,
    annotations: Annotations<Nullability>
) {


    val members = HashSet<ClassMember>()
    annotations.forEachPosition { pos, ann -> members.add(pos.member) }
    val sortedMembers = members.sortBy {it.toString()}

    val sortedPositions = ArrayList<AnnotationPosition>(annotations.size())
    for (m in sortedMembers) {
        if (m is Method) {
            PositionsForMethod(m).forEachValidPosition { pos ->
                if (annotations[pos] != null) {
                    sortedPositions.add(pos)
                }
            }
        }
    }

    val positionsByPackage = HashMap<String, MutableList<AnnotationPosition>>()
    for (pos in sortedPositions) {
        val packageName = pos.member.getInternalPackageName()
        positionsByPackage.getOrPut(packageName, {arrayListOf()}).add(pos)
    }

    for ((path, pathAnnotations) in positionsByPackage) {
        val outputDir = File(destination, path)
        outputDir.mkdirs()
        val outputFile = File(outputDir, "annotations.xml")
        val writer = FileWriter(outputFile)
        writeAnnotationsToXML(writer, pathAnnotations)
    }
}

fun ClassMember.getInternalPackageName(): String {
    val className = declaringClass.internal
    val delimiter = className.lastIndexOf('/')
    return if (delimiter >= 0) className.substring(0, delimiter) else ""
}


fun main(args: Array<String>) {
    println(Date())
    val jarFile = File("/Users/lambdamix/code/kannotator/lib/jdk/jre-7u12-windows-rt.jar")
    inferLib(jarFile)
    println(Date())
}
