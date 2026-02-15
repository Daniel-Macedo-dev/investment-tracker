package com.daniel.core.service;

import com.daniel.core.domain.entity.*;
import com.daniel.core.domain.repository.*;
import com.daniel.infrastructure.persistence.repository.InvestmentTypeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class DailyTrackingUseCase {

    private final IFlowRepository flowRepo;
    private final IInvestmentTypeRepository typeRepo;
    private final ISnapshotRepository snapshotRepo;

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
            if (snapshotRepo instanceof com.daniel.infrastructure.persistence.repository.SnapshotRepository repo) {
                repo.upsertCash(entry.date(), entry.cashCents());
            }
        }

        // Salvar investimentos
        for (var e : entry.investments().entrySet()) {
            InvestmentType type = (InvestmentType) e.getKey();
            Long cents = (Long) e.getValue();
            if (cents != null && cents >= 0) {
                snapshotRepo.setInvestimentValue(entry.date(), type.id(), cents);

                if (snapshotRepo instanceof com.daniel.infrastructure.persistence.repository.SnapshotRepository repo) {
                    repo.upsertInvestment(entry.date(), type.id(), cents, null);
                }
            }
        }
    }

    // ========== FLOWS ==========

    public List<Flow> listFlows(LocalDate date) {
        if (flowRepo instanceof com.daniel.infrastructure.persistence.repository.FlowRepository repo) {
            return repo.listForDate(date);
        }
        return Collections.emptyList();
    }

    public void createFlow(Flow flow) {
        if (flowRepo instanceof com.daniel.infrastructure.persistence.repository.FlowRepository repo) {
            repo.create(flow);
        } else {
            flowRepo.save(flow);
        }
    }

    public void deleteFlow(long flowId) {
        if (flowRepo instanceof com.daniel.infrastructure.persistence.repository.FlowRepository repo) {
            repo.delete(flowId);
        }
    }

    // ========== SUMMARY ==========

    public DailySummary calculateSummary(LocalDate date) {
        DailyEntry entry = loadEntry(date);

        long totalInvestmentsCents = entry.investments().values().stream()
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        long totalCents = entry.cashCents() + totalInvestmentsCents;

        return new DailySummary(
                date,
                entry.cashCents(),
                totalInvestmentsCents,
                totalCents
        );
    }

    // ========== SERIES / CHARTS ==========

    public Map<String, Long> getInvestmentSeries(long investmentTypeId) {
        return snapshotRepo.seriesForInvestiments(investmentTypeId);
    }

    public Map<String, Long> getTotalSeries(LocalDate from, LocalDate to) {
        // Implementar conforme necessário
        Map<String, Long> series = new TreeMap<>();

        LocalDate current = from;
        while (!current.isAfter(to)) {
            DailySummary summary = calculateSummary(current);
            series.put(current.toString(), summary.totalCents());
            current = current.plusDays(1);
        }

        return series;
    }

    // ========== VALIDATION ==========

    public boolean hasDataForDate(LocalDate date) {
        DailyEntry entry = loadEntry(date);
        return entry.cashCents() > 0 ||
                entry.investments().values().stream().anyMatch(v -> v != null && v > 0);
    }

    public List<LocalDate> getDatesWithData(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = from;

        while (!current.isAfter(to)) {
            if (hasDataForDate(current)) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }

        return dates;
    }
}