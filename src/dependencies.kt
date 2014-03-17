package kanva.dependencies

import kanva.index.*
import kanva.graphs.*
import kanva.declarations.*
import org.objectweb.asm.ClassReader.*
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.MethodVisitor
import kotlinlib.flags

public fun buildFunctionDependencyGraph(declarationIndex: DeclarationIndex, classSource: ClassSource) : Graph<Method> =
        FunDependencyGraphBuilder(declarationIndex, classSource).build()

public class FunDependencyGraphBuilder(
        private val index: DeclarationIndex,
        private val source: ClassSource
): GraphBuilder<Method, Method, GraphImpl<Method>>(false, true) {
    private var currentFromNode : NodeImpl<Method>? = null
    private var currentClassName : ClassName? = null

    private val classVisitor = object : ClassVisitor(Opcodes.ASM4) {
        public override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            currentClassName = ClassName.fromInternalName(name)
        }

        public override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val method = Method(currentClassName!!, access, name, desc, signature)
            currentFromNode = getOrCreateNode(method)
            return methodVisitor
        }
    }

    private val methodVisitor = object : MethodVisitor(Opcodes.ASM4) {
        public override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String) {
            val ownerClassName = ClassName.fromInternalName(owner)
            val method = index.findMethod(ownerClassName, name, desc)
            if (method != null) {
                getOrCreateEdge(currentFromNode!!, getOrCreateNode(method))
            }
        }
    }


    override fun newGraph(): GraphImpl<Method> = GraphImpl(false)
    override fun newNode(data: Method): NodeImpl<Method> = DefaultNodeImpl(data)

    public fun build(): Graph<Method> {
        source.forEach {
            reader ->
            reader.accept(classVisitor, flags(SKIP_DEBUG, SKIP_FRAMES))
        }

        return toGraph()
    }
}
