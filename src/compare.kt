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

    val diff1 = AnnotationsImpl<Nullability>()

    val kanvaAnns = kanvaContext.annotations

    var fieldDiffs1 = 0
    var paramDiffs1 = 0
    var returnDiffs1 = 0

    kannotatorContext.annotations.forEachPosition { pos, ann ->
        if (kanvaAnns[pos] == null) {
            diff1[pos] = ann
            // BTW, when doesn't work here
            if (pos is FieldPosition) {
                fieldDiffs1++
            }
            if (pos is MethodPosition && pos.relativePosition is ParameterPosition) {
                paramDiffs1++
            }
            if (pos is MethodPosition && pos.relativePosition == RETURN_POSITION) {
                returnDiffs1++
            }
        }
    }

    println("== DIFFS 1 (kannotator - kanva) ==")
    println("total diffs: ${diff1.size()}")
    println("fields: $fieldDiffs1")
    println("params: $paramDiffs1")
    println("return: $returnDiffs1")

    writeAnnotationsToXmlByPackage(diff1, "diff1", true)

    /////////

    val kannotatorAnns = kannotatorContext.annotations
    val diff2 = AnnotationsImpl<Nullability>()
    var fieldDiffs2 = 0
    var paramDiffs2 = 0
    var returnDiffs2 = 0

    kanvaContext.annotations.forEachPosition { pos, ann ->
        if (kannotatorAnns[pos] == null) {
            diff2[pos] = ann
            // BTW, when doesn't work here
            if (pos is FieldPosition) {
                fieldDiffs2++
            }
            if (pos is MethodPosition && pos.relativePosition is ParameterPosition) {
                paramDiffs2++
            }
            if (pos is MethodPosition && pos.relativePosition == RETURN_POSITION) {
                returnDiffs2++
            }
        }
    }

    println("== DIFFS 2 (kanva - kannotator) ==")
    println("total diffs: ${diff2.size()}")
    println("fields: $fieldDiffs2")
    println("params: $paramDiffs2")
    println("return: $returnDiffs2")
    writeAnnotationsToXmlByPackage(diff2, "diff2", true)
}
