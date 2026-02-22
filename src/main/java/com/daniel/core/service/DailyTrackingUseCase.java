package com.daniel.core.service;

import com.daniel.core.domain.entity.*;
import com.daniel.core.domain.entity.Enums.FlowKind;
import com.daniel.core.domain.repository.IFlowRepository;
import com.daniel.core.domain.repository.IInvestmentTypeRepository;
import com.daniel.core.domain.repository.ISnapshotRepository;
import com.daniel.core.util.Money;
import com.daniel.infrastructure.persistence.repository.FlowRepository;

import java.time.LocalDate;
import java.util.*;

public final class DailyTrackingUseCase {

    public record SeriesPoint(LocalDate date, long valueCents) {}

    public record DayExtremes(LocalDate bestDay, long bestProfitCents,
                              LocalDate worstDay, long worstProfitCents) {}

    public record RangeSummary(LocalDate start, LocalDate end,
                               long totalProfitCents,
                               Map<Long, Long> profitByInvestmentCents,
                               Map<Long, DayExtremes> extremesByInvestment) {}

    private final IFlowRepository flowRepo;
    private final IInvestmentTypeRepository typeRepo;
    private final ISnapshotRepository snapshotRepo;

    public DailyTrackingUseCase(
            IFlowRepository flowRepo,
            IInvestmentTypeRepository typeRepo,
            ISnapshotRepository snapshotRepo
    ) {
        this.flowRepo = flowRepo;
        this.typeRepo = typeRepo;
        this.snapshotRepo = snapshotRepo;
    }

    // ===== Types =====
    public List<InvestmentType> listTypes() { return typeRepo.listAll(); }
    public void createType(String name) { typeRepo.save(name); }
    public void renameType(long id, String newName) { typeRepo.rename(id, newName); }
    public void deleteType(long id) { typeRepo.delete(id); }

    // ===== Entry =====
    public DailyEntry loadEntry(LocalDate date) {
        long cash = snapshotRepo.getCash(date);
        Map<Long, Long> inv = snapshotRepo.getAllInvestimentsForDate(date);
        return new DailyEntry(date, cash, new LinkedHashMap<>(inv));
    }

    public void saveEntry(DailyEntry entry) {
        if (entry == null) return;

        // seu SnapshotRepository real tem upserts; a interface tem métodos incompletos
        if (snapshotRepo instanceof com.daniel.infrastructure.persistence.repository.SnapshotRepository repo) {
            repo.upsertCash(entry.date(), entry.cashCents());
            for (var e : entry.investmentValuesCents().entrySet()) {
                repo.upsertInvestment(entry.date(), e.getKey(), e.getValue(), null);
            }
        } else {
            // fallback (não ideal, mas mantém compilação)
            for (var e : entry.investmentValuesCents().entrySet()) {
                snapshotRepo.setInvestimentValue(entry.date(), e.getKey(), e.getValue());
            }
        }
    }

    // ===== Flows =====
    public List<Flow> flowsFor(LocalDate date) {
        return flowRepo.listForDate(date);
    }

    public long addFlow(LocalDate date,
                        FlowKind fromKind, Long fromInvId,
                        FlowKind toKind, Long toInvId,
                        long amountCents, String note) {

        validateFlow(fromKind, fromInvId, toKind, toInvId, amountCents);

        Flow f = new Flow(0, date, fromKind, fromInvId, toKind, toInvId, amountCents, note);

        if (flowRepo instanceof FlowRepository repo) {
            return repo.create(f);
        }
        flowRepo.save(f);
        return 0L;
    }

    public void deleteFlow(long flowId) {
        flowRepo.delete(flowId);
    }

    private void validateFlow(FlowKind fromKind, Long fromInvId,
                              FlowKind toKind, Long toInvId,
                              long amountCents) {

        if (amountCents <= 0) throw new IllegalArgumentException("Valor deve ser > 0.");

        if (fromKind == FlowKind.CASH && fromInvId != null) throw new IllegalArgumentException("Origem CASH não pode ter investimento.");
        if (toKind == FlowKind.CASH && toInvId != null) throw new IllegalArgumentException("Destino CASH não pode ter investimento.");

        if (fromKind == FlowKind.INVESTMENT && fromInvId == null) throw new IllegalArgumentException("Origem INVESTMENT precisa do tipo.");
        if (toKind == FlowKind.INVESTMENT && toInvId == null) throw new IllegalArgumentException("Destino INVESTMENT precisa do tipo.");

        if (fromKind == FlowKind.INVESTMENT && toKind == FlowKind.INVESTMENT && Objects.equals(fromInvId, toInvId)) {
            throw new IllegalArgumentException("Transferência para o mesmo investimento não faz sentido.");
        }
    }

    // ===== Summary (mercado = delta - fluxos) =====
    public boolean hasAnyDataPublic(LocalDate date) {
        long cash = snapshotRepo.getCash(date);
        boolean hasInv = snapshotRepo.getAllInvestimentsForDate(date).values().stream().anyMatch(v -> v != null && v != 0);
        boolean hasFlows = !flowsFor(date).isEmpty();
        return cash != 0 || hasInv || hasFlows;
    }

    public DailySummary summaryFor(LocalDate date) {
        DailyEntry e = loadEntry(date);
        return previewSummary(date, e.cashCents(), e.investmentValuesCents());
    }

    public DailySummary previewSummary(LocalDate date, long cashTodayCents, Map<Long, Long> invTodayOverride) {
        LocalDate prev = date.minusDays(1);

        boolean hasPrev = hasAnyDataPublic(prev);

        long cashPrev = snapshotRepo.getCash(prev);
        Map<Long, Long> invPrev = snapshotRepo.getAllInvestimentsForDate(prev);
        Map<Long, Long> flowNetByInv = computeFlowNetByInvestment(date);

        Map<Long, Long> invTodayMap = new LinkedHashMap<>();
        Map<Long, Long> invProfitMarketMap = new LinkedHashMap<>();

        long totalToday = cashTodayCents;

        for (InvestmentType t : listTypes()) {
            long id = t.id();

            long today = invTodayOverride.getOrDefault(id, 0L);
            long prevVal = invPrev.getOrDefault(id, 0L);

            invTodayMap.put(id, today);

            long delta = hasPrev ? (today - prevVal) : 0;
            long flowNet = flowNetByInv.getOrDefault(id, 0L);

            long profitMarket = hasPrev ? (delta - flowNet) : 0;
            invProfitMarketMap.put(id, profitMarket);

            totalToday += today;
        }

        long totalProfit = 0;
        for (long p : invProfitMarketMap.values()) totalProfit += p;

        long cashDelta = hasPrev ? (cashTodayCents - cashPrev) : 0;

        return new DailySummary(
                date,
                totalToday,
                totalProfit,
                cashTodayCents,
                cashDelta,
                invTodayMap,
                invProfitMarketMap
        );
    }

    private Map<Long, Long> computeFlowNetByInvestment(LocalDate date) {
        Map<Long, Long> net = new HashMap<>();

        for (Flow f : flowsFor(date)) {
            if (f.toKind() == FlowKind.INVESTMENT && f.toInvestmentTypeId() != null) {
                long id = f.toInvestmentTypeId();
                net.put(id, net.getOrDefault(id, 0L) + f.amountCents());
            }
            if (f.fromKind() == FlowKind.INVESTMENT && f.fromInvestmentTypeId() != null) {
                long id = f.fromInvestmentTypeId();
                net.put(id, net.getOrDefault(id, 0L) - f.amountCents());
            }
        }
        return net;
    }

    // ===== Charts =====
    public List<SeriesPoint> seriesForInvestment(long investmentTypeId) {
        Map<String, Long> raw = snapshotRepo.seriesForInvestiments(investmentTypeId);
        List<SeriesPoint> out = new ArrayList<>();
        for (var e : raw.entrySet()) out.add(new SeriesPoint(LocalDate.parse(e.getKey()), e.getValue()));
        out.sort(Comparator.comparing(SeriesPoint::date));
        return out;
    }

    // ===== Reports =====
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
            if (!hasAnyDataPublic(d)) continue;

            DailySummary s = summaryFor(d);
            totalProfit += s.totalProfitTodayCents();

            for (InvestmentType t : types) {
                long id = t.id();
                long p = s.investmentProfitTodayCents().getOrDefault(id, 0L);

                profitByInv.put(id, profitByInv.get(id) + p);

                if (p > bestProfit.get(id)) {
                    bestProfit.put(id, p);
                    bestDay.put(id, d);
                }
                if (p < worstProfit.get(id)) {
                    worstProfit.put(id, p);
                    worstDay.put(id, d);
                }
            }
        }

        Map<Long, DayExtremes> extremes = new HashMap<>();
        for (InvestmentType t : types) {
            long id = t.id();
            extremes.put(id, new DayExtremes(
                    bestDay.get(id), bestProfit.get(id) == Long.MIN_VALUE ? 0 : bestProfit.get(id),
                    worstDay.get(id), worstProfit.get(id) == Long.MAX_VALUE ? 0 : worstProfit.get(id)
            ));
        }

        return new RangeSummary(start, end, totalProfit, profitByInv, extremes);
    }

    // ===== Formatting =====
    public String brl(long cents) { return Money.centsToText(cents); }
    public String brlAbs(long centsAbs) { return Money.centsToText(centsAbs); }
}