package com.daniel.service;

import com.daniel.domain.*;
import com.daniel.repository.FlowRepository;
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
    private final FlowRepository flowRepo;

    public DailyService(Connection conn) {
        this.conn = conn;
        this.typeRepo = new InvestmentTypeRepository(conn);
        this.snapRepo = new SnapshotRepository(conn);
        this.flowRepo = new FlowRepository(conn);
    }

    public List<InvestmentType> listTypes() { return typeRepo.listAll(); }
    public long createType(String name) { return typeRepo.create(name); }
    public void renameType(long id, String newName) { typeRepo.rename(id, newName); }
    public void deleteType(long id) { typeRepo.delete(id); }

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

    // Flows
    public List<Flow> flowsFor(LocalDate date) {
        return flowRepo.listForDate(date);
    }

    public long addFlow(LocalDate date,
                        FlowKind fromKind, Long fromInvId,
                        FlowKind toKind, Long toInvId,
                        long amountCents, String note) {

        validateFlow(fromKind, fromInvId, toKind, toInvId, amountCents);

        Flow f = new Flow(0, date, fromKind, fromInvId, toKind, toInvId, amountCents, note);
        return flowRepo.create(f);
    }

    public void deleteFlow(long id) {
        flowRepo.delete(id);
    }

    private void validateFlow(FlowKind fromKind, Long fromInvId, FlowKind toKind, Long toInvId, long amountCents) {
        if (amountCents <= 0) throw new IllegalArgumentException("Valor deve ser > 0.");

        if (fromKind == FlowKind.CASH && fromInvId != null) throw new IllegalArgumentException("Origem CASH não pode ter investimento.");
        if (toKind == FlowKind.CASH && toInvId != null) throw new IllegalArgumentException("Destino CASH não pode ter investimento.");

        if (fromKind == FlowKind.INVESTMENT && fromInvId == null) throw new IllegalArgumentException("Origem INVESTMENT precisa do tipo.");
        if (toKind == FlowKind.INVESTMENT && toInvId == null) throw new IllegalArgumentException("Destino INVESTMENT precisa do tipo.");

        if (fromKind == FlowKind.INVESTMENT && toKind == FlowKind.INVESTMENT && Objects.equals(fromInvId, toInvId)) {
            throw new IllegalArgumentException("Transferência para o mesmo investimento não faz sentido.");
        }
    }

    public DailySummary summaryFor(LocalDate date) {
        LocalDate prev = date.minusDays(1);

        List<InvestmentType> types = listTypes();

        boolean hasPrev = hasAnyData(prev);

        long cashToday = snapRepo.getCash(date);
        long cashPrev = snapRepo.getCash(prev);

        Map<Long, Long> invToday = snapRepo.getAllInvestmentsForDate(date);
        Map<Long, Long> invPrev = snapRepo.getAllInvestmentsForDate(prev);

        Map<Long, Long> flowNetByInv = computeFlowNetByInvestment(date);

        Map<Long, Long> invTodayMap = new HashMap<>();
        Map<Long, Long> invProfitMarketMap = new HashMap<>();

        long totalToday = cashToday;
        long totalPrev = cashPrev;

        long totalFlowNetInvestments = 0;
        for (long v : flowNetByInv.values()) totalFlowNetInvestments += v;

        long cashFlowNet = -totalFlowNetInvestments;

        long cashProfitMarket = 0;
        long cashDelta = hasPrev ? (cashToday - cashPrev) : 0;

        for (InvestmentType t : types) {
            long today = invToday.getOrDefault(t.id(), 0L);
            long prevVal = invPrev.getOrDefault(t.id(), 0L);

            invTodayMap.put(t.id(), today);

            long delta = hasPrev ? (today - prevVal) : 0;
            long flowNet = flowNetByInv.getOrDefault(t.id(), 0L);

            long profitMarket = hasPrev ? (delta - flowNet) : 0;
            invProfitMarketMap.put(t.id(), profitMarket);

            totalToday += today;
            totalPrev += prevVal;
        }

        long totalDelta = hasPrev ? (totalToday - totalPrev) : 0;
        long totalProfitMarket = hasPrev ? (totalDelta - totalFlowNetInvestments - cashFlowNet) : 0;

        long sumInvProfit = 0;
        for (long p : invProfitMarketMap.values()) sumInvProfit += p;
        totalProfitMarket = sumInvProfit;

        return new DailySummary(
                date,
                totalToday,
                totalProfitMarket,
                cashToday,
                cashDelta,
                invTodayMap,
                invProfitMarketMap
        );
    }

    private boolean hasAnyData(LocalDate date) {
        long cash = snapRepo.getCash(date);
        return cash != 0 || !snapRepo.getAllInvestmentsForDate(date).isEmpty() || !flowRepo.listForDate(date).isEmpty();
    }

    private Map<Long, Long> computeFlowNetByInvestment(LocalDate date) {
        Map<Long, Long> net = new HashMap<>();
        for (Flow f : flowRepo.listForDate(date)) {
            if (f.toKind() == FlowKind.INVESTMENT) {
                long id = f.toInvestmentTypeId();
                net.put(id, net.getOrDefault(id, 0L) + f.amountCents());
            }
            if (f.fromKind() == FlowKind.INVESTMENT) {
                long id = f.fromInvestmentTypeId();
                net.put(id, net.getOrDefault(id, 0L) - f.amountCents());
            }
        }
        return net;
    }

    public List<SeriesPoint> seriesForInvestment(long investmentTypeId) {
        var raw = snapRepo.seriesForInvestment(investmentTypeId);
        List<SeriesPoint> points = new ArrayList<>();
        for (var e : raw.entrySet()) points.add(new SeriesPoint(LocalDate.parse(e.getKey()), e.getValue()));
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

    public DailyEntry suggestToday(java.time.LocalDate date) {
        java.time.LocalDate y = date.minusDays(1);

        DailyEntry yesterday = loadEntry(y);
        DailyEntry today = loadEntry(date);

        long cash = yesterday.cashCents();
        java.util.Map<Long, Long> inv = new java.util.HashMap<>(yesterday.investmentValuesCents());

        for (Flow f : flowsFor(date)) {
            long v = f.amountCents();

            if (f.fromKind() == FlowKind.CASH) cash -= v;
            else inv.put(f.fromInvestmentTypeId(), inv.getOrDefault(f.fromInvestmentTypeId(), 0L) - v);

            if (f.toKind() == FlowKind.CASH) cash += v;
            else inv.put(f.toInvestmentTypeId(), inv.getOrDefault(f.toInvestmentTypeId(), 0L) + v);
        }

        if (cash < 0) cash = 0;
        for (var e : inv.entrySet()) {
            if (e.getValue() < 0) e.setValue(0L);
        }

        java.util.Set<Long> currentTypes = listTypes().stream().map(InvestmentType::id).collect(java.util.stream.Collectors.toSet());
        inv.keySet().retainAll(currentTypes);

        return new DailyEntry(date, cash, inv);
    }

}
