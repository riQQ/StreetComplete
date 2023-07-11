package de.westnordost.streetcomplete.data.elementfilter

interface BooleanExpressionItf<I : Matcher<T>, T> {
    val parent: OperatorWithChildren<I, T>?
    fun matches(obj: T): Boolean
}

abstract class BooleanExpression<I : Matcher<T>, T> : BooleanExpressionItf<I, T> {
    override var parent: OperatorWithChildren<I, T>? = null
        internal set

    abstract override fun matches(obj: T): Boolean
}

abstract class OperatorWithChildren<I : Matcher<T>, T> : BooleanExpression<I, T>() {
    abstract fun addChild(child: BooleanExpression<I, T>)

    abstract fun removeChild(child: BooleanExpression<I, T>)

    abstract fun replaceChild(replace: BooleanExpression<I, T>, with: BooleanExpression<I, T>)

    abstract fun replaceLastAndAddAsChild(with: OperatorWithChildren<I, T>)

    /** Removes unnecessary depth in the expression tree  */
    abstract fun flatten()

    abstract fun removeEmptyNodes()
}

abstract class Chain<I : Matcher<T>, T> : OperatorWithChildren<I, T>() {
    protected val nodes = ArrayList<BooleanExpression<I, T>>()

    val children: List<BooleanExpression<I, T>> get() = nodes.toList()

    override fun addChild(child: BooleanExpression<I, T>) {
        child.parent = this
        nodes.add(child)
    }

    override fun removeChild(child: BooleanExpression<I, T>) {
        nodes.remove(child)
        child.parent = null
    }

    override fun replaceChild(replace: BooleanExpression<I, T>, with: BooleanExpression<I, T>) {
        val it = nodes.listIterator()
        while (it.hasNext()) {
            val child = it.next()
            if (child === replace) {
                replaceChildAt(it, with)
                return
            }
        }
    }

    private fun replaceChildAt(
        at: MutableListIterator<BooleanExpression<I, T>>,
        vararg with: BooleanExpression<I, T>
    ) {
        at.remove()
        for (w in with) {
            at.add(w)
            w.parent = this
        }
    }

    override fun flatten() {
        removeEmptyNodes()
        mergeNodesWithSameOperator()
    }

    /** remove nodes from superfluous brackets  */
    override fun removeEmptyNodes() {
        val it = nodes.listIterator()
        while (it.hasNext()) {
            val next = it.next()
            if (next is Not) {
                next.removeEmptyNodes()
                continue
            }
            val child = next as? Chain ?: continue
            if (child.children.size == 1) {
                replaceChildAt(it, child.children.first())
                it.previous() // = the just replaced node will be checked again
            } else {
                child.removeEmptyNodes()
            }
        }
    }

    /** merge children recursively which do have the same operator set (and, or)  */
    private fun mergeNodesWithSameOperator() {
        val it = nodes.listIterator()
        while (it.hasNext()) {
            val child = it.next() as? Chain ?: continue
            child.mergeNodesWithSameOperator()

            // merge two successive nodes of same type
            if (child::class == this::class) {
                replaceChildAt(it, *child.children.toTypedArray())
            }
        }
    }

    fun simplifyChildren() : Pair<Boolean, BooleanExpression<I, T>?> {
        when (children.size) {
            0 -> return Pair(true, null)
            1 -> {
                val firstChild = children.first()
                removeChild(firstChild)
                return Pair(true, firstChild)
            }
        }

        return Pair(false, null)
    }

    override fun replaceLastAndAddAsChild(with: OperatorWithChildren<I, T>) {
        val last = children.last()
        replaceChild(last, with)
        with.addChild(last)
    }
}

class Leaf<I : Matcher<T>, T>(val value: I) : BooleanExpression<I, T>() {
    override fun matches(obj: T) = value.matches(obj)
    override fun toString() = value.toString()
}

class AllOf<I : Matcher<T>, T> : Chain<I, T>() {
    override fun matches(obj: T) = nodes.all { it.matches(obj) }
    override fun toString() = nodes.joinToString(" and ") { if (it is AnyOf) "($it)" else "$it" }
}

class AnyOf<I : Matcher<T>, T> : Chain<I, T>() {
    override fun matches(obj: T) = nodes.any { it.matches(obj) }
    override fun toString() = nodes.joinToString(" or ") { "$it" }
}

class Not<I : Matcher<T>, T> : OperatorWithChildren<I, T>() {
    internal var node : BooleanExpression<I, T>? = null
        private set

    internal var childrenIterator : Iterator<BooleanExpression<I,T>> = sequence {
        if (node == null)
            return@sequence
        yield(node!!)
    }.iterator()

    override fun addChild(child: BooleanExpression<I, T>) {
        if (node != null) {
            throw IllegalStateException("Adding a second child to 'Not' is not allowed")
        }

        child.parent = this
        node = child
    }

    override fun removeChild(child: BooleanExpression<I, T>) {
        if (node == child) {
            node = null
            child.parent = null
        }
    }

    override fun replaceChild(replace: BooleanExpression<I, T>, with: BooleanExpression<I, T>) {
        if (node === replace) {
            node = with
        }
    }

    override fun matches(obj: T) = node?.matches(obj) != true
    override fun replaceLastAndAddAsChild(with: OperatorWithChildren<I, T>) {
        val currentNode = node!!
        node = with
        with.addChild(currentNode)
    }

    override fun flatten() {
        removeEmptyNodes()
    }

    override fun removeEmptyNodes() {
        val next = node
        if (next is Not) {
            next.removeEmptyNodes()
            return
        }
        val child = next as? Chain ?: return
        if (child.children.size == 1) {
            val grandchild = child.children.first()
            node = grandchild
            grandchild.parent = this
            removeEmptyNodes() // = the just replaced node will be checked again
        } else {
            child.removeEmptyNodes()
        }
    }

    override fun toString() : String {
        val childString = when (node) {
            is Chain -> "($node)"
            null -> ""
            else -> "$node"
        }

        return "not $childString"
    }
}

interface Matcher<in T> {
    fun matches(obj: T): Boolean
}
