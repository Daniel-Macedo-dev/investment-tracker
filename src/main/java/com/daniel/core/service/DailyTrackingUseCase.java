package com.daniel.core.service;

import com.daniel.core.domain.entity.*;
import com.daniel.core.domain.repository.*;
import com.daniel.infrastructure.persistence.repository.InvestmentTypeRepository;
import com.daniel.infrastructure.persistence.repository.SnapshotRepository;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public final class DailyTrackingUseCase {

    private final IFlowRepository flowRepo;
    private final IInvestmentTypeRepository typeRepo;
    private final ISnapshotRepository snapshotRepo;

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public DailyTrackingUseCase(
            IFlowRepository flowRepo,
            IInvestmentTypeRepository typeRepo,
            ISnapshotRepository snapshotRepo) {
        this.flowRepo = flowRepo;
        this.typeRepo = typeRepo;
        this.snapshotRepo = snapshotRepo;
    }

    // ========== INVESTMENT TYPES ==========

    public List<InvestmentType> listTypes() {
        return typeRepo.listAll();
    }

    public void createType(String name) {
        typeRepo.save(name);
    }

    public void renameType(int id, String newName) {
        typeRepo.rename(id, newName);
    }

    public void deleteType(int id) {
        typeRepo.delete(id);
    }

    public void createTypeFull(String name, String category, String liquidity,
                               LocalDate investmentDate, BigDecimal profitability,
                               BigDecimal investedValue) {
        if (typeRepo instanceof InvestmentTypeRepository repo) {
            repo.createFull(name, category, liquidity, investmentDate,
                    profitability, investedValue);
        } else {
            throw new UnsupportedOperationException("Repository não suporta createFull");
        }
    }

    public void updateTypeFull(int id, String name, String category, String liquidity,
                               LocalDate investmentDate, BigDecimal profitability,
                               BigDecimal investedValue) {
        if (typeRepo instanceof InvestmentTypeRepository repo) {
            repo.updateFull(id, name, category, liquidity, investmentDate,
                    profitability, investedValue);
        } else {
            throw new UnsupportedOperationException("Repository não suporta updateFull");
        }
    }

    // ========== DAILY ENTRY ==========

    public DailyEntry loadEntry(LocalDate date) {
        Map<Long, Long> invMap = snapshotRepo.getAllInvestimentsForDate(date);
        long cashCents = snapshotRepo.getCash(date);

        List<InvestmentType> allTypes = typeRepo.listAll();

        Map<InvestmentType, Long> investments = new LinkedHashMap<>();
        for (InvestmentType t : allTypes) {
            long cents = invMap.getOrDefault((long) t.id(), 0L);
            investments.put(t, cents);
        }

        return new DailyEntry(date, cashCents, investments);
    }

    public void saveEntry(DailyEntry entry) {
        if (entry == null) return;

        // Salvar caixa
        if (entry.cashCents() >= 0) {
            if (snapshotRepo instanceof SnapshotRepository repo) {
                repo.upsertCash(entry.date(), entry.cashCents());
            }
        }

        for (var e : entry.investmentValuesCents().entrySet()) {
            InvestmentType type = e.getKey();
            Long cents = e.getValue();
            if (cents != null && cents >= 0) {
                snapshotRepo.setInvestimentValue(entry.date(), type.id(), cents);

                if (snapshotRepo instanceof SnapshotRepository repo) {
                    repo.upsertInvestment(entry.date(), type.id(), cents, null);
                }
            }
        }
    }

    // ========== FLOWS ==========

    public List<Flow> flowsFor(LocalDate date) {
        return flowRepo.listForDate(date);
    }

    public void createFlow(Flow flow) {
        flowRepo.save(flow);
    }

    public void deleteFlow(long flowId) {
        flowRepo.delete(flowId);
    }

    // ========== SUMMARY ==========

    public boolean hasAnyDataPublic(LocalDate date) {
        DailyEntry entry = loadEntry(date);
        return entry.cashCents() > 0 ||
                entry.investmentValuesCents().values().stream()
                        .anyMatch(v -> v != null && v > 0);
    }

    public DailySummary summaryFor(LocalDate date) {
        DailyEntry entry = loadEntry(date);

        // Buscar dia anterior para calcular delta e lucro
        LocalDate prev = date.minusDays(1);
        DailyEntry prevEntry = loadEntry(prev);

        long cashCents = entry.cashCents();
        long prevCashCents = prevEntry.cashCents();
        long cashDeltaCents = cashCents - prevCashCents;

        Map<Long, Long> investmentTodayCents = new HashMap<>();
        Map<Long, Long> investmentProfitTodayCents = new HashMap<>();

        long totalInvCents = 0;
        long totalProfitCents = 0;

        for (var entryMap : entry.investmentValuesCents().entrySet()) {
            InvestmentType t = entryMap.getKey();
            long todayCents = entryMap.getValue() != null ? entryMap.getValue() : 0L;

            // Buscar valor de ontem
            long yesterdayCents = 0L;
            for (var prevMap : prevEntry.investmentValuesCents().entrySet()) {
                if (prevMap.getKey().id() == t.id()) {
                    yesterdayCents = prevMap.getValue() != null ? prevMap.getValue() : 0L;
                    break;
                }
            }

            investmentTodayCents.put((long) t.id(), todayCents);

            // Calcular lucro considerando fluxos
            List<Flow> flows = flowsFor(date);
            long flowsInCents = 0;
            long flowsOutCents = 0;

            for (Flow f : flows) {
                // Fluxo para este investimento
                if (f.toInvestmentTypeId() != null && f.toInvestmentTypeId() == t.id()) {
                    flowsInCents += f.amountCents();
                }
                // Fluxo saindo deste investimento
                if (f.fromInvestmentTypeId() != null && f.fromInvestmentTypeId() == t.id()) {
                    flowsOutCents += f.amountCents();
                }
            }

            long profitCents = todayCents - yesterdayCents - flowsInCents + flowsOutCents;
            investmentProfitTodayCents.put((long) t.id(), profitCents);

            totalInvCents += todayCents;
            totalProfitCents += profitCents;
        }

        long totalTodayCents = cashCents + totalInvCents;

        return new DailySummary(
                date,
                totalTodayCents,
                totalProfitCents,
                cashCents,
                cashDeltaCents,
                investmentTodayCents,
                investmentProfitTodayCents
        );
    }

    public DailySummary previewSummary(LocalDate date, long cashCents, Map<Long, Long> invMap) {
        // Preview sem salvar - para UI em tempo real
        LocalDate prev = date.minusDays(1);
        DailyEntry prevEntry = loadEntry(prev);

        long prevCashCents = prevEntry.cashCents();
        long cashDeltaCents = cashCents - prevCashCents;

        Map<Long, Long> investmentTodayCents = new HashMap<>(invMap);
        Map<Long, Long> investmentProfitTodayCents = new HashMap<>();

        long totalInvCents = 0;
        long totalProfitCents = 0;

        for (InvestmentType t : listTypes()) {
            long todayCents = invMap.getOrDefault((long) t.id(), 0L);

            // Buscar valor de ontem
            long yesterdayCents = 0L;
            for (var prevMap : prevEntry.investmentValuesCents().entrySet()) {
                if (prevMap.getKey().id() == t.id()) {
                    yesterdayCents = prevMap.getValue() != null ? prevMap.getValue() : 0L;
                    break;
                }
            }

            List<Flow> flows = flowsFor(date);
            long flowsInCents = 0;
            long flowsOutCents = 0;

            for (Flow f : flows) {
                if (f.toInvestmentTypeId() != null && f.toInvestmentTypeId() == t.id()) {
                    flowsInCents += f.amountCents();
                }
                if (f.fromInvestmentTypeId() != null && f.fromInvestmentTypeId() == t.id()) {
                    flowsOutCents += f.amountCents();
                }
            }

            long profitCents = todayCents - yesterdayCents - flowsInCents + flowsOutCents;
            investmentProfitTodayCents.put((long) t.id(), profitCents);

            totalInvCents += todayCents;
            totalProfitCents += profitCents;
        }

        long totalTodayCents = cashCents + totalInvCents;

        return new DailySummary(
                date,
                totalTodayCents,
                totalProfitCents,
                cashCents,
                cashDeltaCents,
                investmentTodayCents,
                investmentProfitTodayCents
        );
    }

    // ========== SERIES / CHARTS ==========

    public record SeriesPoint(LocalDate date, long valueCents) {}

    public List<SeriesPoint> seriesForInvestment(int investmentTypeId) {
        Map<String, Long> data = snapshotRepo.seriesForInvestiments(investmentTypeId);
        List<SeriesPoint> points = new ArrayList<>();

        for (var entry : data.entrySet()) {
            LocalDate date = LocalDate.parse(entry.getKey());
            points.add(new SeriesPoint(date, entry.getValue()));
        }

        return points;
    }

    // ========== RANGE SUMMARY ==========

    public record RangeSummary(
            long totalProfitCents,
            Map<Long, Long> profitByInvestmentCents
    ) {}

    public RangeSummary rangeSummary(LocalDate from, LocalDate to) {
        DailySummary first = summaryFor(from);
        DailySummary last = summaryFor(to);

        long totalProfit = last.totalProfitTodayCents() - first.totalProfitTodayCents();

        Map<Long, Long> profitByInv = new HashMap<>();
        for (Long invId : last.investmentProfitTodayCents().keySet()) {
            long lastProfit = last.investmentProfitTodayCents().getOrDefault(invId, 0L);
            long firstProfit = first.investmentProfitTodayCents().getOrDefault(invId, 0L);
            profitByInv.put(invId, lastProfit - firstProfit);
        }

        return new RangeSummary(totalProfit, profitByInv);
    }

    // ========== FORMATTING HELPERS ==========

    public String brl(long cents) {
        return BRL.format(cents / 100.0);
    }

    public String brlAbs(long cents) {
        return BRL.format(Math.abs(cents) / 100.0).replace("-", "");
    }
}