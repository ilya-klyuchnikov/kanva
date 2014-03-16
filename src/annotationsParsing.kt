package kanva.annotations.xml

import java.io.Reader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.AttributeList
import org.xml.sax.HandlerBase

import java.util.ArrayList
import java.io.BufferedReader
import java.io.FileReader
import java.io.File
import java.util.regex.Pattern
import java.util.HashMap

import kotlinlib.*


private val ANNOTATION_KEY_PATTERN = Pattern.compile("""(@\w*\s)?(.*)""")

trait AnnotationData {
    val annotationClassFqn: String
    val attributes: Map<String, String>
}

class AnnotationDataImpl(
        override val annotationClassFqn: String,
        override val attributes: MutableMap<String, String>
): AnnotationData


fun parseAnnotations(xml: Reader, handler: (key: String, data: Collection<AnnotationData>) -> Unit) {
    val text = escapeAttributes(xml.readText())
    val parser = SAXParserFactory.newInstance()!!.newSAXParser()
    parser.parse(text.getBytes().inputStream, object: HandlerBase(){

        private var currentItemElement: ItemElement? = null

        private inner class ItemElement(val name: String, val annotations: MutableCollection<AnnotationDataImpl>)

        public override fun startElement(name: String, attributes: AttributeList?) {
            if (attributes != null) {
                when (name) {
                    "root" -> {}
                    "item" -> {
                        val nameAttrValue = attributes.getValue("name")
                        if (nameAttrValue != null) {
                            currentItemElement = ItemElement(nameAttrValue, ArrayList())
                        }
                    }
                    "annotation" -> {
                        val nameAttrValue = attributes.getValue("name")
                        if (nameAttrValue != null) {
                            currentItemElement!!.annotations.add(AnnotationDataImpl(nameAttrValue, HashMap()))
                        }
                    }
                    "val" -> {
                        val nameAttrValue = attributes.getValue("name")
                        if (nameAttrValue != null) {
                            val valAttrValue = attributes.getValue("val")
                            if (valAttrValue != null) {
                                currentItemElement!!.annotations.toList().
                                last().attributes.put(nameAttrValue, valAttrValue)
                            }
                        }
                    }
                }
            }
        }

        public override fun endElement(name: String?) {
            if (name == "item") {
                handler(currentItemElement!!.name, currentItemElement!!.annotations)
            }
        }
    })
}

private fun escapeAttributes(str: String): String {
    return buildString {
        sb ->
        var inAttribute = false
        for (c in str) {
            when {
                inAttribute && c == '<' -> sb.append("&lt;")
                inAttribute && c == '>' -> sb.append("&gt;")
                c == '\"' || c == '\'' -> {
                    sb.append('\"')
                    inAttribute = !inAttribute
                }
                else -> sb.append(c);
            }
        }
    }
}
