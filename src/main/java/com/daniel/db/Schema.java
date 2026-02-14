package com.daniel.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Centralized SQLite schema management.
 *
 * Goal:
 *  - App must boot even if the user already has an old/broken DB file in AppData.
 *  - We do NOT force the user to delete the DB.
 *  - We aggressively "repair" missing tables/columns and rename legacy tables/columns.
 */
public final class Schema {

    private Schema() {}

    public static void ensure(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        } catch (SQLException ignored) {
            // ignore - not critical for boot
        }

        // 1) Ensure schema_version exists (not strictly required, but useful)
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version(
                    version INTEGER NOT NULL
                )
                """);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM schema_version")) {
                if (rs.next() && rs.getInt("c") == 0) {
                    st.execute("INSERT INTO schema_version(version) VALUES (0)");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare schema version table", e);
        }

        // 2) Repair legacy table names BEFORE doing anything else.
        // Some older patches used "daily_flows", others used "flows".
        renameTableIfNeeded(conn, "daily_flows", "flows");

        // 3) Ensure required tables exist (IF NOT EXISTS)
        createCoreTables(conn);

        // 4) Repair legacy column names (value_cents vs amount_cents, etc.)
        repairColumns(conn);

        // 5) Bump schema_version to latest (best effort)
        try (Statement st = conn.createStatement()) {
            st.execute("UPDATE schema_version SET version = 4");
        } catch (SQLException ignored) {}
    }

    private static void createCoreTables(Connection conn) {
        try (Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS investment_types(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS cash_snapshots(
                    date TEXT PRIMARY KEY,
                    value_cents INTEGER NOT NULL
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS investment_snapshots(
                    date TEXT NOT NULL,
                    investment_type_id INTEGER NOT NULL,
                    value_cents INTEGER NOT NULL,
                    note TEXT,
                    PRIMARY KEY(date, investment_type_id),
                    FOREIGN KEY(investment_type_id) REFERENCES investment_types(id) ON DELETE CASCADE
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS flows(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date TEXT NOT NULL,

                    from_kind TEXT NOT NULL,
                    from_investment_type_id INTEGER NULL,

                    to_kind TEXT NOT NULL,
                    to_investment_type_id INTEGER NULL,

                    amount_cents INTEGER NOT NULL,
                    note TEXT,

                    FOREIGN KEY(from_investment_type_id) REFERENCES investment_types(id) ON DELETE SET NULL,
                    FOREIGN KEY(to_investment_type_id) REFERENCES investment_types(id) ON DELETE SET NULL
                )
                """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_flows_date ON flows(date)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_inv_snapshots_date ON investment_snapshots(date)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create core tables", e);
        }
    }

    private static void repairColumns(Connection conn) {
        // cash_snapshots: accept legacy "amount_cents" and rename/copy -> value_cents
        if (tableExists(conn, "cash_snapshots")) {
            boolean hasValue = columnExists(conn, "cash_snapshots", "value_cents");
            boolean hasAmount = columnExists(conn, "cash_snapshots", "amount_cents");
            if (!hasValue && hasAmount) {
                // Prefer rename (SQLite supports it in modern versions), fallback to add+copy.
                if (!tryRenameColumn(conn, "cash_snapshots", "amount_cents", "value_cents")) {
                    addColumn(conn, "cash_snapshots", "value_cents INTEGER NOT NULL DEFAULT 0");
                    execSilently(conn, "UPDATE cash_snapshots SET value_cents = amount_cents WHERE value_cents = 0");
                }
            } else if (!hasValue) {
                addColumn(conn, "cash_snapshots", "value_cents INTEGER NOT NULL DEFAULT 0");
            }
        }

        // investment_snapshots: accept legacy "amount_cents" and rename/copy -> value_cents
        if (tableExists(conn, "investment_snapshots")) {
            boolean hasValue = columnExists(conn, "investment_snapshots", "value_cents");
            boolean hasAmount = columnExists(conn, "investment_snapshots", "amount_cents");
            if (!hasValue && hasAmount) {
                if (!tryRenameColumn(conn, "investment_snapshots", "amount_cents", "value_cents")) {
                    addColumn(conn, "investment_snapshots", "value_cents INTEGER NOT NULL DEFAULT 0");
                    execSilently(conn, "UPDATE investment_snapshots SET value_cents = amount_cents WHERE value_cents = 0");
                }
            } else if (!hasValue) {
                addColumn(conn, "investment_snapshots", "value_cents INTEGER NOT NULL DEFAULT 0");
            }

            if (!columnExists(conn, "investment_snapshots", "note")) {
                addColumn(conn, "investment_snapshots", "note TEXT");
            }
        }

        // flows: accept legacy names if you ever used "value_cents" there
        if (tableExists(conn, "flows")) {
            if (!columnExists(conn, "flows", "amount_cents") && columnExists(conn, "flows", "value_cents")) {
                if (!tryRenameColumn(conn, "flows", "value_cents", "amount_cents")) {
                    addColumn(conn, "flows", "amount_cents INTEGER NOT NULL DEFAULT 0");
                    execSilently(conn, "UPDATE flows SET amount_cents = value_cents WHERE amount_cents = 0");
                }
            }
        }
    }

    private static void renameTableIfNeeded(Connection conn, String from, String to) {
        if (tableExists(conn, from) && !tableExists(conn, to)) {
            execSilently(conn, "ALTER TABLE " + from + " RENAME TO " + to);
        }
    }

    private static boolean tryRenameColumn(Connection conn, String table, String fromCol, String toCol) {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " RENAME COLUMN " + fromCol + " TO " + toCol);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void addColumn(Connection conn, String table, String colDef) {
        execSilently(conn, "ALTER TABLE " + table + " ADD COLUMN " + colDef);
    }

    private static void execSilently(Connection conn, String sql) {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException ignored) {}
    }

    private static boolean tableExists(Connection conn, String table) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (column.equalsIgnoreCase(name)) return true;
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }
}
