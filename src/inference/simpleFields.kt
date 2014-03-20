package kanva.inference

import kanva.annotations.Nullability
import kanva.context.Context
import kanva.declarations.*
import kanva.util.isPrimitiveOrVoidType

/** simplest annotations */
fun inferSimpleFields(context: Context) {
    for ((field, fieldNode) in context.index.fields) {
        if (field.isFinal() && !field.getType().isPrimitiveOrVoidType() && field.value != null) {
            context.annotations[getFieldPosition(field)] = Nullability.NOT_NULL
        }
    }
}