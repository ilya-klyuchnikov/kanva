package kotlinlib

import java.io.File
import java.util.regex.Matcher
import java.util.LinkedHashSet

public fun String.removeSuffix(suffix: String): String {
    if (!endsWith(suffix)) return this
    return substring(0, size - suffix.size)
}

public fun buildString(body: (sb: StringBuilder) -> Unit): String {
    val sb = StringBuilder()
    body(sb)
    return sb.toString()
}

///////////

fun String.suffixAfterLast(delimiter: String): String {
    val index = this.lastIndexOf(delimiter)
    if (index < 0) return this
    return this.substring(index + 1)
}

fun String.suffixAfterLast(delimiter: Char): String {
    val index = this.lastIndexOf(delimiter)
    if (index < 0) return this
    return this.substring(index + 1)
}

fun String.suffixAfter(delimiter: String): String {
    val index = this.lastIndexOf(delimiter)
    if (index < 0) return this
    return this.substring(index + 1)
}

fun String.suffixAfter(delimiter: Char): String {
    val index = this.lastIndexOf(delimiter)
    if (index < 0) return this
    return this.substring(index + 1)
}

/////////////

public fun String.prefix(length: Int): String = if (length == 0) "" else substring(0, length)

// Absence of default can be easily replaced with "str.prefixUpTo(ch) ?: default"
public fun String.prefixUpToLast(ch: Char): String? {
    val lastIndex = lastIndexOf(ch)
    return if (lastIndex != -1) prefix(lastIndex) else null
}

public fun String.prefixUpToLast(s: String): String? {
    val lastIndex = lastIndexOf(s)
    return if (lastIndex != -1) prefix(lastIndex) else null
}

public fun String.prefixUpTo(ch: Char): String? {
    val firstIndex = indexOf(ch)
    return if (firstIndex != -1) prefix(firstIndex) else null
}

public fun String.prefixUpTo(s: String): String? {
    val firstIndex = indexOf(s)
    return if (firstIndex != -1) prefix(firstIndex) else null
}

/////

public fun File.recurseFiltered(fileFilter: (File) -> Boolean = {true}, block: (File) -> Unit): Unit {
    if (fileFilter(this)) {
        block(this)
    }
    if (this.isDirectory()) {
        for (child in this.listFiles()!!) {
            child.recurseFiltered(fileFilter, block)
        }
    }
}

fun Matcher.get(groupIndex: Int): String? = group(groupIndex)

///

public fun <T> Collection<T>.union(other: Collection<T>): Set<T> {
    val resultSet = LinkedHashSet(this)
    resultSet.addAll(other)
    return resultSet
}

private val EMPTY_OBJECT_ARRAY = Array<Any?>(0, {null})
public fun <T> Array<out T>?.orEmptyArray(): Array<out T> = if (this == null) EMPTY_OBJECT_ARRAY as Array<out T> else this

public fun flags(f1: Int, f2: Int): Int = f1 or f2

val LINE_SEPARATOR: String = System.getProperty("line.separator")!!

public fun StringBuilder.println(): StringBuilder {
    return append(LINE_SEPARATOR)
}
