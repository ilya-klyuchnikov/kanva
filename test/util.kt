package kanva.test

import java.lang.reflect.Method
import java.lang.reflect.Modifier

fun Method.isStatic(): Boolean =
        getModifiers() and Modifier.STATIC != 0;