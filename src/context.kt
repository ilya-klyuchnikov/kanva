package kanva.context

import java.io.File
import java.io.Reader
import java.util.ArrayList

import kotlinlib.recurseFiltered
import java.io.FileReader
import org.objectweb.asm.tree.MethodInsnNode

import kanva.index.*
import kanva.annotations.*
import kanva.annotations.xml.*
import kanva.declarations.*

class Context(
        val classSource: ClassSource,
        val index: DeclarationIndex,
        val annotations: Annotations<Nullability>
)

fun Context(classSource: ClassSource, annotationFiles: Collection<File>): Context {
    val declarationIndex = DeclarationIndexImpl(classSource)
    val xmlAnnotations = ArrayList<File>()

    for (annFile in annotationFiles) {
        annFile.recurseFiltered(
                { it.isFile() && it.name.endsWith(".xml") },
                { xmlAnnotations.add(it) }
        )
    }

    val annotations = loadExternalAnnotations(
            xmlAnnotations map {{FileReader(it)}},
            declarationIndex
    )

    //println("LOADED ${annotations.size()}")

    return Context(classSource, declarationIndex, annotations)
}

fun Context.findMethodByMethodInsnNode(methodInsnNode: MethodInsnNode): Method? =
        index.findMethod(ClassName.fromInternalName(methodInsnNode.owner!!), methodInsnNode.name!!, methodInsnNode.desc)

fun Context.findNotNullParamPositions(method: Method?): Collection<ParameterPosition> {
    val notNullPositions = arrayListOf<ParameterPosition>()
    if (method != null) {
        PositionsForMethod(method).forEachValidPosition { pos ->
            if (annotations[pos] == Nullability.NOT_NULL) {
                if (pos is MethodTypePosition) {
                    val relPosition = pos.relativePosition
                    if (relPosition is ParameterPosition) {
                        notNullPositions.add(relPosition)
                    }

                }
            }
        }

    }
    return notNullPositions
}

private fun loadExternalAnnotations(
        xmls: Collection<() -> Reader>,
        index: DeclarationIndex
): MutableAnnotations<Nullability> {

    val result = AnnotationsImpl<Nullability>()
    for (xml in xmls) {
        xml() use {
            parseAnnotations(it) {
                key, annotations ->
                val position = index.findPositionByAnnotationKeyString(key)
                if (position != null) {
                    val classNames = annotations.map{it.annotationClassFqn}.toSet()
                    val nullability = classNamesToNullabilityAnnotation(classNames)
                    if (nullability != null) {
                        result.set(position, nullability)
                    }
                } else {
                    println("Position not found for $key")
                }
            }
        }
    }

    println("loaded ${result.size()}")
    return result
}



