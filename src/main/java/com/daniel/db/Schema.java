package com.daniel.db;

import java.sql.Connection;
import java.sql.Statement;

public final class Schema {
    private Schema() {}

    public static void ensure(Connection conn) {
        String sql = """
            CREATE TABLE IF NOT EXISTS accounts (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              type TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS transactions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              created_at TEXT NOT NULL,
              type TEXT NOT NULL,
              account_id INTEGER NOT NULL,
              amount_cents INTEGER NOT NULL,
              note TEXT,
              FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE RESTRICT
            );

            CREATE INDEX IF NOT EXISTS idx_tx_account_created
              ON transactions(account_id, created_at);
            """;

        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure schema", e);
        }
    }
}
