package com.daniel.repository;

import com.daniel.domain.Flow;
import com.daniel.domain.FlowKind;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class FlowRepository {

    private final Connection conn;

    public FlowRepository(Connection conn) {
        this.conn = conn;
    }

    public List<Flow> listForDate(LocalDate date) {
        String sql = """
                SELECT id, date, from_kind, from_investment_type_id,
                       to_kind, to_investment_type_id, amount_cents, note
                FROM daily_flows
                WHERE date = ?
                ORDER BY id DESC
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());

            List<Flow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    LocalDate d = LocalDate.parse(rs.getString("date"));

                    FlowKind fromKind = FlowKind.valueOf(rs.getString("from_kind"));
                    Long fromInvId = readNullableLong(rs, "from_investment_type_id");

                    FlowKind toKind = FlowKind.valueOf(rs.getString("to_kind"));
                    Long toInvId = readNullableLong(rs, "to_investment_type_id");

                    long amount = rs.getLong("amount_cents");
                    String note = rs.getString("note");

                    out.add(new Flow(id, d, fromKind, fromInvId, toKind, toInvId, amount, note));
                }
            }
            return out;

        } catch (Exception e) {
            throw new RuntimeException("Failed to list flows", e);
        }
    }

    public long create(Flow f) {
        String sql = """
                INSERT INTO daily_flows (date, from_kind, from_investment_type_id, to_kind, to_investment_type_id, amount_cents, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.date().toString());
            ps.setString(2, f.fromKind().name());

            if (f.fromInvestmentTypeId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setLong(3, f.fromInvestmentTypeId());

            ps.setString(4, f.toKind().name());

            if (f.toInvestmentTypeId() == null) ps.setNull(5, Types.INTEGER);
            else ps.setLong(5, f.toInvestmentTypeId());

            ps.setLong(6, f.amountCents());
            ps.setString(7, f.note());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new RuntimeException("Failed to create flow: no generated key");

        } catch (Exception e) {
            throw new RuntimeException("Failed to create flow", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM daily_flows WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete flow", e);
        }
    }

    private static Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
