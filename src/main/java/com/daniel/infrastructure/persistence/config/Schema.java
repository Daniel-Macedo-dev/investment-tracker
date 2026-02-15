package com.daniel.infrastructure.persistence.config;

public final class Schema {

    private Schema() {
    }

    public static String createTables() {
        return """
                    -- Tipos de Investimento (ATUALIZADO com novos campos)
                    CREATE TABLE IF NOT EXISTS investment_type (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        category TEXT,
                        liquidity TEXT,
                        investment_date TEXT,
                        profitability REAL,
                        invested_value REAL
                    );
                
                    -- Fluxos
                    CREATE TABLE IF NOT EXISTS flows (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        date TEXT NOT NULL,
                        from_kind TEXT NOT NULL,
                        from_investment_type_id INTEGER,
                        to_kind TEXT NOT NULL,
                        to_investment_type_id INTEGER,
                        amount_cents INTEGER NOT NULL,
                        note TEXT,
                        FOREIGN KEY (from_investment_type_id) REFERENCES investment_type(id) ON DELETE CASCADE,
                        FOREIGN KEY (to_investment_type_id) REFERENCES investment_type(id) ON DELETE CASCADE
                    );
                
                    -- Snapshots de Caixa
                    CREATE TABLE IF NOT EXISTS cash_snapshots (
                        date TEXT PRIMARY KEY,
                        value_cents INTEGER NOT NULL
                    );
                
                    -- Snapshots de Investimentos
                    CREATE TABLE IF NOT EXISTS investment_snapshots (
                        date TEXT NOT NULL,
                        investment_type_id INTEGER NOT NULL,
                        value_cents INTEGER NOT NULL,
                        note TEXT,
                        PRIMARY KEY (date, investment_type_id),
                        FOREIGN KEY (investment_type_id) REFERENCES investment_type(id) ON DELETE CASCADE
                    );
                
                    -- Índices para performance
                    CREATE INDEX IF NOT EXISTS idx_flows_date 
                        ON flows(date DESC);
                
                    CREATE INDEX IF NOT EXISTS idx_flows_from_type 
                        ON flows(from_investment_type_id);
                
                    CREATE INDEX IF NOT EXISTS idx_flows_to_type 
                        ON flows(to_investment_type_id);
                
                    CREATE INDEX IF NOT EXISTS idx_investment_snapshots_date 
                        ON investment_snapshots(date DESC);
                
                    CREATE INDEX IF NOT EXISTS idx_investment_snapshots_type 
                        ON investment_snapshots(investment_type_id);
                """;
    }

    /**
     * Script de migração para adicionar as novas colunas em bancos existentes
     */
    public static String migrationScript() {
        return """
                    -- Adicionar novas colunas se não existirem
                    ALTER TABLE investment_type ADD COLUMN category TEXT;
                    ALTER TABLE investment_type ADD COLUMN liquidity TEXT;
                    ALTER TABLE investment_type ADD COLUMN investment_date TEXT;
                    ALTER TABLE investment_type ADD COLUMN profitability REAL;
                    ALTER TABLE investment_type ADD COLUMN invested_value REAL;
                """;
    }

    /**
     * Verifica se as novas colunas existem
     */
    public static boolean needsMigration(java.sql.Connection conn) {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA table_info(investment_type)")) {

            boolean hasCategory = false;
            while (rs.next()) {
                String colName = rs.getString("name");
                if ("category".equals(colName)) {
                    hasCategory = true;
                    break;
                }
            }
            return !hasCategory;

        } catch (java.sql.SQLException e) {
            return true; // Assume que precisa de migração em caso de erro
        }
    }
}