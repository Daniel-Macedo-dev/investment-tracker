package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.core.service.DiversificationCalculator;
import com.daniel.core.service.DiversificationCalculator.*;
import com.daniel.infrastructure.api.BcbClient;
import com.daniel.infrastructure.api.BrapiClient;
import com.daniel.infrastructure.persistence.repository.AppSettingsRepository;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class DashboardPage implements Page {

    private final DailyTrackingUseCase daily;
    private final ScrollPane scrollPane = new ScrollPane();
    private final VBox root = new VBox(16);

    private final Label dateLabel = new Label("—");
    private final Label totalLabel = new Label("—");
    private final Label profitLabel = new Label("—");
    private final Label cdiComparisonLabel = new Label("—");

    private final PieChart pieChart = new PieChart();
    private final BarChart<String, Number> waterfallChart;
    private final LineChart<Number, Number> comparisonChart;

    private final VBox investmentsByCategoryContainer = new VBox(16);
    private final VBox rankPanel = new VBox(8);

    private double rateCdi = 0.135;
    private double rateSelic = 0.1175;
    private double rateIpca = 0.045;
    private double rateIbov = Double.NaN;
    private boolean ratesFetched = false;

    private int selectedFilterMonths = 12;
    private LocalDate customFrom = null;
    private LocalDate customTo = null;
    private boolean useCustomRange = false;
    private final HBox filterBar = new HBox(6);
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final HBox datePickerBox = new HBox(8);

    private final Label tokenWarningBanner = new Label(
            "⚠️  Token Brapi não configurado — cotações de ações usam preço de compra como referência. " +
            "Configure seu token na página Configurações para ver rentabilidade real.");
    private final AppSettingsRepository settingsRepo = new AppSettingsRepository();

    public DashboardPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Investimentos");
        yAxis.setLabel("Valor (R$)");
        waterfallChart = new BarChart<>(xAxis, yAxis);
        waterfallChart.setTitle("Composição do Patrimônio");
        waterfallChart.setLegendVisible(false);

        NumberAxis xAxisComparison = new NumberAxis();
        NumberAxis yAxisComparison = new NumberAxis();
        xAxisComparison.setLabel("Meses");
        yAxisComparison.setLabel("Rentabilidade %");
        comparisonChart = new LineChart<>(xAxisComparison, yAxisComparison);
        comparisonChart.setTitle("Rentabilidade da Carteira vs Benchmarks");
        comparisonChart.setMinHeight(300);
        comparisonChart.setCreateSymbols(false);

        root.setPadding(new Insets(16));

        Label h1 = new Label("Dashboard");
        h1.getStyleClass().add("h1");

        HBox cards = new HBox(12,
                card("Data", dateLabel),
                card("Patrimônio Total", totalLabel),
                card("Lucro/Prejuízo Total", profitLabel),
                card("vs CDI", cdiComparisonLabel)
        );

        Label h2 = new Label("Análise da Carteira");
        h2.getStyleClass().add("section-title");

        HBox chartsRow = new HBox(12);
        HBox.setHgrow(pieChart, Priority.ALWAYS);
        HBox.setHgrow(waterfallChart, Priority.ALWAYS);

        VBox pieBox = new VBox(8);
        pieBox.getStyleClass().add("card");
        Label pieTitle = new Label("Diversificação por Categoria");
        pieTitle.getStyleClass().add("card-title");
        pieChart.setMinHeight(300);
        pieChart.setLegendSide(Side.RIGHT);
        pieBox.getChildren().addAll(pieTitle, pieChart);

        VBox waterfallBox = new VBox(8);
        waterfallBox.getStyleClass().add("card");
        Label waterfallTitle = new Label("Distribuição de Valores");
        waterfallTitle.getStyleClass().add("card-title");
        waterfallChart.setMinHeight(300);
        waterfallBox.getChildren().addAll(waterfallTitle, waterfallChart);

        rankPanel.getStyleClass().add("card");
        rankPanel.setMinWidth(280);
        rankPanel.setMaxWidth(280);
        Label rankTitle = new Label("Top Rentabilidade Hoje");
        rankTitle.getStyleClass().add("card-title");
        Label rankLoading = new Label("Carregando...");
        rankLoading.getStyleClass().add("muted");
        rankPanel.getChildren().addAll(rankTitle, rankLoading);

        chartsRow.getChildren().addAll(pieBox, waterfallBox, rankPanel);

        VBox comparisonBox = new VBox(8);
        comparisonBox.getStyleClass().add("card");
        Label comparisonTitle = new Label("Comparação com Mercado");
        comparisonTitle.getStyleClass().add("card-title");
        buildFilterBar();
        comparisonBox.getChildren().addAll(comparisonTitle, filterBar, datePickerBox, comparisonChart);

        tokenWarningBanner.setWrapText(true);
        tokenWarningBanner.setStyle(
                "-fx-background-color: #78350f; -fx-text-fill: #fde68a; " +
                "-fx-padding: 10 14; -fx-background-radius: 6; -fx-font-size: 12px;");
        tokenWarningBanner.setVisible(false);
        tokenWarningBanner.setManaged(false);

        root.getChildren().addAll(h1, tokenWarningBanner, cards, h2, chartsRow, comparisonBox, investmentsByCategoryContainer);

        scrollPane.setContent(root);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-pane");
    }

    @Override
    public Parent view() {
        return scrollPane;
    }

    @Override
    public void onShow() {
        boolean hasToken = BrapiClient.hasToken();
        tokenWarningBanner.setVisible(!hasToken);
        tokenWarningBanner.setManaged(!hasToken);
        refreshData();
        if (!ratesFetched) {
            fetchRealRates();
        }
    }

    private void fetchRealRates() {
        CompletableFuture.supplyAsync(() -> {
            double cdi = BcbClient.fetchCdi().orElse(-1.0);
            double selic = BcbClient.fetchSelic().orElse(-1.0);
            double ipca = BcbClient.fetchIpca().orElse(-1.0);

            double ibov = Double.NaN;
            try {
                BrapiClient.StockData data = BrapiClient.fetchStockData("^BVSP");
                if (data != null && data.isValid()) {
                    ibov = data.regularMarketChangePercent();
                }
            } catch (Exception ignored) {}

            return new double[]{cdi, selic, ipca, ibov};
        }).thenAcceptAsync(rates -> Platform.runLater(() -> {
            if (rates[0] > 0) rateCdi = rates[0];
            if (rates[1] > 0) rateSelic = rates[1];
            if (rates[2] > 0) rateIpca = rates[2];
            if (!Double.isNaN(rates[3])) rateIbov = rates[3];
            ratesFetched = true;
            refreshData();
        }));
    }

    private void refreshData() {
        LocalDate today = LocalDate.now();
        dateLabel.setText(formatDate(today));

        List<InvestmentType> investments = daily.listTypes();

        if (investments.isEmpty()) {
            totalLabel.setText("—");
            profitLabel.setText("—");
            cdiComparisonLabel.setText("—");
            pieChart.getData().clear();
            waterfallChart.getData().clear();
            comparisonChart.getData().clear();
            investmentsByCategoryContainer.getChildren().clear();
            return;
        }

        Map<Long, Long> currentValues = daily.getAllCurrentValues(today);
        long totalPatrimony = daily.getTotalPatrimony(today);
        long totalProfit = daily.getTotalProfit(today);

        totalLabel.setText(daily.brl(totalPatrimony));

        profitLabel.setText(totalProfit == 0 ? "—" :
                ((totalProfit >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(totalProfit))));
        profitLabel.getStyleClass().removeAll("pos", "neg", "muted");
        if (totalProfit == 0) {
            profitLabel.getStyleClass().add("muted");
        } else {
            profitLabel.getStyleClass().add(totalProfit >= 0 ? "pos" : "neg");
        }

        updateCDIComparison(today, investments, totalPatrimony);
        updatePieChart(investments, currentValues);
        updateWaterfallChart(investments, currentValues);
        updateComparisonChart(investments, currentValues, today);
        updateInvestmentsByCategory(investments, currentValues, totalPatrimony);
        updateRankPanel(investments, currentValues);
    }

    private void updateCDIComparison(LocalDate today, List<InvestmentType> investments, long totalPatrimony) {
        LocalDate oldestDate = null;
        long totalInvested = 0L;

        for (InvestmentType inv : investments) {
            if (inv.investmentDate() != null) {
                if (oldestDate == null || inv.investmentDate().isBefore(oldestDate)) {
                    oldestDate = inv.investmentDate();
                }
            }
            if (inv.investedValue() != null) {
                totalInvested += inv.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
            }
        }

        if (oldestDate == null || totalInvested == 0) {
            cdiComparisonLabel.setText("Sem histórico");
            cdiComparisonLabel.getStyleClass().removeAll("pos", "neg", "muted");
            cdiComparisonLabel.getStyleClass().add("muted");
            return;
        }

        long months = java.time.temporal.ChronoUnit.MONTHS.between(oldestDate, today);
        if (months < 1) months = 1;

        CDIComparison comparison = DiversificationCalculator.compareWithCDI(
                totalInvested,
                totalPatrimony,
                (int) months,
                rateCdi
        );

        String text;
        if (comparison.outperformsCDI()) {
            text = String.format("↑ %.2f%% acima", Math.abs(comparison.difference()));
            cdiComparisonLabel.getStyleClass().removeAll("pos", "neg", "muted");
            cdiComparisonLabel.getStyleClass().add("pos");
        } else {
            text = String.format("↓ %.2f%% abaixo", Math.abs(comparison.difference()));
            cdiComparisonLabel.getStyleClass().removeAll("pos", "neg", "muted");
            cdiComparisonLabel.getStyleClass().add("neg");
        }

        cdiComparisonLabel.setText(text);
    }

    private void updatePieChart(List<InvestmentType> investments, Map<Long, Long> currentValues) {
        DiversificationData data = DiversificationCalculator.calculateCurrent(
                investments,
                currentValues
        );

        pieChart.getData().clear();

        if (data.totalCents() == 0) {
            pieChart.setTitle("Sem dados de diversificação");
            return;
        }

        pieChart.setTitle(null);

        for (CategoryAllocation alloc : data.allocations()) {
            if (alloc.valueCents() > 0) {
                String label = String.format("%s (%.1f%%)",
                        alloc.category().getDisplayName(),
                        alloc.percentage());

                PieChart.Data slice = new PieChart.Data(label, alloc.valueCents() / 100.0);
                pieChart.getData().add(slice);

                slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        String color = alloc.category().getColor();
                        newNode.setStyle("-fx-pie-color: " + color + ";");
                    }
                });
            }
        }
    }

    private void updateWaterfallChart(List<InvestmentType> investments, Map<Long, Long> currentValues) {
        waterfallChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Valores");

        List<InvestmentValue> invValues = new ArrayList<>();
        for (InvestmentType inv : investments) {
            long value = currentValues.getOrDefault((long) inv.id(), 0L);
            if (value > 0) {
                invValues.add(new InvestmentValue(inv.name(), value));
            }
        }

        invValues.sort((a, b) -> Long.compare(b.valueCents, a.valueCents));

        for (InvestmentValue iv : invValues) {
            XYChart.Data<String, Number> bar = new XYChart.Data<>(
                    truncateName(iv.name, 15),
                    iv.valueCents / 100.0
            );
            series.getData().add(bar);
        }

        waterfallChart.getData().add(series);

        for (XYChart.Data<String, Number> data : series.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-bar-fill: #16a34a;");
                }
            });
        }
    }

    private void buildFilterBar() {
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(4, 0, 4, 0));

        Map<String, Integer> filters = new LinkedHashMap<>();
        filters.put("1M", 1);
        filters.put("3M", 3);
        filters.put("6M", 6);
        filters.put("1A", 12);
        filters.put("3A", 36);
        filters.put("5A", 60);
        filters.put("10A", 120);

        ToggleGroup group = new ToggleGroup();
        List<ToggleButton> buttons = new ArrayList<>();

        for (var entry : filters.entrySet()) {
            ToggleButton btn = new ToggleButton(entry.getKey());
            btn.setToggleGroup(group);
            btn.setStyle("-fx-background-radius: 12; -fx-padding: 4 12;");
            int months = entry.getValue();
            btn.setOnAction(e -> {
                selectedFilterMonths = months;
                useCustomRange = false;
                datePickerBox.setVisible(false);
                datePickerBox.setManaged(false);
                refreshData();
            });
            if (months == 12) btn.setSelected(true);
            buttons.add(btn);
        }

        ToggleButton customBtn = new ToggleButton("Intervalo");
        customBtn.setToggleGroup(group);
        customBtn.setStyle("-fx-background-radius: 12; -fx-padding: 4 12;");
        customBtn.setOnAction(e -> {
            useCustomRange = true;
            datePickerBox.setVisible(true);
            datePickerBox.setManaged(true);
        });
        buttons.add(customBtn);

        filterBar.getChildren().addAll(buttons);

        fromPicker.setPromptText("De");
        fromPicker.setPrefWidth(130);
        toPicker.setPromptText("Até");
        toPicker.setPrefWidth(130);
        toPicker.setValue(LocalDate.now());

        Button applyBtn = new Button("Aplicar");
        applyBtn.getStyleClass().add("primary-btn");
        applyBtn.setStyle("-fx-padding: 4 12;");
        applyBtn.setOnAction(e -> {
            customFrom = fromPicker.getValue();
            customTo = toPicker.getValue();
            if (customFrom != null && customTo != null) {
                refreshData();
            }
        });

        datePickerBox.setAlignment(Pos.CENTER_LEFT);
        datePickerBox.setPadding(new Insets(4, 0, 0, 0));
        datePickerBox.getChildren().addAll(new Label("De:"), fromPicker, new Label("Até:"), toPicker, applyBtn);
        datePickerBox.setVisible(false);
        datePickerBox.setManaged(false);
    }

    private void updateComparisonChart(List<InvestmentType> investments,
                                       Map<Long, Long> currentValues,
                                       LocalDate today) {
        comparisonChart.getData().clear();

        long monthsRange;
        if (useCustomRange && customFrom != null && customTo != null) {
            monthsRange = java.time.temporal.ChronoUnit.MONTHS.between(customFrom, customTo);
        } else {
            monthsRange = selectedFilterMonths;
        }
        if (monthsRange < 1) monthsRange = 1;

        long totalInvested = 0L;
        for (InvestmentType inv : investments) {
            if (inv.investedValue() != null) {
                totalInvested += inv.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
            }
        }

        if (totalInvested == 0) {
            comparisonChart.setTitle("Sem dados para comparação");
            return;
        }

        XYChart.Series<Number, Number> portfolioSeries = new XYChart.Series<>();
        portfolioSeries.setName("Carteira");

        XYChart.Series<Number, Number> cdiSeries = new XYChart.Series<>();
        cdiSeries.setName(String.format("CDI (%.2f%% a.a.)", rateCdi * 100));

        XYChart.Series<Number, Number> selicSeries = new XYChart.Series<>();
        selicSeries.setName(String.format("SELIC (%.2f%% a.a.)", rateSelic * 100));

        XYChart.Series<Number, Number> ipcaSeries = new XYChart.Series<>();
        ipcaSeries.setName(String.format("IPCA (%.2f%% a.a.)", rateIpca * 100));

        XYChart.Series<Number, Number> ibovSeries = new XYChart.Series<>();
        ibovSeries.setName("IBOVESPA");

        double monthlyRateCDI = Math.pow(1 + rateCdi, 1.0/12) - 1;
        double monthlyRateSELIC = Math.pow(1 + rateSelic, 1.0/12) - 1;
        double monthlyRateIPCA = Math.pow(1 + rateIpca, 1.0/12) - 1;

        // IBOVESPA: estimate annual return from daily change % extrapolated to 12 months
        double ibovAnnual = !Double.isNaN(rateIbov) ? (rateIbov / 100.0) * 12 : Double.NaN;
        double monthlyRateIBOV = !Double.isNaN(ibovAnnual) ? Math.pow(1 + ibovAnnual, 1.0/12) - 1 : 0;

        // Carteira: taxa mensal implícita baseada na idade real do investimento mais antigo
        long currentTotal = daily.getTotalPatrimony(today);
        double totalReturnPct = totalInvested > 0
                ? ((currentTotal - totalInvested) * 100.0) / totalInvested : 0;
        long totalMonthsInvested = calcularMesesDesdeInvestimentoMaisAntigo(investments, today);
        if (totalMonthsInvested < 1) totalMonthsInvested = 1;
        double portfolioMonthlyRate = Math.pow(1 + totalReturnPct / 100.0, 1.0 / totalMonthsInvested) - 1;

        for (int month = 0; month <= monthsRange; month++) {
            double rentCDI = (Math.pow(1 + monthlyRateCDI, month) - 1) * 100;
            cdiSeries.getData().add(new XYChart.Data<>(month, rentCDI));

            double rentSELIC = (Math.pow(1 + monthlyRateSELIC, month) - 1) * 100;
            selicSeries.getData().add(new XYChart.Data<>(month, rentSELIC));

            double rentIPCA = (Math.pow(1 + monthlyRateIPCA, month) - 1) * 100;
            ipcaSeries.getData().add(new XYChart.Data<>(month, rentIPCA));

            if (!Double.isNaN(ibovAnnual)) {
                double rentIBOV = (Math.pow(1 + monthlyRateIBOV, month) - 1) * 100;
                ibovSeries.getData().add(new XYChart.Data<>(month, rentIBOV));
            }

            double rentPortfolio = (Math.pow(1 + portfolioMonthlyRate, month) - 1) * 100;
            portfolioSeries.getData().add(new XYChart.Data<>(month, rentPortfolio));
        }

        // Configurar eixo X com inteiros (meses)
        NumberAxis xAxis = (NumberAxis) comparisonChart.getXAxis();
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(monthsRange);
        xAxis.setTickUnit(Math.max(1, Math.ceil(monthsRange / 12.0)));
        xAxis.setMinorTickCount(0);

        comparisonChart.getData().addAll(portfolioSeries, cdiSeries, selicSeries, ipcaSeries);
        if (!ibovSeries.getData().isEmpty()) {
            comparisonChart.getData().add(ibovSeries);
        }

        applySeriesStyle(portfolioSeries, "#22c55e", 3);
        applySeriesStyle(cdiSeries, "#3b82f6", 2);
        applySeriesStyle(selicSeries, "#f59e0b", 2);
        applySeriesStyle(ipcaSeries, "#ef4444", 2);
        if (!ibovSeries.getData().isEmpty()) {
            applySeriesStyle(ibovSeries, "#8b5cf6", 2);
        }
    }

    private record RankEntry(String name, String ticker, double changePercent, long valueCents) {}

    private void updateRankPanel(List<InvestmentType> investments, Map<Long, Long> currentValues) {
        // Keep the title, show loading
        rankPanel.getChildren().removeIf(n -> !(n instanceof Label l && l.getStyleClass().contains("card-title")));
        Label loading = new Label("Carregando...");
        loading.getStyleClass().add("muted");
        rankPanel.getChildren().add(loading);

        CompletableFuture.supplyAsync(() -> {
            List<RankEntry> entries = new ArrayList<>();

            // Collect tickers that need Brapi lookup
            List<InvestmentType> withTicker = new ArrayList<>();
            List<InvestmentType> withoutTicker = new ArrayList<>();

            for (InvestmentType inv : investments) {
                if (inv.ticker() != null && !inv.ticker().isBlank()) {
                    withTicker.add(inv);
                } else {
                    withoutTicker.add(inv);
                }
            }

            // Agrupar por ticker (evita duplicatas no painel)
            if (!withTicker.isEmpty()) {
                Map<String, List<InvestmentType>> tickerGroups = new LinkedHashMap<>();
                for (InvestmentType inv : withTicker) {
                    tickerGroups.computeIfAbsent(
                            inv.ticker().toUpperCase().trim(), k -> new ArrayList<>()
                    ).add(inv);
                }

                // Buscar tickers únicos em batch
                String tickersStr = String.join(",", tickerGroups.keySet());
                Map<String, BrapiClient.StockData> stockMap = new HashMap<>();
                try {
                    stockMap = BrapiClient.fetchMultipleStocks(tickersStr);
                } catch (Exception ignored) {}

                // Uma entrada por ticker (soma de todas as compras)
                for (var tickerEntry : tickerGroups.entrySet()) {
                    String ticker = tickerEntry.getKey();
                    List<InvestmentType> group = tickerEntry.getValue();

                    BrapiClient.StockData data = stockMap.get(ticker);
                    double change = (data != null && data.isValid()) ? data.regularMarketChangePercent() : 0;

                    long totalValue = group.stream()
                            .mapToLong(inv -> currentValues.getOrDefault((long) inv.id(), 0L))
                            .sum();

                    String displayName = group.size() == 1 ? group.get(0).name() : ticker;
                    entries.add(new RankEntry(displayName, ticker, change, totalValue));
                }
            }

            // Fixed income: estimate daily variation = annual rate / 252
            for (InvestmentType inv : withoutTicker) {
                double dailyChange = 0;
                if (inv.profitability() != null) {
                    dailyChange = inv.profitability().doubleValue() / 252.0;
                }
                long value = currentValues.getOrDefault((long) inv.id(), 0L);
                entries.add(new RankEntry(inv.name(), null, dailyChange, value));
            }

            // Sort by |changePercent| descending, limit to 8
            entries.sort((a, b) -> Double.compare(Math.abs(b.changePercent()), Math.abs(a.changePercent())));
            if (entries.size() > 8) entries = entries.subList(0, 8);

            return entries;
        }).thenAcceptAsync(entries -> Platform.runLater(() -> {
            rankPanel.getChildren().removeIf(n -> !(n instanceof Label l && l.getStyleClass().contains("card-title")));

            if (entries.isEmpty()) {
                Label empty = new Label("Nenhum ativo cadastrado");
                empty.getStyleClass().add("muted");
                rankPanel.getChildren().add(empty);
                return;
            }

            for (RankEntry entry : entries) {
                rankPanel.getChildren().add(buildRankRow(entry));
            }
        }));
    }

    private HBox buildRankRow(RankEntry entry) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        row.setStyle("-fx-border-color: transparent transparent #1a2332 transparent; -fx-border-width: 0 0 1 0;");

        VBox nameBox = new VBox(1);
        Label nameLabel = new Label(entry.ticker() != null ? entry.ticker() : entry.name());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        if (entry.ticker() != null) {
            Label subLabel = new Label(entry.name());
            subLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.6;");
            nameBox.getChildren().addAll(nameLabel, subLabel);
        } else {
            nameBox.getChildren().add(nameLabel);
        }
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        VBox rightBox = new VBox(1);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        boolean positive = entry.changePercent() >= 0;
        Label changeLabel = new Label(String.format("%s%.2f%%", positive ? "+" : "", entry.changePercent()));
        changeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        changeLabel.getStyleClass().add(positive ? "pos" : "neg");

        Label valueLabel = new Label(daily.brl(entry.valueCents()));
        valueLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.6;");

        rightBox.getChildren().addAll(changeLabel, valueLabel);

        row.getChildren().addAll(nameBox, rightBox);
        return row;
    }

    private long calcularMesesDesdeInvestimentoMaisAntigo(List<InvestmentType> investments, LocalDate today) {
        return investments.stream()
                .filter(inv -> inv.investmentDate() != null)
                .mapToLong(inv -> java.time.temporal.ChronoUnit.MONTHS.between(inv.investmentDate(), today))
                .max()
                .orElse(1L);
    }

    private void applySeriesStyle(XYChart.Series<Number, Number> series, String color, int width) {
        series.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: " + width + "px;");
            }
        });
    }

    private void updateInvestmentsByCategory(List<InvestmentType> investments,
                                             Map<Long, Long> currentValues,
                                             long totalPatrimony) {
        investmentsByCategoryContainer.getChildren().clear();

        Label title = new Label("Investimentos por Categoria");
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 16px; -fx-padding: 16 0 8 0;");

        investmentsByCategoryContainer.getChildren().add(title);

        Map<CategoryEnum, List<InvestmentType>> byCategory = new LinkedHashMap<>();
        for (InvestmentType inv : investments) {
            if (inv.category() != null) {
                try {
                    CategoryEnum cat = CategoryEnum.valueOf(inv.category());
                    byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(inv);
                } catch (Exception ignored) {}
            }
        }

        List<Map.Entry<CategoryEnum, List<InvestmentType>>> sortedCategories = new ArrayList<>(byCategory.entrySet());
        sortedCategories.sort((a, b) -> {
            long totalA = a.getValue().stream()
                    .mapToLong(inv -> currentValues.getOrDefault((long)inv.id(), 0L)).sum();
            long totalB = b.getValue().stream()
                    .mapToLong(inv -> currentValues.getOrDefault((long)inv.id(), 0L)).sum();
            return Long.compare(totalB, totalA);
        });

        for (var entry : sortedCategories) {
            CategoryEnum category = entry.getKey();
            List<InvestmentType> categoryInvestments = entry.getValue();

            long categoryTotal = categoryInvestments.stream()
                    .mapToLong(inv -> currentValues.getOrDefault((long)inv.id(), 0L)).sum();

            if (categoryTotal == 0) continue;

            double categoryPercent = (categoryTotal * 100.0) / totalPatrimony;

            VBox categorySection = buildCategorySection(category, categoryInvestments,
                    currentValues, categoryPercent,
                    categoryTotal, totalPatrimony);
            investmentsByCategoryContainer.getChildren().add(categorySection);
        }
    }

    private VBox buildCategorySection(CategoryEnum category, List<InvestmentType> investments,
                                      Map<Long, Long> currentValues, double categoryPercent,
                                      long categoryTotal, long totalPatrimony) {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Circle circle = new Circle(6);
        circle.setFill(Color.web(category.getColor()));

        Label categoryName = new Label(category.getDisplayName());
        categoryName.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label categoryPercentLabel = new Label(String.format("%.1f%% • %s",
                categoryPercent, daily.brl(categoryTotal)));
        categoryPercentLabel.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(circle, categoryName, spacer, categoryPercentLabel);

        VBox investmentsList = new VBox(8);

        // ✅ AGRUPAR POR TICKER
        Map<String, List<InvestmentType>> grouped = new LinkedHashMap<>();
        List<InvestmentType> nonTickered = new ArrayList<>();

        for (InvestmentType inv : investments) {
            if (inv.ticker() != null && !inv.ticker().isBlank()) {
                String tickerUpper = inv.ticker().toUpperCase().trim();
                grouped.computeIfAbsent(tickerUpper, k -> new ArrayList<>()).add(inv);
            } else {
                nonTickered.add(inv);
            }
        }

        // DEBUG
        System.out.println("=== AGRUPAMENTO CATEGORIA: " + category.getDisplayName() + " ===");
        for (var entry : grouped.entrySet()) {
            System.out.println(entry.getKey() + " → " + entry.getValue().size() + " compras");
        }
        System.out.println("============================================");

        // Ordenar grupos por valor total
        List<Map.Entry<String, List<InvestmentType>>> sortedGroups = new ArrayList<>(grouped.entrySet());
        sortedGroups.sort((a, b) -> {
            long totalA = a.getValue().stream()
                    .mapToLong(inv -> currentValues.getOrDefault((long)inv.id(), 0L)).sum();
            long totalB = b.getValue().stream()
                    .mapToLong(inv -> currentValues.getOrDefault((long)inv.id(), 0L)).sum();
            return Long.compare(totalB, totalA);
        });

        // Adicionar grupos de tickers
        for (var entry : sortedGroups) {
            String ticker = entry.getKey();
            List<InvestmentType> tickerInvs = entry.getValue();

            long groupTotal = tickerInvs.stream()
                    .mapToLong(inv -> currentValues.getOrDefault((long)inv.id(), 0L)).sum();

            if (groupTotal == 0) continue;

            HBox invRow = buildGroupedStockRow(ticker, tickerInvs, groupTotal, totalPatrimony);
            investmentsList.getChildren().add(invRow);
        }

        // Adicionar investimentos não agrupados
        nonTickered.sort((a, b) -> {
            long valA = currentValues.getOrDefault((long)a.id(), 0L);
            long valB = currentValues.getOrDefault((long)b.id(), 0L);
            return Long.compare(valB, valA);
        });

        for (InvestmentType inv : nonTickered) {
            long currentValue = currentValues.getOrDefault((long)inv.id(), 0L);
            if (currentValue == 0) continue;

            HBox invRow = buildInvestmentRow(inv, currentValue, totalPatrimony);
            investmentsList.getChildren().add(invRow);
        }

        section.getChildren().addAll(header, new Separator(), investmentsList);
        return section;
    }

    private HBox buildGroupedStockRow(String ticker, List<InvestmentType> investments,
                                      long totalValueCents, long totalPatrimony) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 12; -fx-background-color: rgba(255,255,255,0.04); " +
                "-fx-background-radius: 8;");

        VBox mainInfo = new VBox(4);
        Label nameLabel = new Label(ticker);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label countLabel = new Label(investments.size() + (investments.size() == 1 ? " compra" : " compras"));
        countLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
        mainInfo.getChildren().addAll(nameLabel, countLabel);
        row.getChildren().add(mainInfo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        // ✅ CALCULAR CONSOLIDADO
        int qtdTotal = 0;
        double somaPrecoQtd = 0;
        long totalInvestido = 0;

        for (InvestmentType inv : investments) {
            if (inv.quantity() != null && inv.quantity() > 0) {
                qtdTotal += inv.quantity();

                if (inv.purchasePrice() != null) {
                    double preco = inv.purchasePrice().doubleValue();
                    somaPrecoQtd += (preco * inv.quantity());
                    totalInvestido += (long)(preco * inv.quantity() * 100);
                }
            }
        }

        double precoMedio = qtdTotal > 0 ? somaPrecoQtd / qtdTotal : 0;
        double posicaoAtual = totalValueCents / 100.0;
        double ultimoPreco = qtdTotal > 0 ? posicaoAtual / qtdTotal : 0;
        double rentabilidade = precoMedio > 0 ? ((ultimoPreco - precoMedio) / precoMedio) * 100 : 0;
        double alocacao = totalPatrimony > 0 ? (totalValueCents * 100.0) / totalPatrimony : 0;

        // Ticker Badge
        VBox tickerBox = new VBox(2);
        tickerBox.setAlignment(Pos.CENTER_RIGHT);
        Label tickerLabel = new Label(ticker);
        tickerLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label tickerHint = new Label("Ticker");
        tickerHint.setStyle("-fx-font-size: 10px; -fx-opacity: 0.5;");
        tickerBox.getChildren().addAll(tickerLabel, tickerHint);
        row.getChildren().add(tickerBox);

        // Valores
        row.getChildren().add(createInfoBox("Valor Investido", daily.brl(totalInvestido)));

        Label posicaoLabel = new Label(daily.brl(totalValueCents));
        posicaoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        row.getChildren().add(createInfoBox("Posição Atual", posicaoLabel));

        Label rentLabel;
        if (!BrapiClient.hasToken()) {
            rentLabel = new Label("—");
            rentLabel.setStyle("-fx-opacity: 0.45; -fx-font-size: 12px;");
        } else {
            rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
            rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                    " -fx-font-weight: bold; -fx-font-size: 12px;");
        }
        row.getChildren().add(createInfoBox("Rentabilidade", rentLabel));

        row.getChildren().add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));
        row.getChildren().add(createInfoBox("Preço Médio", String.format("R$ %.2f", precoMedio)));
        row.getChildren().add(createInfoBox("Último Preço", String.format("R$ %.2f",
                BrapiClient.hasToken() ? ultimoPreco : precoMedio)));

        row.getChildren().add(createInfoBox("Qtd Total", String.valueOf(qtdTotal)));

        return row;
    }

    private HBox buildInvestmentRow(InvestmentType inv, long currentValueCents, long totalPatrimony) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 12; -fx-background-color: rgba(255,255,255,0.04); " +
                "-fx-background-radius: 8;");

        VBox mainInfo = new VBox(4);
        Label nameLabel = new Label(inv.name());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        if (inv.typeOfInvestment() != null) {
            Label typeLabel = new Label(getTypeDisplayName(inv.typeOfInvestment()));
            typeLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
            mainInfo.getChildren().addAll(nameLabel, typeLabel);
        } else {
            mainInfo.getChildren().add(nameLabel);
        }
        row.getChildren().add(mainInfo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        if (inv.ticker() != null && !inv.ticker().isBlank()) {
            row.getChildren().addAll(buildStockInfo(inv, currentValueCents, totalPatrimony));
        } else if (inv.category() != null && inv.category().equals("RENDA_FIXA")) {
            row.getChildren().addAll(buildRendaFixaInfo(inv, currentValueCents, totalPatrimony));
        } else {
            row.getChildren().addAll(buildGenericInfo(inv, currentValueCents, totalPatrimony));
        }

        return row;
    }

    private List<javafx.scene.Node> buildStockInfo(InvestmentType inv, long currentValueCents, long totalPatrimony) {
        List<javafx.scene.Node> nodes = new ArrayList<>();

        VBox tickerBox = new VBox(2);
        tickerBox.setAlignment(Pos.CENTER_RIGHT);
        Label tickerLabel = new Label(inv.ticker());
        tickerLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label tickerHint = new Label("Ticker");
        tickerHint.setStyle("-fx-font-size: 10px; -fx-opacity: 0.5;");
        tickerBox.getChildren().addAll(tickerLabel, tickerHint);
        nodes.add(tickerBox);

        if (inv.quantity() != null && inv.purchasePrice() != null) {
            int qtdTotal = inv.quantity();
            double precoMedio = inv.purchasePrice().doubleValue();
            double posicaoAtual = currentValueCents / 100.0;
            double ultimoPreco = posicaoAtual / qtdTotal;

            long valorInvestidoCents = (long)(precoMedio * qtdTotal * 100);
            nodes.add(createInfoBox("Valor Investido", daily.brl(valorInvestidoCents)));

            Label posicaoLabel = new Label(daily.brl(currentValueCents));
            posicaoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Posição Atual", posicaoLabel));

            double rentabilidade = ((ultimoPreco - precoMedio) / precoMedio) * 100;
            Label rentLabel;
            if (!BrapiClient.hasToken()) {
                rentLabel = new Label("—");
                rentLabel.setStyle("-fx-opacity: 0.45; -fx-font-size: 12px;");
            } else {
                rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
                rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                        " -fx-font-weight: bold; -fx-font-size: 12px;");
            }
            nodes.add(createInfoBox("Rentabilidade", rentLabel));

            double alocacao = (currentValueCents * 100.0) / totalPatrimony;
            nodes.add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));
            nodes.add(createInfoBox("Preço Médio", String.format("R$ %.2f", precoMedio)));
            nodes.add(createInfoBox("Último Preço", String.format("R$ %.2f", ultimoPreco)));
            nodes.add(createInfoBox("Qtd Total", String.valueOf(qtdTotal)));

            if (inv.investmentDate() != null) {
                nodes.add(createInfoBox("Data Investimento",
                        inv.investmentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            }
        }

        return nodes;
    }

    private List<javafx.scene.Node> buildRendaFixaInfo(InvestmentType inv, long currentValueCents, long totalPatrimony) {
        List<javafx.scene.Node> nodes = new ArrayList<>();

        double alocacao = (currentValueCents * 100.0) / totalPatrimony;

        if (inv.investedValue() != null) {
            long aplicado = inv.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
            nodes.add(createInfoBox("Valor Investido", daily.brl(aplicado)));
            nodes.add(createInfoBox("Posição Atual", daily.brl(currentValueCents)));

            long lucro = currentValueCents - aplicado;
            double rentabilidade = (lucro * 100.0) / aplicado;
            Label rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
            rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                    " -fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Rentabilidade", rentLabel));
        }

        nodes.add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));

        if (inv.profitability() != null) {
            String taxa = String.format("%.2f%% a.a.", inv.profitability());
            nodes.add(createInfoBox("Taxa", taxa));
        }

        if (inv.investmentDate() != null) {
            nodes.add(createInfoBox("Data Investimento",
                    inv.investmentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }

        return nodes;
    }

    private List<javafx.scene.Node> buildGenericInfo(InvestmentType inv, long currentValueCents, long totalPatrimony) {
        List<javafx.scene.Node> nodes = new ArrayList<>();

        double alocacao = (currentValueCents * 100.0) / totalPatrimony;

        if (inv.investedValue() != null) {
            long investido = inv.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
            nodes.add(createInfoBox("Valor Investido", daily.brl(investido)));
            nodes.add(createInfoBox("Posição Atual", daily.brl(currentValueCents)));

            long lucro = currentValueCents - investido;
            double rentabilidade = investido > 0 ? (lucro * 100.0) / investido : 0;
            Label rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
            rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                    " -fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Rentabilidade", rentLabel));
        }

        nodes.add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));

        if (inv.investmentDate() != null) {
            nodes.add(createInfoBox("Data Investimento",
                    inv.investmentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }

        return nodes;
    }

    private VBox createInfoBox(String label, String value) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setStyle("-fx-min-width: 90;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Label labelLabel = new Label(label);
        labelLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.5;");

        box.getChildren().addAll(valueLabel, labelLabel);
        return box;
    }

    private VBox createInfoBox(String label, Label customLabel) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setStyle("-fx-min-width: 90;");

        customLabel.setStyle("-fx-font-size: 12px;");

        Label labelLabel = new Label(label);
        labelLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.5;");

        box.getChildren().addAll(customLabel, labelLabel);
        return box;
    }

    private String getTypeDisplayName(String type) {
        return switch (type) {
            case "PREFIXADO" -> "Prefixado";
            case "POS_FIXADO" -> "Pós-fixado";
            case "HIBRIDO" -> "Híbrido";
            case "ACAO" -> "Ação";
            default -> type;
        };
    }

    private VBox card(String title, Label value) {
        VBox box = new VBox(6);
        box.getStyleClass().add("card");
        Label t = new Label(title);
        t.getStyleClass().add("muted");
        value.getStyleClass().add("big-value");
        box.getChildren().addAll(t, value);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String truncateName(String name, int maxLength) {
        if (name.length() <= maxLength) return name;
        return name.substring(0, maxLength - 3) + "...";
    }

    private record InvestmentValue(String name, long valueCents) {}
}