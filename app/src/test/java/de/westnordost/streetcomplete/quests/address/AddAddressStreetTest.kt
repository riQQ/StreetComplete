package de.westnordost.streetcomplete.quests.address

import de.westnordost.osmapi.map.data.*
import de.westnordost.streetcomplete.mock
import de.westnordost.streetcomplete.quests.TestMapDataWithGeometry
import org.junit.Assert.*
import org.junit.Test

class AddAddressStreetTest {

    private val questType = AddAddressStreet(mock())

    @Test fun `applicable to place without street name`() {
        val mapData = TestMapDataWithGeometry(listOf(
            OsmNode(1L, 1, 0.0,0.0, mapOf(
                "addr:housenumber" to "123"
            ))
        ))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `not applicable to place with street name`() {
        val mapData = TestMapDataWithGeometry(listOf(
            OsmNode(1L, 1, 0.0,0.0, mapOf(
                "addr:housenumber" to "123",
                "addr:street" to "onetwothree",
            ))
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `not applicable to place without street name but in a associatedStreet relation`() {
        val mapData = TestMapDataWithGeometry(listOf(
            OsmNode(1L, 1, 0.0,0.0, mapOf(
                "addr:housenumber" to "123"
            )),
            OsmRelation(1L, 1, listOf(
                OsmRelationMember(1L, "doesntmatter", Element.Type.NODE)
            ), mapOf(
                "type" to "associatedStreet"
            )),
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `applicable to place in interpolation without street name`() {
        val mapData = TestMapDataWithGeometry(listOf(
            OsmNode(1L, 1, 0.0,0.0, mapOf(
                "addr:housenumber" to "123"
            )),
            OsmWay(1L, 1, listOf(1,2,3), mapOf(
                "addr:interpolation" to "whatever",
            ))
        ))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `not applicable to place in interpolation with street name`() {
        val mapData = TestMapDataWithGeometry(listOf(
            OsmNode(1L, 1, 0.0,0.0, mapOf(
                "addr:housenumber" to "123"
            )),
            OsmWay(1L, 1, listOf(1,2,3), mapOf(
                "addr:interpolation" to "whatever",
                "addr:street" to "Street Name"
            ))
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }
}
