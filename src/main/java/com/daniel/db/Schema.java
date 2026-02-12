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

            -- Movimentações genéricas:
            -- DEPOSIT/WITHDRAW usam apenas from_account_id (o "alvo" da movimentação)
            -- TRANSFER usa from_account_id e to_account_id
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

            CREATE INDEX IF NOT EXISTS idx_tx_from_date ON transactions(from_account_id, created_at);
            CREATE INDEX IF NOT EXISTS idx_tx_to_date   ON transactions(to_account_id, created_at);
            """;

        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure schema", e);
        }
    }
}
