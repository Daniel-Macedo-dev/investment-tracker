package com.daniel.db;

import java.sql.Connection;
import java.sql.Statement;

public final class Schema {
    private Schema() {}

    public static void ensure(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_version (
                  version INTEGER NOT NULL
                );
            """);

            ensureV1(st);
            ensureV2Flows(st);

            st.executeUpdate("INSERT INTO schema_version(version) SELECT 2 WHERE NOT EXISTS (SELECT 1 FROM schema_version);");
            st.executeUpdate("UPDATE schema_version SET version = CASE WHEN version < 2 THEN 2 ELSE version END;");

        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure schema", e);
        }
    }

    private static void ensureV1(Statement st) throws Exception {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS investment_types (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE
            );
        """);

        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS cash_snapshots (
              date TEXT PRIMARY KEY,         -- YYYY-MM-DD
              value_cents INTEGER NOT NULL
            );
        """);

        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS daily_snapshots (
              investment_type_id INTEGER NOT NULL,
              date TEXT NOT NULL,            -- YYYY-MM-DD
              value_cents INTEGER NOT NULL,
              note TEXT,
              PRIMARY KEY (investment_type_id, date),
              FOREIGN KEY (investment_type_id) REFERENCES investment_types(id) ON DELETE CASCADE
            );
        """);

        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshots_date ON daily_snapshots(date);");
    }

    private static void ensureV2Flows(Statement st) throws Exception {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS daily_flows (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              date TEXT NOT NULL,              -- YYYY-MM-DD

              from_kind TEXT NOT NULL,         -- CASH | INVESTMENT
              from_investment_type_id INTEGER, -- nullable se CASH

              to_kind TEXT NOT NULL,           -- CASH | INVESTMENT
              to_investment_type_id INTEGER,   -- nullable se CASH

              amount_cents INTEGER NOT NULL,
              note TEXT,

              FOREIGN KEY (from_investment_type_id) REFERENCES investment_types(id) ON DELETE CASCADE,
              FOREIGN KEY (to_investment_type_id)   REFERENCES investment_types(id) ON DELETE CASCADE
            );
        """);

        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_flows_date ON daily_flows(date);");
    }
}
