package kanva.util

import java.io.File
import java.util.jar.JarFile
import java.util.Enumeration
import java.util.jar.JarEntry

import org.objectweb.asm.Type
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.FieldNode

import kotlinlib.*
import kanva.declarations.*

fun processJar(file: File, block: (jarFile: File, classType: Type, classReader: ClassReader) -> Unit) {
    val jar = JarFile(file)
    for (entry in jar.entries() as Enumeration<JarEntry>) {
        val name = entry.getName()
        if (!name.endsWith(".class")) continue

        val internalName = name.removeSuffix(".class")
        val classType = Type.getType("L$internalName;")

        val inputStream = jar.getInputStream(entry)
        val classReader = ClassReader(inputStream)

        block(file, classType, classReader)
    }
}

public fun Method.createMethodNodeStub(): MethodNode =
        MethodNode(access.flags, id.methodName, id.methodDesc, genericSignature, null)

public fun Field.createFieldNodeStub(): FieldNode =
        FieldNode(access.flags, id.fieldName, desc, genericSignature, null)

public fun Type.isPrimitiveOrVoidType() : Boolean =
        when (getSort()) {
            Type.VOID,
            Type.BOOLEAN,
            Type.CHAR,
            Type.BYTE,
            Type.SHORT,
            Type.INT,
            Type.FLOAT,
            Type.LONG,
            Type.DOUBLE ->
                true
            else ->
                false
        }
