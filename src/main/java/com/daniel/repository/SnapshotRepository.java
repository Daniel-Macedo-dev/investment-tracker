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
        // Tenta o schema novo primeiro (value_cents). Se falhar por coluna inexistente, tenta o legado (amount_cents).
        try {
            return querySingleLong(
                    "SELECT value_cents FROM cash_snapshots WHERE date = ?",
                    date
            );
        } catch (RuntimeException ex) {
            if (!looksLikeMissingColumn(ex)) throw ex;

            return querySingleLong(
                    "SELECT amount_cents FROM cash_snapshots WHERE date = ?",
                    date
            );
        }
    }

    public void upsertCash(LocalDate date, long cashCents) {
        // Tenta inserir no schema novo; se falhar por coluna inexistente, tenta legado.
        try {
            execUpdate("""
                INSERT INTO cash_snapshots(date, value_cents)
                VALUES(?, ?)
                ON CONFLICT(date) DO UPDATE SET value_cents = excluded.value_cents
            """, date, cashCents);
        } catch (RuntimeException ex) {
            if (!looksLikeMissingColumn(ex)) throw ex;

            execUpdate("""
                INSERT INTO cash_snapshots(date, amount_cents)
                VALUES(?, ?)
                ON CONFLICT(date) DO UPDATE SET amount_cents = excluded.amount_cents
            """, date, cashCents);
        }
    }

    public Map<Long, Long> getAllInvestmentsForDate(LocalDate date) {
        // Schema novo: investment_snapshots.value_cents
        try {
            return queryInvestmentMap("""
                SELECT investment_type_id, value_cents
                FROM investment_snapshots
                WHERE date = ?
            """, date);
        } catch (RuntimeException ex) {
            if (!looksLikeMissingColumn(ex)) throw ex;

            // Legado: amount_cents
            return queryInvestmentMap("""
                SELECT investment_type_id, amount_cents
                FROM investment_snapshots
                WHERE date = ?
            """, date);
        }
    }

    public void upsertInvestment(LocalDate date, long investmentTypeId, long valueCents, String note) {
        String normalizedNote = (note == null || note.isBlank()) ? null : note.trim();

        // Novo
        try {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO investment_snapshots(date, investment_type_id, value_cents, note)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(date, investment_type_id)
                DO UPDATE SET value_cents = excluded.value_cents, note = excluded.note
            """)) {
                ps.setString(1, date.toString());
                ps.setLong(2, investmentTypeId);
                ps.setLong(3, valueCents);
                ps.setString(4, normalizedNote);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Se for “coluna não existe”, tenta legado (amount_cents)
            RuntimeException wrapped = new RuntimeException("Failed to upsert investment snapshot", e);
            if (!looksLikeMissingColumn(wrapped)) throw wrapped;

            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO investment_snapshots(date, investment_type_id, amount_cents, note)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(date, investment_type_id)
                DO UPDATE SET amount_cents = excluded.amount_cents, note = excluded.note
            """)) {
                ps.setString(1, date.toString());
                ps.setLong(2, investmentTypeId);
                ps.setLong(3, valueCents);
                ps.setString(4, normalizedNote);
                ps.executeUpdate();
            } catch (SQLException e2) {
                e.addSuppressed(e2);
                throw new RuntimeException("Failed to upsert investment snapshot (legacy)", e);
            }
        }
    }

    public Map<String, Long> seriesForInvestment(long investmentTypeId) {
        // Prefer novo
        try {
            return querySeries("""
                SELECT date, value_cents
                FROM investment_snapshots
                WHERE investment_type_id = ?
                ORDER BY date ASC
            """, investmentTypeId);
        } catch (RuntimeException ex) {
            if (!looksLikeMissingColumn(ex)) throw ex;

            return querySeries("""
                SELECT date, amount_cents
                FROM investment_snapshots
                WHERE investment_type_id = ?
                ORDER BY date ASC
            """, investmentTypeId);
        }
    }

    public Map<String, Long> seriesTotal(LocalDate from, LocalDate to, Iterable<Long> investmentIds) {
        // Não usado no teu projeto atual (mantive compatível com suas páginas)
        throw new UnsupportedOperationException("Not used");
    }

    // ---------------- helpers ----------------

    private long querySingleLong(String sql, LocalDate date) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read cash snapshot", e);
        }
    }

    private void execUpdate(String sql, LocalDate date, long cents) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setLong(2, cents);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert cash snapshot", e);
        }
    }

    private Map<Long, Long> queryInvestmentMap(String sql, LocalDate date) {
        Map<Long, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getLong(1), rs.getLong(2));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list investment snapshots", e);
        }
    }

    private Map<String, Long> querySeries(String sql, long investmentTypeId) {
        Map<String, Long> out = new TreeMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, investmentTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getLong(2));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load investment series", e);
        }
    }

    private static boolean looksLikeMissingColumn(RuntimeException ex) {
        Throwable t = ex;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains("no such column")) return true;
            t = t.getCause();
        }
        return false;
    }
}
