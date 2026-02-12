package com.daniel.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class Database {
    private static final String DB_FILE = "investment-tracker.db";

    private Database() {}

    public static Connection open() {
        try {
            Path dbPath = resolveDbPath();
            Files.createDirectories(dbPath.getParent());

            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            }

            Schema.ensure(conn);
            return conn;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open database", e);
        }
    }

    private static Path resolveDbPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "InvestmentTracker", DB_FILE);
            }
        }
        return Path.of(home, ".local", "share", "InvestmentTracker", DB_FILE);
    }
}
