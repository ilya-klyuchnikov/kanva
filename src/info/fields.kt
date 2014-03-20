package info.fields

import java.io.File
import kanva.index.FileBasedClassSource
import kanva.context.Context

fun main(args: Array<String>) {
    val jarFile = File("/Users/lambdamix/code/kannotator/lib/jdk/jre-7u12-windows-rt.jar")
    val annotationsDir = File("kannotator-annotations-fields")
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf(annotationsDir))

}