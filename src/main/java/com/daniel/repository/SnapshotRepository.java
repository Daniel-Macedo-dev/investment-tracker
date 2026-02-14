package com.daniel.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class SnapshotRepository {

    private final Connection conn;

    public SnapshotRepository(Connection conn) {
        this.conn = conn;
    }

    public long getCash(LocalDate date) {
        String sql = "SELECT COALESCE(value_cents, amount_cents, 0) AS v FROM cash_snapshots WHERE date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("v");
                return 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read cash snapshot", e);
        }
    }

    public void upsertCash(LocalDate date, long cashCents) {
        // Prefer canonical "value_cents"
        String sql = """
            INSERT INTO cash_snapshots(date, value_cents)
            VALUES(?, ?)
            ON CONFLICT(date) DO UPDATE SET value_cents = excluded.value_cents
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setLong(2, cashCents);
            ps.executeUpdate();
        } catch (SQLException e) {
            fallbackUpsertCashLegacy(date, cashCents, e);
        }
    }

    private void fallbackUpsertCashLegacy(LocalDate date, long cashCents, SQLException original) {
        String legacySql = """
            INSERT INTO cash_snapshots(date, amount_cents)
            VALUES(?, ?)
            ON CONFLICT(date) DO UPDATE SET amount_cents = excluded.amount_cents
            """;
        try (PreparedStatement ps = conn.prepareStatement(legacySql)) {
            ps.setString(1, date.toString());
            ps.setLong(2, cashCents);
            ps.executeUpdate();
        } catch (SQLException e) {
            original.addSuppressed(e);
            throw new RuntimeException("Failed to upsert cash snapshot", original);
        }
    }

    public Map<Long, Long> getAllInvestmentsForDate(LocalDate date) {
        String sql = """
            SELECT investment_type_id,
                   COALESCE(value_cents, amount_cents, 0) AS v
            FROM investment_snapshots
            WHERE date = ?
            """;
        Map<Long, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("investment_type_id");
                    long v = rs.getLong("v");
                    out.put(id, v);
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list investment snapshots", e);
        }
    }

    public void upsertInvestment(LocalDate date, long investmentTypeId, long valueCents, String note) {
        String sql = """
            INSERT INTO investment_snapshots(date, investment_type_id, value_cents, note)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(date, investment_type_id)
            DO UPDATE SET value_cents = excluded.value_cents, note = excluded.note
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setLong(2, investmentTypeId);
            ps.setLong(3, valueCents);
            ps.setString(4, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            fallbackUpsertInvestmentLegacy(date, investmentTypeId, valueCents, note, e);
        }
    }

    private void fallbackUpsertInvestmentLegacy(LocalDate date, long investmentTypeId, long valueCents, String note, SQLException original) {
        String legacySql = """
            INSERT INTO investment_snapshots(date, investment_type_id, amount_cents, note)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(date, investment_type_id)
            DO UPDATE SET amount_cents = excluded.amount_cents, note = excluded.note
            """;
        try (PreparedStatement ps = conn.prepareStatement(legacySql)) {
            ps.setString(1, date.toString());
            ps.setLong(2, investmentTypeId);
            ps.setLong(3, valueCents);
            ps.setString(4, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            original.addSuppressed(e);
            throw new RuntimeException("Failed to upsert investment snapshot", original);
        }
    }

    public Map<String, Long> seriesForInvestment(long investmentTypeId) {
        String sql = """
            SELECT date, COALESCE(value_cents, amount_cents, 0) AS v
            FROM investment_snapshots
            WHERE investment_type_id = ?
            ORDER BY date ASC
            """;
        Map<String, Long> out = new TreeMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, investmentTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString("date"), rs.getLong("v"));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read investment series", e);
        }
    }
}
