package com.daniel.core.service;

import com.daniel.core.domain.entity.*;
import com.daniel.core.domain.entity.Enums.FlowKind;
import com.daniel.core.domain.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DailyTrackingUseCase using lightweight in-memory stubs.
 * No JavaFX, no SQLite, no HTTP calls are involved.
 *
 * Skipped paths:
 *   - getCurrentValue() for investments with ticker → calls BrapiClient (HTTP)
 *   - saveEntry / takeSnapshotIfNeeded → use instanceof checks against concrete repo classes
 */
class DailyTrackingUseCaseTest {

    // ===== Minimal in-memory stubs =====

    static class StubTypeRepo implements IInvestmentTypeRepository {
        final List<InvestmentType> all = new ArrayList<>();

        void add(InvestmentType inv) { all.add(inv); }

        @Override public List<InvestmentType> listAll() { return Collections.unmodifiableList(all); }
        @Override public void save(String name) {}
        @Override public void rename(int id, String newName) {}
        @Override public void delete(long id) {}
    }

    static class StubFlowRepo implements IFlowRepository {
        final Map<LocalDate, List<Flow>> byDate = new HashMap<>();

        void add(LocalDate date, Flow flow) {
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(flow);
        }

        @Override public List<Flow> listForDate(LocalDate date) {
            return byDate.getOrDefault(date, List.of());
        }
        @Override public void save(Flow flow) {}
        @Override public void delete(long id) {}
        @Override public long create(Flow flow) { return 0; }
    }

    static class StubSnapshotRepo implements ISnapshotRepository {
        final Map<LocalDate, Long> cash = new HashMap<>();
        final Map<LocalDate, Map<Long, Long>> investments = new HashMap<>();

        void putCash(LocalDate date, long cents) { cash.put(date, cents); }
        void putInvestment(LocalDate date, long typeId, long cents) {
            investments.computeIfAbsent(date, k -> new HashMap<>()).put(typeId, cents);
        }

        @Override public long getCash(LocalDate date) { return cash.getOrDefault(date, 0L); }
        @Override public void setCash(LocalDate date) {}
        @Override public Map<Long, Long> getAllInvestimentsForDate(LocalDate date) {
            return investments.getOrDefault(date, Map.of());
        }
        @Override public void setInvestimentValue(LocalDate date, long typeId, long cents) {}
        @Override public Map<String, Long> seriesForInvestiments(long investimentsTypeId) { return Map.of(); }
    }

    static class StubTxRepo implements ITransactionRepository {
        final List<Transaction> all = new ArrayList<>();

        @Override public long insert(Transaction tx) { all.add(tx); return all.size(); }
        @Override public List<Transaction> listBetween(LocalDate start, LocalDate end) {
            return all.stream()
                    .filter(t -> !t.date().isBefore(start) && !t.date().isAfter(end))
                    .toList();
        }
    }

    // ===== Test fixtures =====

    private StubTypeRepo typeRepo;
    private StubFlowRepo flowRepo;
    private StubSnapshotRepo snapRepo;
    private StubTxRepo txRepo;
    private DailyTrackingUseCase uc;

    @BeforeEach
    void setUp() {
        typeRepo = new StubTypeRepo();
        flowRepo = new StubFlowRepo();
        snapRepo = new StubSnapshotRepo();
        txRepo   = new StubTxRepo();
        uc = new DailyTrackingUseCase(flowRepo, typeRepo, snapRepo, txRepo);
    }

    // ===== Formatting helpers =====

    @Test
    void brl_formatsPositiveCents() {
        String result = uc.brl(125000L); // R$ 1.250,00
        assertTrue(result.contains("1") && result.contains("250"),
                "Expected R$ 1.250,00 style in: " + result);
    }

    @Test
    void brl_zeroFormatsToZero() {
        String result = uc.brl(0L);
        assertTrue(result.contains("0"), "Expected '0' in: " + result);
    }

    @Test
    void brlAbs_negativeInputBecomesPositive() {
        String neg = uc.brl(-1250L);
        String abs = uc.brlAbs(-1250L);
        assertFalse(abs.contains("-"), "brlAbs must not contain '-', got: " + abs);
        // absolute value should equal positive formatted value
        assertEquals(uc.brlAbs(1250L), abs);
    }

    // ===== getAveragePrice =====

    @Test
    void getAveragePrice_singlePosition_equalsPrice() {
        typeRepo.add(new InvestmentType(
                1, "PETR4", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(3000),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(30.0), 100, null
        ));
        assertEquals(30.0, uc.getAveragePrice("PETR4"), 0.001);
    }

    @Test
    void getAveragePrice_twoPositions_weightedAverage() {
        // 100 * 30 = 3000, 50 * 40 = 2000 → total 5000 / 150 = 33.33
        typeRepo.add(new InvestmentType(
                1, "PETR4 A", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(3000),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(30.0), 100, null
        ));
        typeRepo.add(new InvestmentType(
                2, "PETR4 B", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(2000),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(40.0), 50, null
        ));
        assertEquals(100.0 / 3.0, uc.getAveragePrice("PETR4"), 0.01);
    }

    @Test
    void getAveragePrice_unknownTicker_returnsZero() {
        assertEquals(0.0, uc.getAveragePrice("XPTO3"), 0.001);
    }

    @Test
    void getAveragePrice_tickerMismatch_returnsZero() {
        typeRepo.add(new InvestmentType(
                1, "VALE3", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(2000),
                "ACAO", null, null, "VALE3", BigDecimal.valueOf(70.0), 50, null
        ));
        assertEquals(0.0, uc.getAveragePrice("PETR4"), 0.001);
    }

    // ===== getTotalQuantity =====

    @Test
    void getTotalQuantity_singlePosition() {
        typeRepo.add(new InvestmentType(
                1, "VALE3", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(2000),
                "ACAO", null, null, "VALE3", BigDecimal.valueOf(70.0), 50, null
        ));
        assertEquals(50, uc.getTotalQuantity("VALE3"));
    }

    @Test
    void getTotalQuantity_multiplePositions_summed() {
        typeRepo.add(new InvestmentType(
                1, "PETR4 A", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(3000),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(30.0), 100, null
        ));
        typeRepo.add(new InvestmentType(
                2, "PETR4 B", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(1500),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(30.0), 50, null
        ));
        assertEquals(150, uc.getTotalQuantity("PETR4"));
    }

    @Test
    void getTotalQuantity_unknownTicker_returnsZero() {
        assertEquals(0, uc.getTotalQuantity("XPTO3"));
    }

    // ===== groupByTicker =====

    @Test
    void groupByTicker_groupsCorrectly() {
        typeRepo.add(new InvestmentType(
                1, "PETR4 A", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(3000),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(30.0), 100, null
        ));
        typeRepo.add(new InvestmentType(
                2, "PETR4 B", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(1500),
                "ACAO", null, null, "PETR4", BigDecimal.valueOf(30.0), 50, null
        ));
        typeRepo.add(new InvestmentType(
                3, "VALE3", "ACOES", "MUITO_ALTA",
                LocalDate.now(), null, BigDecimal.valueOf(3500),
                "ACAO", null, null, "VALE3", BigDecimal.valueOf(70.0), 50, null
        ));

        Map<String, List<InvestmentType>> grouped = uc.groupByTicker();

        assertEquals(2, grouped.size());
        assertEquals(2, grouped.get("PETR4").size());
        assertEquals(1, grouped.get("VALE3").size());
    }

    @Test
    void groupByTicker_nullTicker_excluded() {
        typeRepo.add(new InvestmentType(1, "Tesouro")); // no ticker
        Map<String, List<InvestmentType>> grouped = uc.groupByTicker();
        assertTrue(grouped.isEmpty());
    }

    // ===== listTypes =====

    @Test
    void listTypes_delegatesToRepository() {
        typeRepo.add(new InvestmentType(1, "A"));
        typeRepo.add(new InvestmentType(2, "B"));
        assertEquals(2, uc.listTypes().size());
    }

    // ===== flowsFor =====

    @Test
    void flowsFor_returnsFlowsForDate() {
        LocalDate date = LocalDate.of(2024, 3, 7);
        Flow flow = new Flow(1L, date, FlowKind.CASH, null, FlowKind.INVESTMENT, 1L, 5000L, null);
        flowRepo.add(date, flow);

        List<Flow> result = uc.flowsFor(date);
        assertEquals(1, result.size());
        assertEquals(5000L, result.get(0).amountCents());
    }

    @Test
    void flowsFor_noFlows_returnsEmpty() {
        assertTrue(uc.flowsFor(LocalDate.now()).isEmpty());
    }

    // ===== summaryFor =====

    @Test
    void summaryFor_totalTodayCents_isCashPlusInvestments() {
        InvestmentType tesouro = new InvestmentType(
                1, "Tesouro", "RENDA_FIXA", "ALTA",
                LocalDate.of(2023, 1, 1), BigDecimal.valueOf(0.12), BigDecimal.valueOf(1000)
        );
        typeRepo.add(tesouro);

        LocalDate today = LocalDate.of(2024, 3, 7);
        snapRepo.putCash(today, 10000L);
        snapRepo.putInvestment(today, 1L, 50000L);

        DailySummary summary = uc.summaryFor(today);

        assertEquals(60000L, summary.totalTodayCents()); // 10000 + 50000
    }

    @Test
    void summaryFor_cashDelta_dayOverDay() {
        InvestmentType tesouro = new InvestmentType(
                1, "Tesouro", "RENDA_FIXA", "ALTA",
                LocalDate.of(2023, 1, 1), BigDecimal.valueOf(0.12), BigDecimal.valueOf(1000)
        );
        typeRepo.add(tesouro);

        LocalDate today    = LocalDate.of(2024, 3, 7);
        LocalDate yesterday = today.minusDays(1);

        snapRepo.putCash(today, 12000L);
        snapRepo.putCash(yesterday, 10000L);

        DailySummary summary = uc.summaryFor(today);

        assertEquals(2000L, summary.cashDeltaCents());
    }

    @Test
    void summaryFor_investmentProfit_withoutFlows() {
        InvestmentType tesouro = new InvestmentType(
                1, "Tesouro", "RENDA_FIXA", "ALTA",
                LocalDate.of(2023, 1, 1), BigDecimal.valueOf(0.12), BigDecimal.valueOf(1000)
        );
        typeRepo.add(tesouro);

        LocalDate today    = LocalDate.of(2024, 3, 7);
        LocalDate yesterday = today.minusDays(1);

        snapRepo.putInvestment(today, 1L, 51000L);
        snapRepo.putInvestment(yesterday, 1L, 50000L);

        DailySummary summary = uc.summaryFor(today);

        // profit = today - yesterday - flowsIn + flowsOut = 1000
        assertEquals(1000L, summary.investmentProfitTodayCents().get(1L));
    }

    @Test
    void summaryFor_investmentProfit_flowInReducesProfit() {
        InvestmentType tesouro = new InvestmentType(
                1, "Tesouro", "RENDA_FIXA", "ALTA",
                LocalDate.of(2023, 1, 1), BigDecimal.valueOf(0.12), BigDecimal.valueOf(1000)
        );
        typeRepo.add(tesouro);

        LocalDate today    = LocalDate.of(2024, 3, 7);
        LocalDate yesterday = today.minusDays(1);

        snapRepo.putInvestment(today, 1L, 55000L);
        snapRepo.putInvestment(yesterday, 1L, 50000L);

        // 3000 cents deposited into this investment (inflow)
        Flow inflow = new Flow(1L, today, FlowKind.CASH, null, FlowKind.INVESTMENT, 1L, 3000L, null);
        flowRepo.add(today, inflow);

        DailySummary summary = uc.summaryFor(today);

        // profit = 55000 - 50000 - 3000 = 2000
        assertEquals(2000L, summary.investmentProfitTodayCents().get(1L));
    }

    @Test
    void summaryFor_noData_allZero() {
        LocalDate today = LocalDate.of(2024, 3, 7);
        DailySummary summary = uc.summaryFor(today);

        assertEquals(0L, summary.totalTodayCents());
        assertEquals(0L, summary.cashTodayCents());
        assertEquals(0L, summary.cashDeltaCents());
        assertEquals(0L, summary.totalProfitTodayCents());
    }

    // ===== recordBuy / recordSell / listTransactions =====

    @Test
    void recordBuy_insertsTransaction() {
        LocalDate date = LocalDate.of(2024, 3, 7);
        uc.recordBuy(1, "PETR4", "PETR4", 100, 3000L, 300000L, date);

        List<Transaction> txs = uc.listTransactions(YearMonth.of(2024, 3));
        assertEquals(1, txs.size());
        assertEquals(Transaction.BUY, txs.get(0).type());
        assertEquals(100, txs.get(0).quantity());
        assertEquals(300000L, txs.get(0).totalCents());
    }

    @Test
    void recordSell_insertsTransaction() {
        LocalDate date = LocalDate.of(2024, 3, 7);
        uc.recordSell(1, "PETR4", "PETR4", 50, 3500L, 175000L, date, "Realizando lucro");

        List<Transaction> txs = uc.listTransactions(YearMonth.of(2024, 3));
        assertEquals(1, txs.size());
        assertEquals(Transaction.SELL, txs.get(0).type());
        assertEquals("Realizando lucro", txs.get(0).note());
    }

    @Test
    void listTransactions_filtersToMonth() {
        uc.recordBuy(1, "PETR4", "PETR4", 10, 3000L, 30000L, LocalDate.of(2024, 2, 15));
        uc.recordBuy(1, "PETR4", "PETR4", 20, 3000L, 60000L, LocalDate.of(2024, 3, 7));

        List<Transaction> march = uc.listTransactions(YearMonth.of(2024, 3));
        assertEquals(1, march.size());

        List<Transaction> feb = uc.listTransactions(YearMonth.of(2024, 2));
        assertEquals(1, feb.size());
    }

    // ===== hasAnyDataPublic =====

    @Test
    void hasAnyDataPublic_withCash_returnsTrue() {
        LocalDate today = LocalDate.of(2024, 3, 7);
        snapRepo.putCash(today, 100L);
        assertTrue(uc.hasAnyDataPublic(today));
    }

    @Test
    void hasAnyDataPublic_noData_returnsFalse() {
        assertFalse(uc.hasAnyDataPublic(LocalDate.of(2024, 3, 7)));
    }

    @Test
    void hasAnyDataPublic_withInvestmentValue_returnsTrue() {
        InvestmentType inv = new InvestmentType(1, "Tesouro");
        typeRepo.add(inv);

        LocalDate today = LocalDate.of(2024, 3, 7);
        snapRepo.putInvestment(today, 1L, 5000L);

        assertTrue(uc.hasAnyDataPublic(today));
    }
}
