package com.daniel.infrastructure.persistence.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final String URL = "jdbc:sqlite:investment_tracker.db";
    private static Connection connection = null;

    private Database() {
        // Singleton
    }

    public static synchronized Connection open() {
        try {
            if (connection == null) {
                System.out.println("🔧 Criando conexão com o banco de dados...");
                connection = DriverManager.getConnection(URL);

                // Criar tabelas
                System.out.println("🔧 Criando tabelas...");
                createTables();
                System.out.println("✅ Banco de dados pronto!");
            }

            if (connection.isClosed()) {
                System.out.println("⚠️ Connection estava fechada, reabrindo...");
                connection = DriverManager.getConnection(URL);
            }

            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar ao banco: " + e.getMessage(), e);
        }
    }

    private static void createTables() {
        try (Statement stmt = connection.createStatement()) {
            String sql = Schema.createTables();

            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }

        } catch (SQLException e) {
            System.err.println("⚠️ Erro ao criar tabelas: " + e.getMessage());
            throw new RuntimeException("Erro ao criar tabelas: " + e.getMessage(), e);
        }
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}