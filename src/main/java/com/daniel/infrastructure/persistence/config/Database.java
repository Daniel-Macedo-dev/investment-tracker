package com.daniel.infrastructure.persistence.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

public final class Database {

    private Database() {}

    public static Connection open() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".investment-tracker");
            Files.createDirectories(dir);

            Path dbFile = dir.resolve("investment-tracker.db");
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            Connection conn = DriverManager.getConnection(url);
            Schema.ensure(conn);
            return conn;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open database", e);
        }
    }
}
