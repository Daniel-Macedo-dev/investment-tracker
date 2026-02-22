package com.daniel.infrastructure.persistence.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final String URL = "jdbc:sqlite:investment_tracker.db";
    private static Connection connection;

    private Database() {}

    public static Connection open() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(URL);

                // ✅ cria tabelas (script multi-statement) corretamente
                executeSqlScript(connection, Schema.createTables());

                // ✅ migração (se necessário)
                if (Schema.needsMigration(connection)) {
                    System.out.println("🔄 Aplicando migração do banco de dados...");
                    executeSqlScript(connection, Schema.migrationScript());
                    System.out.println("✅ Migração concluída!");
                }
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar ao banco: " + e.getMessage(), e);
        }
    }

    /**
     * Executa script SQL com múltiplos comandos separados por ';'
     * e remove linhas de comentário iniciadas por '--'.
     */
    private static void executeSqlScript(Connection conn, String script) {
        if (script == null || script.isBlank()) return;

        String[] parts = script.split(";");

        for (String part : parts) {
            String sql = stripSqlComments(part).trim();
            if (sql.isEmpty()) continue;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException e) {
                // se algum comando falhar, loga e segue
                System.err.println("⚠️ Erro ao executar SQL (ignorado): " + e.getMessage());
            }
        }
    }

    private static String stripSqlComments(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.split("\\R"); // qualquer quebra de linha
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("--")) continue;
            if (t.isEmpty()) continue;
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}