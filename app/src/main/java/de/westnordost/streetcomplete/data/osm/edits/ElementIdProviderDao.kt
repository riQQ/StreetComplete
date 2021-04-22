package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.osmapi.map.data.Element
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProviderTable.Columns.EDIT_ID
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProviderTable.Columns.ELEMENT_TYPE
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProviderTable.Columns.ID
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProviderTable.NAME
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import javax.inject.Inject

/** Assigns new element ids for ElementEditActions that create new elements */
class ElementIdProviderDao @Inject constructor(private val db: Database) {

    fun assign(editId: Long, nodeCount: Int, wayCount: Int, relationCount: Int) {
        if (nodeCount == 0 && wayCount == 0 && relationCount == 0) return

        db.insertMany(NAME,
            arrayOf(EDIT_ID, ELEMENT_TYPE),
            sequence {
                repeat(nodeCount) { yield(Element.Type.NODE) }
                repeat(wayCount) { yield(Element.Type.WAY) }
                repeat(relationCount) { yield(Element.Type.RELATION) }
            }.map { arrayOf<Any?>(editId, it.name) }.asIterable()
        )
    }

    fun get(editId: Long) = ElementIdProvider(
        db.query(NAME, where = "$EDIT_ID = $editId") {
            ElementKey(Element.Type.valueOf(it.getString(ELEMENT_TYPE)), -it.getLong(ID))
        })

    fun delete(editId: Long): Int =
        db.delete(NAME, "$EDIT_ID = $editId")

    fun deleteAll(editIds: List<Long>): Int =
        db.delete(NAME, "$EDIT_ID IN (${editIds.joinToString(",")})")
}
