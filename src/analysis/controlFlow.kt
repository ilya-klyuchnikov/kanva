package kanva.analysis

import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.BasicInterpreter

import kanva.declarations.Method
import kanva.graphs.*

/** builds simplest control flow */
fun buildCFG(method: Method, methodNode: MethodNode): Graph<Int> =
        ControlFlowBuilder().buildCFG(method, methodNode)

private class ControlFlowBuilder : Analyzer<BasicValue>(BasicInterpreter()) {
    private class CfgBuilder: GraphBuilder<Int, Int, GraphImpl<Int>>(true, true) {
        override fun newNode(data: Int) = DefaultNodeImpl<Int>(data)
        override fun newGraph() = GraphImpl<Int>(true)
    }

    private var builder = CfgBuilder()

    fun buildCFG(method: Method, methodNode: MethodNode): Graph<Int> {
        builder = CfgBuilder()
        analyze(method.declaringClass.internal, methodNode)
        return builder.graph
    }

    override protected fun newControlFlowEdge(insn: Int, successor: Int) {
        val fromNode = builder.getOrCreateNode(insn)
        val toNode = builder.getOrCreateNode(successor)
        builder.getOrCreateEdge(fromNode, toNode)
    }
}
