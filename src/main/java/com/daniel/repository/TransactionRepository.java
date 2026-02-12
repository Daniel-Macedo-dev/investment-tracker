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

    public long insertDeposit(long accountId, long amountCents, Instant createdAt, String note) {
        return insert(TransactionType.DEPOSIT, accountId, null, amountCents, createdAt, note);
    }

    public long insertWithdraw(long accountId, long amountCents, Instant createdAt, String note) {
        return insert(TransactionType.WITHDRAW, accountId, null, amountCents, createdAt, note);
    }

    public long insertTransfer(long fromAccountId, long toAccountId, long amountCents, Instant createdAt, String note) {
        return insert(TransactionType.TRANSFER, fromAccountId, toAccountId, amountCents, createdAt, note);
    }

    private long insert(TransactionType type, long fromAccountId, Long toAccountId, long amountCents, Instant createdAt, String note) {
        String sql = """
            INSERT INTO transactions(created_at, type, from_account_id, to_account_id, amount_cents, note)
            VALUES(?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, createdAt.toString());
            ps.setString(2, type.name());
            ps.setLong(3, fromAccountId);
            if (toAccountId == null) ps.setNull(4, java.sql.Types.INTEGER);
            else ps.setLong(4, toAccountId);
            ps.setLong(5, amountCents);
            ps.setString(6, (note == null || note.isBlank()) ? null : note.trim());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new IllegalStateException("No generated key returned for transactions");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert transaction", e);
        }
    }

    public List<Transaction> listForAccount(long accountId) {
        String sql = """
            SELECT id, created_at, type, from_account_id, to_account_id, amount_cents, note
            FROM transactions
            WHERE from_account_id = ? OR to_account_id = ?
            ORDER BY created_at DESC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setLong(2, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> list = new ArrayList<>();
                while (rs.next()) {
                    Long toId = (rs.getObject("to_account_id") == null) ? null : rs.getLong("to_account_id");
                    list.add(new Transaction(
                            rs.getLong("id"),
                            Instant.parse(rs.getString("created_at")),
                            TransactionType.valueOf(rs.getString("type")),
                            rs.getLong("from_account_id"),
                            toId,
                            rs.getLong("amount_cents"),
                            rs.getString("note")
                    ));
                }
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list transactions for account", e);
        }
    }

    public long balanceCents(long accountId) {
        String sql = """
            SELECT COALESCE(SUM(delta), 0) AS balance
            FROM (
                SELECT
                  CASE
                    WHEN type = 'DEPOSIT' AND from_account_id = ? THEN amount_cents
                    WHEN type = 'WITHDRAW' AND from_account_id = ? THEN -amount_cents
                    WHEN type = 'TRANSFER' AND from_account_id = ? THEN -amount_cents
                    WHEN type = 'TRANSFER' AND to_account_id = ? THEN amount_cents
                    ELSE 0
                  END AS delta
                FROM transactions
                WHERE from_account_id = ? OR to_account_id = ?
            )
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setLong(2, accountId);
            ps.setLong(3, accountId);
            ps.setLong(4, accountId);
            ps.setLong(5, accountId);
            ps.setLong(6, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getLong("balance");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute balance", e);
        }
    }
}
