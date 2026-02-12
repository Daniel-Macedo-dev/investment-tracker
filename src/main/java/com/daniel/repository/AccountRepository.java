package com.daniel.repository;

import com.daniel.domain.AccountType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public final class AccountRepository {
    private final Connection conn;

    public AccountRepository(Connection conn) {
        this.conn = conn;
    }

    public long ensureDefaultCashAccount() {
        Long existing = findIdByNameAndType("Dinheiro Livre", AccountType.CASH);
        if (existing != null) return existing;

        String sql = "INSERT INTO accounts(name, type) VALUES(?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Dinheiro Livre");
            ps.setString(2, AccountType.CASH.name());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new IllegalStateException("No generated key returned for accounts");
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure default cash account", e);
        }
    }

    private Long findIdByNameAndType(String name, AccountType type) {
        String sql = "SELECT id FROM accounts WHERE name = ? AND type = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find account", e);
        }
    }
}
