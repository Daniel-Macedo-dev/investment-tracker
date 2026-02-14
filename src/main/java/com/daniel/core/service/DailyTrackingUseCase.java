package com.daniel.core.service;

import com.daniel.core.domain.entity.*;
import com.daniel.core.domain.repository.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public final class DailyTrackingUseCase {

    public record SeriesPoint(LocalDate date, long valueCents) {}

    private final IFlowRepository flowRepo;
    private final IInvestmentTypeRepository typeRepo;
    private final ISnapshotRepository snapRepo;

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));

    public DailyTrackingUseCase(IFlowRepository flowRepo,
                                IInvestmentTypeRepository typeRepo,
                                ISnapshotRepository snapRepo) {
        this.flowRepo = flowRepo;
        this.typeRepo = typeRepo;
        this.snapRepo = snapRepo;
    }

    /* ===================== Investment Types ===================== */

    public List<InvestmentType> listTypes() {
        return typeRepo.listAll();
    }

    public void saveType(InvestmentType type) {
        typeRepo.save(type);
    }

    public void deleteType(long id) {
        typeRepo.delete(id);
    }

    /* ===================== Daily Entry ===================== */

    public DailyEntry loadEntry(LocalDate date) {
        long cash = snapRepo.getCash(date);
        Map<Long, Long> inv = snapRepo.getAllInvestimentsForDate(date);
        return new DailyEntry(date, cash, inv);
    }

    public void saveEntry(DailyEntry entry) {
        snapRepo.setCash(entry.date());

        for (var e : entry.investmentValuesCents().entrySet()) {
            snapRepo.setInvestimentValue(entry.date(), e.getKey(), e.getValue());
        }
    }

    /* ===================== Flows ===================== */

    public List<Flow> flowsFor(LocalDate date) {
        return flowRepo.listForDate(date);
    }

    public void addFlow(LocalDate date,
                        FlowKind fromKind, Long fromInvId,
                        FlowKind toKind, Long toInvId,
                        long amountCents, String note) {

        validateFlow(fromKind, fromInvId, toKind, toInvId, amountCents);

        Flow flow = new Flow(0, date, fromKind, fromInvId, toKind, toInvId, amountCents, note);
        flowRepo.save(flow);
    }

    public void deleteFlow(long id) {
        flowRepo.delete(id);
    }

    private void validateFlow(FlowKind fromKind, Long fromInvId,
                              FlowKind toKind, Long toInvId,
                              long amountCents) {

        if (amountCents <= 0)
            throw new IllegalArgumentException("Valor deve ser > 0.");

        if (fromKind == FlowKind.CASH && fromInvId != null)
            throw new IllegalArgumentException("Origem CASH não pode ter investimento.");

        if (toKind == FlowKind.CASH && toInvId != null)
            throw new IllegalArgumentException("Destino CASH não pode ter investimento.");

        if (fromKind == FlowKind.INVESTMENT && fromInvId == null)
            throw new IllegalArgumentException("Origem INVESTMENT precisa do tipo.");

        if (toKind == FlowKind.INVESTMENT && toInvId == null)
            throw new IllegalArgumentException("Destino INVESTMENT precisa do tipo.");

        if (fromKind == FlowKind.INVESTMENT &&
                toKind == FlowKind.INVESTMENT &&
                Objects.equals(fromInvId, toInvId)) {
            throw new IllegalArgumentException("Transferência para o mesmo investimento não faz sentido.");
        }
    }

    /* ===================== Summary ===================== */

    public DailySummary summaryFor(LocalDate date) {
        DailyEntry e = loadEntry(date);
        return previewSummary(date, e.cashCents(), e.investmentValuesCents());
    }

    public DailySummary previewSummary(LocalDate date,
                                       long cashTodayCents,
                                       Map<Long, Long> invTodayOverride) {

        LocalDate prev = date.minusDays(1);

        List<InvestmentType> types = listTypes();

        long cashPrev = snapRepo.getCash(prev);
        Map<Long, Long> invPrev = snapRepo.getAllInvestimentsForDate(prev);

        Map<Long, Long> flowNet = computeFlowNetByInvestment(date);

        Map<Long, Long> invProfitMarketMap = new HashMap<>();
        long totalToday = cashTodayCents;
        long totalPrev = cashPrev;

        for (InvestmentType t : types) {
            long today = invTodayOverride.getOrDefault(t.id(), 0L);
            long prevVal = invPrev.getOrDefault(t.id(), 0L);

            long delta = today - prevVal;
            long flow = flowNet.getOrDefault(t.id(), 0L);
            long profitMarket = delta - flow;

            invProfitMarketMap.put(t.id(), profitMarket);

            totalToday += today;
            totalPrev += prevVal;
        }

        long totalProfit = totalToday - totalPrev;

        return new DailySummary(
                date,
                totalToday,
                totalProfit,
                cashTodayCents,
                cashTodayCents - cashPrev,
                invTodayOverride,
                invProfitMarketMap
        );
    }

    private Map<Long, Long> computeFlowNetByInvestment(LocalDate date) {
        Map<Long, Long> net = new HashMap<>();

        for (Flow f : flowRepo.listForDate(date)) {
            if (f.toKind() == FlowKind.INVESTMENT && f.toInvestmentTypeId() != null) {
                net.merge(f.toInvestmentTypeId(), f.amountCents(), Long::sum);
            }
            if (f.fromKind() == FlowKind.INVESTMENT && f.fromInvestmentTypeId() != null) {
                net.merge(f.fromInvestmentTypeId(), -f.amountCents(), Long::sum);
            }
        }

        return net;
    }

    /* ===================== Series ===================== */

    public List<SeriesPoint> seriesForInvestment(long investmentTypeId) {
        Map<String, Long> raw = snapRepo.seriesForInvestiments(investmentTypeId);

        List<SeriesPoint> points = new ArrayList<>();
        for (var e : raw.entrySet()) {
            points.add(new SeriesPoint(LocalDate.parse(e.getKey()), e.getValue()));
        }

        points.sort(Comparator.comparing(SeriesPoint::date));
        return points;
    }

    /* ===================== Utils ===================== */

    public String brl(long cents) {
        return brl.format(cents / 100.0);
    }
}
