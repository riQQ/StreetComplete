package de.westnordost.streetcomplete.data.elementfilter

/** Builds a boolean expression. Basically a BooleanExpression with a cursor.  */
class BooleanExpressionBuilder<I : Matcher<T>, T> {
    private var node: OperatorWithChildren<I, T> = BracketHelper()
    private var bracketCount = 0

    fun build(): BooleanExpression<I, T>? {
        if (bracketCount > 0) {
            throw IllegalStateException("Closed one bracket too little")
        }

        while (node.parent != null) {
            node = node.parent!!
        }

        node.flatten()

        val chain = node as? Chain ?: return node

        // flatten cannot remove itself, but we wanna do that
        val (isSimplified, simplifiedNode) = chain.simplifyChildren()
        if (isSimplified) {
            return simplifiedNode
        }

        chain.ensureNoBracketNodes()
        return chain
    }

    fun addOpenBracket() {
        val group = BracketHelper<I, T>()
        node.addChild(group)
        node = group

        bracketCount++
    }

    fun addCloseBracket() {
        if (--bracketCount < 0) throw IllegalStateException("Closed one bracket too much")

        while (node !is BracketHelper) {
            node = node.parent!!
        }
        node = node.parent!!
        node.flatten()
    }

    fun addValue(i: I) {
        node.addChild(Leaf(i))

        // Not can only have a single child and it binds stronger than the other boolean operators
        if (node is Not)
        {
            node = node.parent!!
        }
    }

    fun addAnd() {
        if (node !is AllOf) {
            val allOf = AllOf<I, T>()
            node.replaceLastAndAddAsChild(allOf)
            node = allOf
        }
    }

    fun addOr() {
        val allOf = node as? AllOf
        val group = node as? BracketHelper

        if (allOf != null) {
            val nodeParent = node.parent
            if (nodeParent is AnyOf) {
                node = nodeParent
            } else {
                nodeParent?.removeChild(allOf)
                val anyOf = AnyOf<I, T>()
                anyOf.addChild(allOf)
                nodeParent?.addChild(anyOf)
                node = anyOf
            }
        } else if (group != null) {
            val anyOf = AnyOf<I, T>()
            node.replaceLastAndAddAsChild(anyOf)
            node = anyOf
        }
    }

    fun addNot() {
        val not = Not<I, T>()
        node.addChild(not)
        node = not
    }
}

// TODO
private fun <I : Matcher<T>, T> Chain<I, T>.ensureNoBracketNodes() {
    if (this is BracketHelper) throw IllegalStateException("BooleanExpression still contains a Bracket node!")

    val it = children.iterator()
    while (it.hasNext()) {
        val child = it.next()
        if (child is Chain) child.ensureNoBracketNodes()
    }
}

private class BracketHelper<I : Matcher<T>, T> : Chain<I, T>() {
    override fun matches(obj: T) = throw IllegalStateException("Bracket cannot match")
}
