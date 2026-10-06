package com.najmulcodes.zapflick.data.db

/**
 * The SQL of each schema change, kept apart from Room so a test can read it. The statements must
 * produce exactly the tables Room expects from the entities, including index names
 * ("index_<table>_<column>"), or the app refuses to open the database.
 */
object MigrationSql {

    /**
     * Version 1 -> 2: the private folder flag on downloads, the favorite sites, and the browser
     * history. Existing download rows keep all their data; every one starts out not private.
     */
    val V1_TO_V2: List<String> = listOf(
        "ALTER TABLE downloads ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS favorite_sites (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "title TEXT NOT NULL, " +
            "url TEXT NOT NULL, " +
            "position INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS browser_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "url TEXT NOT NULL, " +
            "title TEXT NOT NULL, " +
            "visited_at INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_browser_history_url ON browser_history (url)",
        "CREATE INDEX IF NOT EXISTS index_browser_history_visited_at ON browser_history (visited_at)",
    )
}
