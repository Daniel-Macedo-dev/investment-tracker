package com.daniel.repository;

import com.daniel.domain.AccountType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AccountRepository {
    private final Connection conn;

    public AccountRepository(Connection conn) {
        this.conn = conn;
    }

    public long ensureDefaultCashAccount() {
        Long existing = findIdByNameAndType("Dinheiro Livre", AccountType.CASH);
        if (existing != null) return existing;

        return createAccount("Dinheiro Livre", AccountType.CASH);
    }

    public long createInvestmentAccount(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Nome do investimento inválido.");
        }
        return createAccount(name.trim(), AccountType.INVESTMENT);
    }

    public List<AccountRow> listInvestmentAccounts() {
        String sql = "SELECT id, name, type FROM accounts WHERE type = ? ORDER BY name ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, AccountType.INVESTMENT.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<AccountRow> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new AccountRow(
                            rs.getLong("id"),
                            rs.getString("name"),
                            AccountType.valueOf(rs.getString("type"))
                    ));
                }
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list investment accounts", e);
        }
    }

    public String findNameById(long id) {
        String sql = "SELECT name FROM accounts WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find account name", e);
        }
    }

    private long createAccount(String name, AccountType type) {
        String sql = "INSERT INTO accounts(name, type) VALUES(?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, type.name());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new IllegalStateException("No generated key returned for accounts");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create account", e);
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

    public record AccountRow(long id, String name, AccountType type) {}
}
