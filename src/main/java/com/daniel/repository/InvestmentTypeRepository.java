package com.daniel.repository;

import com.daniel.domain.InvestmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class InvestmentTypeRepository {
    private final Connection conn;

    public InvestmentTypeRepository(Connection conn) {
        this.conn = conn;
    }

    public List<InvestmentType> listAll() {
        String sql = "SELECT id, name FROM investment_types ORDER BY name ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<InvestmentType> list = new ArrayList<>();
            while (rs.next()) list.add(new InvestmentType(rs.getLong("id"), rs.getString("name")));
            return list;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list investment types", e);
        }
    }

    public long create(String name) {
        if (name == null || name.trim().isBlank()) throw new IllegalArgumentException("Nome inválido.");
        String sql = "INSERT INTO investment_types(name) VALUES(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                throw new RuntimeException("Failed to get generated id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create investment type", e);
        }
    }

    public void rename(long id, String newName) {
        if (newName == null || newName.trim().isBlank()) throw new IllegalArgumentException("Nome inválido.");
        String sql = "UPDATE investment_types SET name = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName.trim());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to rename investment type", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM investment_types WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete investment type", e);
        }
    }
}
