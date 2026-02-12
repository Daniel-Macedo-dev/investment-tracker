package com.daniel.repository;

import com.daniel.domain.Transaction;
import com.daniel.domain.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TransactionRepository {
    private final Connection conn;

    public TransactionRepository(Connection conn) {
        this.conn = conn;
    }

    public long insert(TransactionType type, long accountId, long amountCents, Instant createdAt, String note) {
        String sql = """
            INSERT INTO transactions(created_at, type, account_id, amount_cents, note)
            VALUES(?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, createdAt.toString());
            ps.setString(2, type.name());
            ps.setLong(3, accountId);
            ps.setLong(4, amountCents);
            ps.setString(5, (note == null || note.isBlank()) ? null : note.trim());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new IllegalStateException("No generated key returned for transactions");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert transaction", e);
        }
    }

    public List<Transaction> findAllByAccount(long accountId) {
        String sql = """
            SELECT id, created_at, type, account_id, amount_cents, note
            FROM transactions
            WHERE account_id = ?
            ORDER BY created_at DESC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Transaction(
                            rs.getLong("id"),
                            Instant.parse(rs.getString("created_at")),
                            TransactionType.valueOf(rs.getString("type")),
                            rs.getLong("account_id"),
                            rs.getLong("amount_cents"),
                            rs.getString("note")
                    ));
                }
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list transactions", e);
        }
    }

    public long computeCashBalanceCents(long accountId) {
        String sql = """
            SELECT COALESCE(SUM(
                CASE
                    WHEN type = 'DEPOSIT' THEN amount_cents
                    WHEN type = 'WITHDRAW' THEN -amount_cents
                    ELSE 0
                END
            ), 0) AS balance
            FROM transactions
            WHERE account_id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getLong("balance");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute balance", e);
        }
    }
}
