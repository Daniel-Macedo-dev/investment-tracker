package com.daniel.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Schema {

    private Schema() {}

    public static void ensure(Connection conn) {
        try (Statement st = conn.createStatement()) {
            // Segurança e consistência
            st.execute("PRAGMA foreign_keys = ON;");

            // Tipos de investimento
            st.execute("""
                    CREATE TABLE IF NOT EXISTS investment_types (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE
                    );
                    """);

            // Snapshot do CASH por dia
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cash_snapshots (
                        date TEXT PRIMARY KEY,
                        cash_cents INTEGER NOT NULL
                    );
                    """);

            // Snapshot do valor total de cada investimento por dia
            st.execute("""
                    CREATE TABLE IF NOT EXISTS daily_snapshots (
                        date TEXT NOT NULL,
                        investment_type_id INTEGER NOT NULL,
                        value_cents INTEGER NOT NULL,
                        note TEXT,
                        PRIMARY KEY (date, investment_type_id),
                        FOREIGN KEY (investment_type_id) REFERENCES investment_types(id) ON DELETE CASCADE
                    );
                    """);

            // Movimentações (transferência/aporte/retirada) — NÃO é lucro
            st.execute("""
                    CREATE TABLE IF NOT EXISTS daily_flows (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        date TEXT NOT NULL,

                        from_kind TEXT NOT NULL, -- CASH ou INVESTMENT
                        from_investment_type_id INTEGER NULL,

                        to_kind TEXT NOT NULL,   -- CASH ou INVESTMENT
                        to_investment_type_id INTEGER NULL,

                        amount_cents INTEGER NOT NULL,
                        note TEXT,

                        FOREIGN KEY (from_investment_type_id) REFERENCES investment_types(id) ON DELETE CASCADE,
                        FOREIGN KEY (to_investment_type_id) REFERENCES investment_types(id) ON DELETE CASCADE
                    );
                    """);

            // Índices pra performance
            st.execute("CREATE INDEX IF NOT EXISTS idx_daily_snapshots_date ON daily_snapshots(date);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_daily_flows_date ON daily_flows(date);");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure schema", e);
        }
    }
}
