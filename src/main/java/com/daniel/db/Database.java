package com.daniel.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class Database {

    private Database() {}

    public static Connection open() {
        try {
            Path dir = Path.of(System.getenv("APPDATA"), "InvestmentTracker");
            Files.createDirectories(dir);

            Path dbFile = dir.resolve("investment_tracker.db");
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            Connection conn = DriverManager.getConnection(url);

            Schema.ensure(conn);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='cash_snapshots'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Schema not applied: missing table cash_snapshots");
                    }
                }
            }

            return conn;

        } catch (Exception e) {
            throw new RuntimeException("Failed to open database", e);
        }
    }
}
