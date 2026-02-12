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

            if (version < 2) {
                migrateToV2(st);
                setVersion(st, 2);
            }

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

    private static boolean tableExists(Statement st, String name) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='" + name + "'"
        )) {
            return rs.next();
        }
    }

    private static boolean columnExists(Statement st, String table, String column) throws Exception {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String colName = rs.getString("name");
                if (column.equalsIgnoreCase(colName)) return true;
            }
            return false;
        }
    }

    private static void migrateToV2(Statement st) throws Exception {
        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS accounts (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              type TEXT NOT NULL
            );
        """);

        if (tableExists(st, "transactions") && !columnExists(st, "transactions", "from_account_id")) {
            st.executeUpdate("ALTER TABLE transactions RENAME TO transactions_legacy;");
        }

        st.executeUpdate("""
            CREATE TABLE IF NOT EXISTS transactions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              created_at TEXT NOT NULL,
              type TEXT NOT NULL,
              from_account_id INTEGER NOT NULL,
              to_account_id INTEGER,
              amount_cents INTEGER NOT NULL,
              note TEXT,
              FOREIGN KEY(from_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
              FOREIGN KEY(to_account_id) REFERENCES accounts(id) ON DELETE RESTRICT
            );
        """);

        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tx_from_date ON transactions(from_account_id, created_at);");
        st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tx_to_date   ON transactions(to_account_id, created_at);");
    }
}
