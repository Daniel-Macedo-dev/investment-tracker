package com.daniel.infrastructure.persistence.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final String URL = "jdbc:sqlite:investment_tracker.db";
    private static Connection connection;

    private Database() {
        // Singleton
    }

    public static Connection open() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(URL);

                // Criar tabelas se não existirem
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(Schema.createTables());
                }

                // Aplicar migração se necessário
                if (Schema.needsMigration(connection)) {
                    System.out.println("🔄 Aplicando migração do banco de dados...");
                    applyMigration(connection);
                    System.out.println("✅ Migração concluída!");
                }
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar ao banco: " + e.getMessage(), e);
        }
    }

    private static void applyMigration(Connection conn) {
        String[] migrations = Schema.migrationScript().split(";");

        for (String migration : migrations) {
            String trimmed = migration.trim();
            if (trimmed.isEmpty()) continue;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(trimmed);
            } catch (SQLException e) {
                // Ignora erros de "coluna já existe"
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    System.err.println("⚠️  Erro na migração (ignorado): " + e.getMessage());
                }
            }
        }
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}