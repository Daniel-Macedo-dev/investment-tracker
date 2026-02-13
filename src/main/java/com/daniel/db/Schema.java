package com.daniel.db;

import java.sql.Connection;
import java.sql.ResultSet;
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

            int version = currentVersion(st);

            ensureV1Tables(st);

            if (version < 1) setVersion(st, 1);

        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure schema", e);
        }
    }

    private static int currentVersion(Statement st) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            if (rs.next()) return rs.getInt(1);
        }
        st.executeUpdate("INSERT INTO schema_version(version) VALUES(0)");
        return 0;
    }

    private static void setVersion(Statement st, int v) throws Exception {
        st.executeUpdate("UPDATE schema_version SET version = " + v);
    }

    private static void ensureV1Tables(Statement st) throws Exception {
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
}
