package kanva.annotations

import kanva.declarations.AnnotationPosition
import java.util.HashMap

trait Annotations<out A> {
    fun get(typePosition: AnnotationPosition): A?
}

trait MutableAnnotations<A> : Annotations<A> {
    fun set(typePosition: AnnotationPosition, annotation: A)
}

class AnnotationsImpl<A: Any> : MutableAnnotations<A> {
    private val data = HashMap<AnnotationPosition, A>()

    override fun get(typePosition: AnnotationPosition): A? {
        return data[typePosition]
    }

    override fun set(typePosition: AnnotationPosition, annotation: A) {
        data[typePosition] = annotation
    }

    fun size() = data.size
}

enum class Nullability {
    NOT_NULL
}

public val JB_NOT_NULL: String = "org.jetbrains.annotations.NotNull"
public val JB_NULLABLE: String = "org.jetbrains.annotations.Nullable"
public val JSR_305_NOT_NULL: String = "javax.annotation.Nonnull"
public val JSR_305_NULLABLE: String = "javax.annotation.Nullable"

fun classNamesToNullabilityAnnotation(canonicalClassNames: Set<String>) : Nullability? {
    val containsNotNull =
            canonicalClassNames.contains(JB_NOT_NULL) || canonicalClassNames.contains(JSR_305_NOT_NULL)
    return if (containsNotNull)
        Nullability.NOT_NULL
    else
        null
}
