package de.westnordost.streetcomplete.data.elementfilter

import de.westnordost.streetcomplete.osm.LAST_CHECK_DATE_KEYS
import de.westnordost.streetcomplete.osm.MAXSPEED_TYPE_KEYS
import de.westnordost.streetcomplete.osm.surface.ANYTHING_UNPAVED
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.ktx.toEpochMilli
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HIGHWAY = "highway"
private const val RESIDENTIAL = "residential"

class ElementFilterParserTest {
    @Test
    fun `test expression parsing and matching simple tag filter`() {
        val someNode = node(tags = mapOf(HIGHWAY to RESIDENTIAL))
        val someWay = way(tags = mapOf("bla" to "1"))
        val residentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL, "name" to "Main street"))

        val expression = "ways with highway = residential"

        val filterExpression = expression.toElementFilterExpression()

        assertFalse("Way filter is not expected to match a node with correct tags", filterExpression.matches(someNode))
        assertFalse("Filter is not expected to match a way with irrelevant tags", filterExpression.matches(someWay))
        assertTrue("Filter is  expected to match a residential way", filterExpression.matches(residentialWay))
    }

    @Test
    fun `test expression parsing and matching tag filter`() {
        val someNode = node(tags = mapOf(HIGHWAY to RESIDENTIAL))
        val someWay = way(tags = mapOf("bla" to "1"))
        val residentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL, "name" to "Main street"))
        val unnamedResidentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL, "surface" to "asphalt"))

        val expression = "ways with (highway = residential or highway = tertiary) and !name"

        val filterExpression = expression.toElementFilterExpression()

        assertFalse("Way filter is not expected to match a node with correct tags", filterExpression.matches(someNode))
        assertFalse("Filter is not expected to match a way with irrelevant tags", filterExpression.matches(someWay))
        assertFalse("Filter is not expected to match a named residential way", filterExpression.matches(residentialWay))
        assertTrue("Unnamed residential way wasn't matched.", filterExpression.matches(unnamedResidentialWay))
    }

    @Test
    fun `test operator precedence`() {
        val expression = "ways with highway = residential or highway = tertiary and !name"
        val namedTertiaryWay = way(tags = mapOf(HIGHWAY to "tertiary", "name" to "Main street"))
        val unnamedTertiaryWay = way(tags = mapOf(HIGHWAY to "tertiary"))

        val namedResidentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL, "name" to "Residential street"))
        val unnamedResidentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL))


        val filterExpression = expression.toElementFilterExpression()
        assertFalse("Filter is not expected to match a named tertiary way", filterExpression.matches(namedTertiaryWay))
        assertTrue("Filter is expected to match an unnamed tertiary way", filterExpression.matches(unnamedTertiaryWay))
        assertTrue("Filter is expected to match a named residential way", filterExpression.matches(namedResidentialWay))
        assertTrue("Filter is expected to match an unnamed residential way", filterExpression.matches(unnamedResidentialWay))
    }

    @Test
    fun `test expression parsing and matching date filter`() {
        val fiveYearsAgo = LocalDate.now().minusYears(5).toKotlinLocalDate().toEpochMilli()
        val nodeLastEditedFiveYearsAgo = node(tags = mapOf("bla" to "1"), timestamp = fiveYearsAgo)

        val threeYearsAgo = LocalDate.now().minusYears(3).toKotlinLocalDate().toEpochMilli()
        val nodeLastEditedThreeYearsAgo = node(tags = mapOf("bla" to "1"), timestamp = threeYearsAgo)


        val expression = "nodes with ${lastChecked(4.0)}".toElementFilterExpression()

        assertTrue("Five years old node wasn't matched", expression.matches(nodeLastEditedFiveYearsAgo))
        assertFalse("Three years old node was erroneously matched", expression.matches(nodeLastEditedThreeYearsAgo))
    }

    @Test
    fun `test expression parsing with not and matching tags`() {
        val nonResidentialWay = way(tags = mapOf("bla" to "1"))
        val residentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL))


        val expression = "ways with not highway = residential".toElementFilterExpression()

        assertTrue("Non-residential way wasn't matched", expression.matches(nonResidentialWay))
        assertFalse("Residential way was matched erroneously", expression.matches(residentialWay))
    }

    @Test
    fun `test expression parsing with complex not and matching tags`() {
        val someWay = way(tags = mapOf("bla" to "1"))
        val residentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL))


        val expression = "ways with not (highway = residential or highway = tertiary)".toElementFilterExpression()

        assertTrue("Way wasn't matched erroneously", expression.matches(someWay))
        assertFalse("Residential way was matched erroneously", expression.matches(residentialWay))
    }

    @Test
    fun `test expression parsing with not-vs-or precedence without any braces and matching tags`() {
        val accesibleResidentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL, "access" to "yes"))
        val accesibleTertiaryWay = way(tags = mapOf(HIGHWAY to "tertiary", "access" to "yes"))
        val notAccesibleResidentialWay = way(tags = mapOf(HIGHWAY to RESIDENTIAL, "access" to "no"))
        val notAccesibleTertiaryWay = way(tags = mapOf(HIGHWAY to "tertiary", "access" to "no"))


        val expression = "ways with not highway = residential or access = yes".toElementFilterExpression()

        assertFalse("Way was matched erroneously", expression.matches(notAccesibleResidentialWay))
        assertTrue("Way wasn't matched", expression.matches(accesibleResidentialWay))
        assertTrue("Way wasn't matched", expression.matches(accesibleTertiaryWay))
        assertTrue("Way wasn't matched", expression.matches(notAccesibleTertiaryWay))
    }

    @Test
    fun `test expression parsing AddCycleway`() {
        val accesibleResidentialWay = way(tags = mapOf(
            HIGHWAY to RESIDENTIAL,
            "access" to "yes",
            "maxspeed" to "30",
            "zone:traffic" to "DE:urban",
            "zone:maxspeed" to "DE:30",
        ))

        val residentialWay = way(tags = mapOf(
            HIGHWAY to RESIDENTIAL,
            "maxspeed" to "30",
            "zone:traffic" to "DE:urban"
        ))

        val notInZone30OrLessNew = """
   not ~"${(MAXSPEED_TYPE_KEYS + "maxspeed").joinToString("|")}" ~ ".*:(30|20)"
"""

        val notInZone30OrLess = """
   ~"${(MAXSPEED_TYPE_KEYS + "maxspeed").joinToString("|")}" ~ ".*:(urban|rural|trunk|motorway|nsl_single|nsl_dual)"
"""

        val expression = """
    ways with (
        highway ~ primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified
        or highway = residential and (maxspeed > 33 or $notInZone30OrLessNew)
      )
      and !cycleway
      and !cycleway:left
      and !cycleway:right
      and !cycleway:both
      and !sidewalk:bicycle
      and !sidewalk:left:bicycle
      and !sidewalk:right:bicycle
      and !sidewalk:both:bicycle
      and (
        !maxspeed
        or maxspeed > 20
        or $notInZone30OrLessNew
      )
      and surface !~ ${ANYTHING_UNPAVED.joinToString("|")}
""".toElementFilterExpression()

        assertFalse("Way was matched", expression.matches(accesibleResidentialWay))
        assertTrue("Way wasn't matched", expression.matches(residentialWay))
    }

    // copied
    private fun lastChecked(yearsAgo: Double): String = """
        older today -$yearsAgo years
        or ${LAST_CHECK_DATE_KEYS.joinToString(" or ") { "$it < today -$yearsAgo years" }}
    """.trimIndent()
}
