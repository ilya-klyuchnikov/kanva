package kanva.annotations.xml

import java.io.Writer
import java.util.LinkedHashMap
import kanva.declarations.AnnotationPosition
import kotlinlib.*

// only for not nulls for now
fun writeAnnotationsToXML(writer: Writer, annotations: List<AnnotationPosition>) {
    val sb = StringBuilder()
    val printer = XmlPrinter(sb)
    printer.openTag("root")
    printer.pushIndent()
    for (typePosition in annotations) {
        printer.openTag("item", hashMapOf("name" to typePosition.toAnnotationKey()))
        printer.pushIndent()
        printer.openTag("annotation", hashMapOf("name" to "org.jetbrains.annotations.NotNull"), true)
        printer.popIndent()
        printer.closeTag("item")
    }
    printer.popIndent()
    printer.closeTag("root")

    writer.write(sb.toString())
    writer.close()
}

private class XmlPrinter(val sb: StringBuilder) {
    private val INDENTATION_UNIT = "    ";
    private var indent = "";

    public fun println() {
        sb.println()
    }

    fun openTag(tagName: String, attributes: Map<String, String>? = null, isClosed: Boolean = false, quoteChar : Char = '\'') {
        sb.append(indent)
        sb.append("<").append(tagName)
        if (attributes != null) {
            for ((name, value) in attributes) {
                sb.append(" ").append(escape(name)).append("=").append(quoteChar).append(escape(value)).append(quoteChar)
            }
        }
        if (isClosed) {
            sb.append("/>")
        }
        else {
            sb.append(">")
        }
        println()
    }

    fun closeTag(tagName: String) {
        sb.append(indent);
        sb.append("</").append(tagName).append(">")
        println()
    }

    public fun pushIndent() {
        indent += INDENTATION_UNIT;
    }

    public fun popIndent() {
        if (indent.length() < INDENTATION_UNIT.length()) {
            throw IllegalStateException("No indentation to pop");
        }

        indent = indent.substring(INDENTATION_UNIT.length());
    }
}

private fun escape(str: String): String {
    return buildString {
        sb ->
        for (c in str) {
            when {
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '\"' || c == '\'' -> {
                    sb.append("&quot;")
                }
                else -> sb.append(c);
            }
        }
    }
}

