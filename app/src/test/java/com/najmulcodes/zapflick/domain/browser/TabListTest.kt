package com.najmulcodes.zapflick.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabListTest {

    @Test
    fun `a fresh list has one empty active tab`() {
        val list = TabList.fresh()
        assertEquals(1, list.tabs.size)
        assertTrue(list.active.isNewTabPage)
    }

    @Test
    fun `adding a tab makes it active and gives it a new id`() {
        val list = TabList.fresh().add("https://a.com", "A")
        assertEquals(2, list.tabs.size)
        assertEquals("https://a.com", list.active.url)
        assertEquals(setOf(1L, 2L), list.tabs.map { it.id }.toSet())
    }

    @Test
    fun `the list is capped at twenty tabs`() {
        var list = TabList.fresh()
        repeat(40) { list = list.add("https://e.com/$it") }
        assertEquals(TabList.MAX_TABS, list.tabs.size)
        assertFalse(list.canAdd)
        assertEquals(list, list.add("https://more.com"))
    }

    @Test
    fun `closing the active tab selects its neighbour`() {
        val base = TabList.fresh().add("u2").add("u3").add("u4")
        val ids = base.tabs.map { it.id }
        val middle = base.select(ids[1]).close(ids[1])
        assertEquals(ids[2], middle.activeId)
        val last = base.select(ids[3]).close(ids[3])
        assertEquals(ids[2], last.activeId)
    }

    @Test
    fun `closing another tab keeps the active one`() {
        val base = TabList.fresh().add("u2").add("u3")
        val ids = base.tabs.map { it.id }
        val after = base.select(ids[0]).close(ids[2])
        assertEquals(ids[0], after.activeId)
        assertEquals(2, after.tabs.size)
    }

    @Test
    fun `closing the last tab leaves one new empty tab with a fresh id`() {
        val list = TabList.fresh().add("u2")
        val one = list.close(list.tabs[0].id).let { it.close(it.tabs[0].id) }
        assertEquals(1, one.tabs.size)
        assertTrue(one.active.isNewTabPage)
        assertTrue(one.active.id > 2L)
    }

    @Test
    fun `closing an unknown tab changes nothing`() {
        val list = TabList.fresh().add("u2")
        assertEquals(list, list.close(999L))
        assertEquals(list, list.select(999L))
    }

    @Test
    fun `closeAll leaves one empty tab`() {
        val list = TabList.fresh().add("u2").add("u3").closeAll()
        assertEquals(1, list.tabs.size)
        assertTrue(list.active.isNewTabPage)
    }

    @Test
    fun `update changes only the named tab and only the given fields`() {
        val list = TabList.fresh().add("u2", "Two")
        val id = list.activeId
        val updated = list.update(id, url = "u2b")
        assertEquals("u2b", updated.active.url)
        assertEquals("Two", updated.active.title)
        assertEquals("", updated.tabs[0].url)
        assertEquals("T", list.update(id, title = "T").active.title)
    }

    @Test
    fun `codec round-trips tabs including awkward titles`() {
        val list = TabList.fresh().add("https://a.com/?q=1&x=2", "Tab\twith\ttabs").add("https://b.org", "Line\nbreak ✓ বাংলা")
        val decoded = TabListCodec.decode(TabListCodec.encode(list))
        assertEquals(list.tabs, decoded?.tabs)
        assertEquals(list.activeId, decoded?.activeId)
    }

    @Test
    fun `after decoding a new tab gets an unused id`() {
        val decoded = TabListCodec.decode(TabListCodec.encode(TabList.fresh().add("u2").add("u3")))!!
        val before = decoded.tabs.map { it.id }.toSet()
        val added = decoded.add("u4")
        assertFalse(added.activeId in before)
    }

    @Test
    fun `damaged or empty text decodes to nothing`() {
        assertNull(TabListCodec.decode(null))
        assertNull(TabListCodec.decode(""))
        assertNull(TabListCodec.decode("garbage"))
        assertNull(TabListCodec.decode("tabs1|x|3\n1\tu\tt"))
        assertNull(TabListCodec.decode("tabs1|1|3\n1\tonly-two-fields"))
        assertNull(TabListCodec.decode("tabs1|1|3\n1\ta\tb\n1\tc\td"))
        assertNull(TabListCodec.decode("tabs1|1|3"))
    }

    @Test
    fun `an active id that no longer exists falls back to the first tab`() {
        val decoded = TabListCodec.decode("tabs1|77|9\n1\tu\tt\n2\tv\ts")
        assertEquals(1L, decoded?.activeId)
    }
}
