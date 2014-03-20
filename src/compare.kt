package kanva.compare

import java.io.File
import kanva.context.Context
import kanva.index.FileBasedClassSource
import kanva.annotations.Nullability
import kanva.annotations.AnnotationsImpl
import kanva.annotations.xml.writeAnnotationsToXmlByPackage
import kanva.declarations.FieldPosition
import kanva.declarations.MethodPosition
import kanva.declarations.ParameterPosition
import kanva.declarations.RETURN_POSITION

val jarFile = File("/Users/lambdamix/code/kannotator/lib/jdk/jre-7u12-windows-rt.jar")

/** utility to compare results of kannotator with kanva */
fun main(args: Array<String>) {
    val jarSource = FileBasedClassSource(listOf(jarFile))

    println("== KANNOTATOR ==")
    val kannotatorContext =
            Context(jarSource, listOf(File("kannotator-annotations-fields"), File("kannotator-annotations-params"), File("kannotator-annotations-returns")))

    println("== KANVA ==")
    val kanvaContext =
            Context(jarSource, listOf(File("kanva-annotations-fields"), File("kanva-annotations-params"), File("kanva-annotations-returns")))

    val diff = AnnotationsImpl<Nullability>()

    val kanvaAnns = kanvaContext.annotations

    var fieldDiffs = 0
    var paramDiffs = 0
    var returnDiffs = 0
    kannotatorContext.annotations.forEachPosition { pos, ann ->
        if (kanvaAnns[pos] == null) {
            diff[pos] = ann
            // BTW, when doesn't work here
            if (pos is FieldPosition) {
                fieldDiffs++
            }
            if (pos is MethodPosition && pos.relativePosition is ParameterPosition) {
                paramDiffs++
            }
            if (pos is MethodPosition && pos.relativePosition == RETURN_POSITION) {
                returnDiffs++
            }
        }
    }

    println("== DIFFS ==")
    println("total diffs: ${diff.size()}")
    println("fields: $fieldDiffs")
    println("params: $paramDiffs")
    println("return: $returnDiffs")

    writeAnnotationsToXmlByPackage(diff, "diff", true)
}
