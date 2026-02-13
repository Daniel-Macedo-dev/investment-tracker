package com.daniel.service;

import com.daniel.domain.DailyEntry;
import com.daniel.domain.DailySummary;
import com.daniel.domain.InvestmentType;
import com.daniel.repository.InvestmentTypeRepository;
import com.daniel.repository.SnapshotRepository;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

public final class DailyService {

    public record SeriesPoint(LocalDate date, long valueCents) {}

    private final Connection conn;
    private final InvestmentTypeRepository typeRepo;
    private final SnapshotRepository snapRepo;

    public DailyService(Connection conn) {
        this.conn = conn;
        this.typeRepo = new InvestmentTypeRepository(conn);
        this.snapRepo = new SnapshotRepository(conn);
    }

    public List<InvestmentType> listTypes() {
        return typeRepo.listAll();
    }

    public long createType(String name) {
        return typeRepo.create(name);
    }

    public void renameType(long id, String newName) {
        typeRepo.rename(id, newName);
    }

    public void deleteType(long id) {
        typeRepo.delete(id);
    }

    public DailyEntry loadEntry(LocalDate date) {
        long cash = snapRepo.getCash(date);
        Map<Long, Long> inv = snapRepo.getAllInvestmentsForDate(date);
        return new DailyEntry(date, cash, inv);
    }

    public void saveEntry(DailyEntry entry) {
        try {
            conn.setAutoCommit(false);

            snapRepo.upsertCash(entry.date(), entry.cashCents());

            for (var e : entry.investmentValuesCents().entrySet()) {
                snapRepo.upsertInvestment(entry.date(), e.getKey(), e.getValue(), null);
            }

            conn.commit();
        } catch (Exception e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            throw new RuntimeException("Failed to save daily entry", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (Exception ignored) {}
        }
    }

    public DailySummary summaryFor(LocalDate date) {
        LocalDate prev = date.minusDays(1);

        List<InvestmentType> types = listTypes();

        boolean hasPrev = hasData(prev);

        long cashToday = snapRepo.getCash(date);
        long cashPrev = snapRepo.getCash(prev);
        long cashProfit = hasPrev ? (cashToday - cashPrev) : 0;

        Map<Long, Long> todayMap = new HashMap<>();
        Map<Long, Long> profitMap = new HashMap<>();

        long totalToday = cashToday;
        long totalPrev = cashPrev;

        Map<Long, Long> invTodayRaw = snapRepo.getAllInvestmentsForDate(date);
        Map<Long, Long> invPrevRaw = snapRepo.getAllInvestmentsForDate(prev);

        for (InvestmentType t : types) {
            long today = invTodayRaw.getOrDefault(t.id(), 0L);
            long prevVal = invPrevRaw.getOrDefault(t.id(), 0L);

            todayMap.put(t.id(), today);
            profitMap.put(t.id(), hasPrev ? (today - prevVal) : 0);

            totalToday += today;
            totalPrev += prevVal;
        }

        long totalProfit = hasPrev ? (totalToday - totalPrev) : 0;

        return new DailySummary(
                date,
                totalToday,
                totalProfit,
                cashToday,
                cashProfit,
                todayMap,
                profitMap
        );
    }

    private boolean hasData(LocalDate date) {
        long cash = snapRepo.getCash(date);
        return cash != 0 || !snapRepo.getAllInvestmentsForDate(date).isEmpty();
    }

    public List<SeriesPoint> seriesForInvestment(long investmentTypeId) {
        Map<String, Long> raw = snapRepo.seriesForInvestment(investmentTypeId);
        List<SeriesPoint> points = new ArrayList<>();
        for (var e : raw.entrySet()) {
            points.add(new SeriesPoint(LocalDate.parse(e.getKey()), e.getValue()));
        }
        points.sort(Comparator.comparing(SeriesPoint::date));
        return points;
    }

    public List<SeriesPoint> seriesTotalLastDays(int daysBack) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(daysBack);

        List<InvestmentType> types = listTypes();

        List<SeriesPoint> points = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long total = snapRepo.getCash(d);
            Map<Long, Long> inv = snapRepo.getAllInvestmentsForDate(d);
            for (InvestmentType t : types) total += inv.getOrDefault(t.id(), 0L);
            points.add(new SeriesPoint(d, total));
        }
        return points;
    }
}
