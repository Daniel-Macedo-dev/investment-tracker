package com.daniel.service;

import com.daniel.domain.*;
import com.daniel.repository.FlowRepository;
import com.daniel.repository.InvestmentTypeRepository;
import com.daniel.repository.SnapshotRepository;

import java.sql.Connection;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public final class DailyService {

    public record SeriesPoint(LocalDate date, long valueCents) {}

    public record RangeSummary(LocalDate start, LocalDate end,
                               long totalProfitCents,
                               Map<Long, Long> profitByInvestmentCents,
                               Map<Long, DayExtremes> extremesByInvestment) {}

    public record DayExtremes(LocalDate bestDay, long bestProfitCents,
                              LocalDate worstDay, long worstProfitCents) {}

    private final Connection conn;
    private final InvestmentTypeRepository typeRepo;
    private final SnapshotRepository snapRepo;
    private final FlowRepository flowRepo;

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));

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

    public boolean hasAnyDataPublic(LocalDate date) {
        return hasAnyData(date);
    }

    private boolean hasAnyData(LocalDate date) {
        long cash = snapRepo.getCash(date);
        return cash != 0 || !snapRepo.getAllInvestmentsForDate(date).isEmpty() || !flowRepo.listForDate(date).isEmpty();
    }

    public DailySummary summaryFor(LocalDate date) {
        DailyEntry e = loadEntry(date);
        return previewSummary(date, e.cashCents(), e.investmentValuesCents());
    }

    public DailySummary previewSummary(LocalDate date, long cashTodayCents, Map<Long, Long> invTodayOverride) {
        LocalDate prev = date.minusDays(1);

        List<InvestmentType> types = listTypes();
        boolean hasPrev = hasAnyData(prev);

        long cashPrev = snapRepo.getCash(prev);

        Map<Long, Long> invPrev = snapRepo.getAllInvestmentsForDate(prev);
        Map<Long, Long> flowNetByInv = computeFlowNetByInvestment(date);

        Map<Long, Long> invTodayMap = new HashMap<>();
        Map<Long, Long> invProfitMarketMap = new HashMap<>();

        long totalToday = cashTodayCents;
        long totalPrev = cashPrev;

        for (InvestmentType t : types) {
            long today = invTodayOverride.getOrDefault(t.id(), 0L);
            long prevVal = invPrev.getOrDefault(t.id(), 0L);

            invTodayMap.put(t.id(), today);

            long delta = hasPrev ? (today - prevVal) : 0;
            long flowNet = flowNetByInv.getOrDefault(t.id(), 0L);

            long profitMarket = hasPrev ? (delta - flowNet) : 0;
            invProfitMarketMap.put(t.id(), profitMarket);

            totalToday += today;
            totalPrev += prevVal;
        }

        long sumInvProfit = 0;
        for (long p : invProfitMarketMap.values()) sumInvProfit += p;

        return new DailySummary(
                date,
                totalToday,
                sumInvProfit,
                cashTodayCents,
                hasPrev ? (cashTodayCents - cashPrev) : 0,
                invTodayMap,
                invProfitMarketMap
        );
    }

    private Map<Long, Long> computeFlowNetByInvestment(LocalDate date) {
        Map<Long, Long> net = new HashMap<>();
        for (Flow f : flowRepo.listForDate(date)) {
            if (f.toKind() == FlowKind.INVESTMENT) {
                Long idObj = f.toInvestmentTypeId();
                if (idObj != null) {
                    long id = idObj;
                    net.put(id, net.getOrDefault(id, 0L) + f.amountCents());
                }
            }
            if (f.fromKind() == FlowKind.INVESTMENT) {
                Long idObj = f.fromInvestmentTypeId();
                if (idObj != null) {
                    long id = idObj;
                    net.put(id, net.getOrDefault(id, 0L) - f.amountCents());
                }
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

    public String brl(long cents) { return brl.format(cents / 100.0); }
    public String brlAbs(long centsAbs) { return brl.format(centsAbs / 100.0); }

    public RangeSummary rangeSummary(LocalDate start, LocalDate end) {
        Map<Long, Long> profitByInv = new HashMap<>();
        Map<Long, LocalDate> bestDay = new HashMap<>();
        Map<Long, Long> bestProfit = new HashMap<>();
        Map<Long, LocalDate> worstDay = new HashMap<>();
        Map<Long, Long> worstProfit = new HashMap<>();

        long totalProfit = 0;

        List<InvestmentType> types = listTypes();
        for (InvestmentType t : types) {
            profitByInv.put(t.id(), 0L);
            bestProfit.put(t.id(), Long.MIN_VALUE);
            worstProfit.put(t.id(), Long.MAX_VALUE);
        }

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!hasAnyData(d)) continue;

            DailySummary s = summaryFor(d);
            totalProfit += s.totalProfitTodayCents();

            for (InvestmentType t : types) {
                long p = s.investmentProfitTodayCents().getOrDefault(t.id(), 0L);
                profitByInv.put(t.id(), profitByInv.get(t.id()) + p);

                if (p > bestProfit.get(t.id())) {
                    bestProfit.put(t.id(), p);
                    bestDay.put(t.id(), d);
                }
                if (p < worstProfit.get(t.id())) {
                    worstProfit.put(t.id(), p);
                    worstDay.put(t.id(), d);
                }
            }
        }

        Map<Long, DayExtremes> extremes = new HashMap<>();
        for (InvestmentType t : types) {
            extremes.put(t.id(), new DayExtremes(
                    bestDay.get(t.id()), bestProfit.get(t.id()) == Long.MIN_VALUE ? 0 : bestProfit.get(t.id()),
                    worstDay.get(t.id()), worstProfit.get(t.id()) == Long.MAX_VALUE ? 0 : worstProfit.get(t.id())
            ));
        }

        return new RangeSummary(start, end, totalProfit, profitByInv, extremes);
    }
}
