package com.daniel.repository;

import com.daniel.domain.Flow;
import com.daniel.domain.FlowKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class FlowRepository {
    private final Connection conn;

    public FlowRepository(Connection conn) {
        this.conn = conn;
    }

    private static Long nullableLong(ResultSet rs, String col) throws Exception {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public List<Flow> listForDate(LocalDate date) {
        String sql = """
            SELECT id, date, from_kind, from_investment_type_id, to_kind, to_investment_type_id, amount_cents, note
            FROM daily_flows
            WHERE date = ?
            ORDER BY id ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Flow> list = new ArrayList<>();
                while (rs.next()) {
                    Long fromId = nullableLong(rs, "from_investment_type_id");
                    Long toId = nullableLong(rs, "to_investment_type_id");

                    list.add(new Flow(
                            rs.getLong("id"),
                            LocalDate.parse(rs.getString("date")),
                            FlowKind.valueOf(rs.getString("from_kind")),
                            fromId,
                            FlowKind.valueOf(rs.getString("to_kind")),
                            toId,
                            rs.getLong("amount_cents"),
                            rs.getString("note")
                    ));
                }
                return list;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list flows", e);
        }
    }

    public long create(Flow flow) {
        String sql = """
            INSERT INTO daily_flows(date, from_kind, from_investment_type_id, to_kind, to_investment_type_id, amount_cents, note)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, flow.date().toString());
            ps.setString(2, flow.fromKind().name());
            ps.setObject(3, flow.fromInvestmentTypeId());
            ps.setString(4, flow.toKind().name());
            ps.setObject(5, flow.toInvestmentTypeId());
            ps.setLong(6, flow.amountCents());
            ps.setString(7, (flow.note() == null || flow.note().isBlank()) ? null : flow.note().trim());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new IllegalStateException("No generated id for flow");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create flow", e);
        }
    }

    public void delete(long id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM daily_flows WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete flow", e);
        }
    }
}
