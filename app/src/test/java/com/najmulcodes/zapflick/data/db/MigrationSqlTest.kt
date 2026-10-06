package com.najmulcodes.zapflick.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The statements are also run against a real SQLite in the repository's checks; this guards the
 * parts that must never change: no data is dropped, and the names match the Room entities.
 */
class MigrationSqlTest {

    private val sql = MigrationSql.V1_TO_V2

    @Test
    fun `the migration never deletes anything`() {
        sql.forEach {
            val upper = it.uppercase()
            assertFalse(it, "DROP " in upper)
            assertFalse(it, "DELETE " in upper)
            assertFalse(it, "TRUNCATE" in upper)
        }
    }

    @Test
    fun `the private flag is added to downloads and starts off`() {
        assertEquals(
            "ALTER TABLE downloads ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0",
            sql.first(),
        )
    }

    @Test
    fun `the new tables and indices use the names Room expects`() {
        val all = sql.joinToString("\n")
        assertTrue("CREATE TABLE IF NOT EXISTS favorite_sites" in all)
        assertTrue("CREATE TABLE IF NOT EXISTS browser_history" in all)
        assertTrue("CREATE UNIQUE INDEX IF NOT EXISTS index_browser_history_url ON browser_history (url)" in all)
        assertTrue("CREATE INDEX IF NOT EXISTS index_browser_history_visited_at ON browser_history (visited_at)" in all)
        assertTrue("id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" in all)
    }

    @Test
    fun `there are five statements`() {
        assertEquals(5, sql.size)
    }
}
