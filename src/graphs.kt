package kanva.graphs

import java.util.HashMap
import java.util.ArrayList
import kotlinlib.union

public trait Graph<out T> {
    public val nodes: Collection<Node<T>>
    public fun findNode(data: T): Node<T>?
}

public trait Edge<out T> {
    public val from: Node<T>
    public val to: Node<T>
}

public trait Node<out T> {
    public val data: T
    public val incomingEdges: Collection<Edge<T>>
    public val outgoingEdges: Collection<Edge<T>>
}

public val <T> Node<T>.predecessors: Collection<Node<T>>
    get() = incomingEdges.map { e -> e.from }
public val <T> Node<T>.successors: Collection<Node<T>>
    get() = outgoingEdges.map { e -> e.to }


// implementation

open class GraphImpl<out T>(createNodeMap: Boolean): Graph<T> {
    private val _nodes: MutableCollection<Node<T>> = ArrayList()
    private val nodeMap: MutableMap<T, Node<T>>? = if (createNodeMap) HashMap<T, Node<T>>() else null

    override val nodes: Collection<Node<T>> = _nodes

    override fun findNode(data: T): Node<T>? = nodeMap?.get(data)

    fun addNode(node: Node<T>) {
        _nodes.add(node)
        nodeMap?.put(node.data, node)
    }

    fun removeNode(node: Node<T>) {
        _nodes.remove(node)
        nodeMap?.remove(node.data)
    }
}

abstract class NodeImpl<out T> : Node<T> {
    private val _incomingEdges: MutableCollection<Edge<T>> = ArrayList()
    private val _outgoingEdges: MutableCollection<Edge<T>> = ArrayList()

    override val incomingEdges: Collection<Edge<T>> = _incomingEdges
    override val outgoingEdges: Collection<Edge<T>> = _outgoingEdges

    fun addIncomingEdge(edge: Edge<T>) {
        if (!_incomingEdges.contains(edge)) _incomingEdges.add(edge)
    }

    fun addOutgoingEdge(edge: Edge<T>) {
        if (!_outgoingEdges.contains(edge)) _outgoingEdges.add(edge)
    }

    fun removeIncomingEdge(edge: Edge<T>) {
        _incomingEdges.remove(edge)
    }

    fun removeOutgoingEdge(edge: Edge<T>) {
        _outgoingEdges.remove(edge)
    }

    override fun toString(): String {
        return "${data} in$incomingEdges out$outgoingEdges"
    }
}

class DefaultNodeImpl<out T>(public override val data: T) : NodeImpl<T>()

open class EdgeImpl<T>(
        public override val from: NodeImpl<T>,
        public override val to: NodeImpl<T>
) : Edge<T> {
    override fun toString(): String {
        return "${from.data} -> ${to.data}"
    }

    override fun hashCode(): Int {
        return from.hashCode() * 31 + to.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is EdgeImpl<*>) {
            return from == other.from && to == other.to
        }
        return false
    }
}

abstract class GraphBuilder<NodeKey, NodeData, G: GraphImpl<NodeData>>(
        val createNodeMap: Boolean, cacheNodes: Boolean
) {
    val nodeCache = if (cacheNodes) HashMap<NodeKey, NodeImpl<NodeData>>() else null

    val graph: G = newGraph()

    abstract fun newGraph(): G
    abstract fun newNode(data: NodeKey): NodeImpl<NodeData>
    open fun newEdge(from: NodeImpl<NodeData>, to: NodeImpl<NodeData>): EdgeImpl<NodeData> = EdgeImpl(from, to)

    fun getOrCreateNode(data: NodeKey): NodeImpl<NodeData> {
        val cachedNode = nodeCache?.get(data)
        if (cachedNode != null) {
            return cachedNode
        }

        val node = newNode(data)
        graph.addNode(node)
        nodeCache?.put(data, node)
        return node
    }

    fun getOrCreateEdge(from: NodeImpl<NodeData>, to: NodeImpl<NodeData>): EdgeImpl<NodeData> {
        val edge = newEdge(from, to)
        from.addOutgoingEdge(edge)
        to.addIncomingEdge(edge)
        return edge
    }

    fun removeNode(n: NodeImpl<NodeData>) {
        val edges = n.incomingEdges.union(n.outgoingEdges)
        for (e in edges) {
            removeEdge(e as EdgeImpl<NodeData>)
        }
        graph.removeNode(n)
    }

    fun removeEdge(e: EdgeImpl<NodeData>) {
        e.from.removeOutgoingEdge(e)
        e.to.removeIncomingEdge(e)
    }

    fun toGraph(): G = graph
}

fun <NodeKey, NodeData, G: GraphImpl<NodeData>> GraphBuilder<NodeKey, NodeData, G>.removeGraphNodes(
        nodeToRemove: (Node<NodeData>) -> Boolean
) {
    val restrictedNodes = graph.nodes.filter(nodeToRemove)
    for (node in restrictedNodes) {
        this.removeNode(node as NodeImpl<NodeData>)
    }
}