package com.daniel.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class SnapshotRepository {
    private final Connection conn;

    public SnapshotRepository(Connection conn) {
        this.conn = conn;
    }

    public long getCash(LocalDate date) {
        String sql = "SELECT value_cents FROM cash_snapshots WHERE date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read cash snapshot", e);
        }
    }

    public void upsertCash(LocalDate date, long valueCents) {
        String sql = """
            INSERT INTO cash_snapshots(date, value_cents) VALUES(?, ?)
            ON CONFLICT(date) DO UPDATE SET value_cents = excluded.value_cents
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setLong(2, valueCents);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert cash snapshot", e);
        }
    }

    public Map<Long, Long> getAllInvestmentsForDate(LocalDate date) {
        String sql = "SELECT investment_type_id, value_cents FROM daily_snapshots WHERE date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                Map<Long, Long> map = new HashMap<>();
                while (rs.next()) map.put(rs.getLong(1), rs.getLong(2));
                return map;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read daily snapshots", e);
        }
    }

    public void upsertInvestment(LocalDate date, long investmentTypeId, long valueCents, String note) {
        String sql = """
            INSERT INTO daily_snapshots(investment_type_id, date, value_cents, note)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(investment_type_id, date)
            DO UPDATE SET value_cents = excluded.value_cents, note = excluded.note
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, investmentTypeId);
            ps.setString(2, date.toString());
            ps.setLong(3, valueCents);
            ps.setString(4, (note == null || note.isBlank()) ? null : note.trim());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert investment snapshot", e);
        }
    }

    public Map<String, Long> seriesForInvestment(long investmentTypeId) {
        String sql = """
            SELECT date, value_cents
            FROM daily_snapshots
            WHERE investment_type_id = ?
            ORDER BY date ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, investmentTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> series = new HashMap<>();
                while (rs.next()) series.put(rs.getString(1), rs.getLong(2));
                return series;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load investment series", e);
        }
    }

    public Map<String, Long> seriesTotal(LocalDate from, LocalDate to, Iterable<Long> investmentIds) {
        throw new UnsupportedOperationException("Not used");
    }
}
