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
    private final CategoryAxis compXAxis = new CategoryAxis();
    private final NumberAxis compYAxis = new NumberAxis();
    private final LineChart<String, Number> comparisonChart = new LineChart<>(compXAxis, compYAxis);

    private final VBox investmentsByCategoryContainer = new VBox(16);
    private final VBox rankPanelAltas = new VBox(8);
    private final VBox rankPanelBaixas = new VBox(8);
    private final HBox rankPanel = new HBox(12);

    private double rateCdi = 0.135;
    private double rateSelic = 0.1175;
    private double rateIpca = 0.045;
    private double rateIbov = Double.NaN;
    private boolean ratesFetched = false;

    private String selectedBenchmark = "CDI";
    private final Label metricRendimentoLabel = new Label("—");
    private final Label metricRentabilidadeLabel = new Label("—");
    private final Label metricBenchmarkLabel = new Label("—");
    private final Label metricBenchmarkTitleLabel = new Label("Rent. CDI");

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

        comparisonChart.setTitle(null);
        comparisonChart.setMinHeight(300);
        comparisonChart.setCreateSymbols(false);
        comparisonChart.setAnimated(false);

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
        rankPanel.setMinWidth(560);
        rankPanel.setMaxWidth(560);

        rankPanelAltas.setMinWidth(260);
        rankPanelAltas.setMaxWidth(260);
        rankPanelBaixas.setMinWidth(260);
        rankPanelBaixas.setMaxWidth(260);

        Label altasTitle = new Label("🟢 Maiores Altas");
        altasTitle.getStyleClass().add("card-title");
        altasTitle.setStyle("-fx-text-fill: #22c55e;");
        rankPanelAltas.getChildren().add(altasTitle);

        Label baixasTitle = new Label("🔴 Maiores Baixas");
        baixasTitle.getStyleClass().add("card-title");
        baixasTitle.setStyle("-fx-text-fill: #ef4444;");
        rankPanelBaixas.getChildren().add(baixasTitle);

        Separator vertSep = new Separator(javafx.geometry.Orientation.VERTICAL);
        rankPanel.getChildren().addAll(rankPanelAltas, vertSep, rankPanelBaixas);

        chartsRow.getChildren().addAll(pieBox, waterfallBox, rankPanel);

        VBox comparisonBox = buildComparisonSection();

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

            double ibov = BrapiClient.fetchIbovespaReturn().orElse(Double.NaN);

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

    private VBox buildComparisonSection() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");

        Label title = new Label("Performance da sua carteira");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        // Botões de benchmark
        ToggleGroup benchTg = new ToggleGroup();
        HBox benchBar = new HBox(8);
        benchBar.setAlignment(Pos.CENTER_RIGHT);

        for (String bench : List.of("CDI", "SELIC", "IPCA", "IBOVESPA")) {
            ToggleButton btn = new ToggleButton(bench);
            btn.setToggleGroup(benchTg);
            if (bench.equals("CDI")) {
                btn.setSelected(true);
                btn.setStyle(
                    "-fx-background-radius: 20; -fx-padding: 4 16;" +
                    "-fx-font-size: 12px; -fx-font-weight: bold;" +
                    "-fx-background-color: rgba(34,197,94,0.25);" +
                    "-fx-text-fill: #22c55e; -fx-border-color: #22c55e;" +
                    "-fx-border-radius: 20;");
            } else {
                btn.setStyle(
                    "-fx-background-radius: 20; -fx-padding: 4 16;" +
                    "-fx-font-size: 12px; -fx-font-weight: bold;" +
                    "-fx-background-color: rgba(255,255,255,0.08);" +
                    "-fx-text-fill: #bfead2;");
            }
            btn.setOnAction(e -> {
                selectedBenchmark = bench;
                benchTg.getToggles().forEach(t -> {
                    ToggleButton tb = (ToggleButton) t;
                    if (tb.isSelected()) {
                        tb.setStyle(
                            "-fx-background-radius: 20; -fx-padding: 4 16;" +
                            "-fx-font-size: 12px; -fx-font-weight: bold;" +
                            "-fx-background-color: rgba(34,197,94,0.25);" +
                            "-fx-text-fill: #22c55e; -fx-border-color: #22c55e;" +
                            "-fx-border-radius: 20;");
                    } else {
                        tb.setStyle(
                            "-fx-background-radius: 20; -fx-padding: 4 16;" +
                            "-fx-font-size: 12px; -fx-font-weight: bold;" +
                            "-fx-background-color: rgba(255,255,255,0.08);" +
                            "-fx-text-fill: #bfead2;");
                    }
                });
                metricBenchmarkTitleLabel.setText("Rent. " + bench);
                refreshData();
            });
            benchBar.getChildren().add(btn);
        }

        // Painel de métricas lateral
        metricRendimentoLabel.setStyle(
                "-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 16px;");
        metricRentabilidadeLabel.setStyle(
                "-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 16px;");
        metricBenchmarkLabel.setStyle(
                "-fx-text-fill: #3b82f6; -fx-font-weight: bold; -fx-font-size: 16px;");
        metricBenchmarkTitleLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");

        VBox metricsPanel = new VBox(14);
        metricsPanel.setAlignment(Pos.TOP_RIGHT);
        metricsPanel.setMinWidth(160);
        metricsPanel.setPadding(new Insets(4, 0, 0, 20));
        metricsPanel.getChildren().addAll(
                benchBar,
                buildMetricBox("Rendimento", metricRendimentoLabel),
                buildMetricBox("Rentabilidade", metricRentabilidadeLabel),
                buildMetricBoxCustomTitle(metricBenchmarkTitleLabel, metricBenchmarkLabel)
        );

        // Configurar gráfico
        compXAxis.setLabel(null);
        compYAxis.setLabel("Rentabilidade %");
        comparisonChart.setAnimated(false);
        comparisonChart.setCreateSymbols(false);
        comparisonChart.setLegendVisible(true);
        comparisonChart.setMinHeight(300);

        HBox chartRow = new HBox(0, comparisonChart);
        HBox.setHgrow(comparisonChart, Priority.ALWAYS);
        chartRow.setAlignment(Pos.TOP_LEFT);

        // metricsPanel fica fora do chartRow, em coluna separada à direita
        HBox contentRow = new HBox(12, chartRow, metricsPanel);
        HBox.setHgrow(chartRow, Priority.ALWAYS);
        contentRow.setAlignment(Pos.TOP_LEFT);

        // Barra de filtro de período
        buildFilterBar();

        box.getChildren().addAll(title, filterBar, datePickerBox, contentRow);
        return box;
    }

    private VBox buildMetricBox(String label, Label valueLabel) {
        VBox b = new VBox(3);
        b.setAlignment(Pos.CENTER_RIGHT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
        b.getChildren().addAll(valueLabel, lbl);
        return b;
    }

    private VBox buildMetricBoxCustomTitle(Label titleLabel, Label valueLabel) {
        VBox b = new VBox(3);
        b.setAlignment(Pos.CENTER_RIGHT);
        b.getChildren().addAll(valueLabel, titleLabel);
        return b;
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

        if (investments.isEmpty()) return;

        // Calcular total investido e patrimônio atual
        long totalInvestido = 0L;
        for (InvestmentType inv : investments) {
            if (inv.investedValue() != null) {
                totalInvestido += inv.investedValue()
                        .multiply(java.math.BigDecimal.valueOf(100)).longValue();
            }
        }
        if (totalInvestido == 0) return;

        long patrimonioAtual = currentValues.values().stream()
                .mapToLong(Long::longValue).sum();

        // Data de início: filtro selecionado ou a data do investimento mais antigo
        LocalDate dataInicio;
        LocalDate dataFim = today;
        if (useCustomRange && customFrom != null && customTo != null) {
            dataInicio = customFrom;
            dataFim = customTo;
        } else {
            dataInicio = today.minusMonths(selectedFilterMonths);
            LocalDate maisAntiga = investments.stream()
                    .filter(inv -> inv.investmentDate() != null)
                    .map(InvestmentType::investmentDate)
                    .min(LocalDate::compareTo)
                    .orElse(dataInicio);
            if (maisAntiga.isAfter(dataInicio)) dataInicio = maisAntiga;
        }

        long totalMeses = java.time.temporal.ChronoUnit.MONTHS.between(dataInicio, dataFim);
        if (totalMeses < 1) totalMeses = 1;

        // Rentabilidade total da carteira
        double rentTotalCarteira = (patrimonioAtual - totalInvestido) * 100.0 / totalInvestido;

        // Taxa mensal implícita da carteira
        long mesesTotaisCarteira = calcularMesesDesdeInvestimentoMaisAntigo(investments, today);
        if (mesesTotaisCarteira < 1) mesesTotaisCarteira = 1;
        double taxaMensalCarteira =
                Math.pow(1 + rentTotalCarteira / 100.0, 1.0 / mesesTotaisCarteira) - 1;

        // Taxa mensal do benchmark selecionado
        double taxaAnualBench = switch (selectedBenchmark) {
            case "SELIC"    -> rateSelic;
            case "IPCA"     -> rateIpca;
            case "IBOVESPA" -> Double.isNaN(rateIbov) ? 0.0 : rateIbov;
            default         -> rateCdi;
        };
        double taxaMensalBench = Math.pow(1 + taxaAnualBench, 1.0 / 12) - 1;

        // Séries
        XYChart.Series<String, Number> carteiraSeries = new XYChart.Series<>();
        carteiraSeries.setName("Carteira");

        XYChart.Series<String, Number> benchSeries = new XYChart.Series<>();
        benchSeries.setName(selectedBenchmark);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yy");
        long pontosGrafico = Math.min(totalMeses, mesesTotaisCarteira);

        for (long m = 0; m <= pontosGrafico; m++) {
            String label = dataInicio.plusMonths(m).format(fmt);
            double rentCart  = (Math.pow(1 + taxaMensalCarteira, m) - 1) * 100;
            double rentBench = (Math.pow(1 + taxaMensalBench,   m) - 1) * 100;
            carteiraSeries.getData().add(new XYChart.Data<>(label, rentCart));
            benchSeries.getData().add(new XYChart.Data<>(label, rentBench));
        }

        comparisonChart.getData().addAll(carteiraSeries, benchSeries);

        // Estilizar linhas e ícones de legenda após renderização
        // Carteira → laranja/amarelo (#f59e0b), Benchmark → verde (#22c55e)
        Platform.runLater(() -> {
            if (carteiraSeries.getNode() != null) {
                carteiraSeries.getNode().setStyle("-fx-stroke: #f59e0b; -fx-stroke-width: 2.5px;");
            }
            if (benchSeries.getNode() != null) {
                benchSeries.getNode().setStyle("-fx-stroke: #22c55e; -fx-stroke-width: 2px;");
            }
            // Corrigir ícones da legenda para combinar com as cores das linhas
            javafx.scene.Node sym0 = comparisonChart.lookup(".series0.chart-legend-item-symbol");
            if (sym0 != null) sym0.setStyle("-fx-background-color: #f59e0b, white;");
            javafx.scene.Node sym1 = comparisonChart.lookup(".series1.chart-legend-item-symbol");
            if (sym1 != null) sym1.setStyle("-fx-background-color: #22c55e, white;");
        });

        // Atualizar métricas laterais — sincronizadas com o período do filtro selecionado
        double rentCartPeriodo  = (Math.pow(1 + taxaMensalCarteira, (double) totalMeses) - 1) * 100;
        double rentBenchPeriodo = (Math.pow(1 + taxaMensalBench,    (double) totalMeses) - 1) * 100;
        long rendimentoPeriodo  = Math.round(totalInvestido * rentCartPeriodo / 100.0);

        String corGanho = rendimentoPeriodo >= 0
                ? "-fx-text-fill: #22c55e; -fx-font-weight: bold; -fx-font-size: 16px;"
                : "-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 16px;";

        metricRendimentoLabel.setText(daily.brl(rendimentoPeriodo));
        metricRendimentoLabel.setStyle(corGanho);

        metricRentabilidadeLabel.setText(String.format("%.2f%%", rentCartPeriodo));
        metricRentabilidadeLabel.setStyle(corGanho);

        boolean ibovUnavailable = "IBOVESPA".equals(selectedBenchmark) && Double.isNaN(rateIbov);
        metricBenchmarkLabel.setText(ibovUnavailable ? "—" : String.format("%.2f%%", rentBenchPeriodo));
        metricBenchmarkLabel.setStyle(
                "-fx-text-fill: #3b82f6; -fx-font-weight: bold; -fx-font-size: 16px;");
    }

    private record RankEntry(String name, String ticker, double changePercent, long valueCents) {}

    private void updateRankPanel(List<InvestmentType> investments, Map<Long, Long> currentValues) {
        // Keep the titles, show loading
        rankPanelAltas.getChildren().removeIf(n -> !(n instanceof Label l && l.getStyleClass().contains("card-title")));
        rankPanelBaixas.getChildren().removeIf(n -> !(n instanceof Label l && l.getStyleClass().contains("card-title")));
        Label loading = new Label("Carregando...");
        loading.getStyleClass().add("muted");
        rankPanelAltas.getChildren().add(loading);

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

            // Agrupar por ticker — garante uma entrada única por ticker no ranking
            if (!withTicker.isEmpty()) {
                Map<String, List<InvestmentType>> tickerGroups = new LinkedHashMap<>();
                for (InvestmentType inv : withTicker) {
                    tickerGroups.computeIfAbsent(
                            inv.ticker().trim().toUpperCase(), k -> new ArrayList<>()
                    ).add(inv);
                }

                // Buscar tickers únicos em batch (sem chamadas individuais por compra)
                String tickersStr = String.join(",", tickerGroups.keySet());
                Map<String, BrapiClient.StockData> rawMap = new HashMap<>();
                try {
                    rawMap = BrapiClient.fetchMultipleStocks(tickersStr);
                } catch (Exception ignored) {}

                // Normalizar chaves para uppercase — evita falha de lookup por casing da API
                final Map<String, BrapiClient.StockData> stockMap = new HashMap<>();
                for (var e : rawMap.entrySet()) {
                    stockMap.put(e.getKey().toUpperCase().trim(), e.getValue());
                }

                // Uma entrada por ticker (soma dos valores de todas as compras do grupo)
                for (var tickerEntry : tickerGroups.entrySet()) {
                    String ticker = tickerEntry.getKey();
                    List<InvestmentType> group = tickerEntry.getValue();

                    BrapiClient.StockData data = stockMap.get(ticker);
                    double change = (data != null && data.isValid()) ? data.regularMarketChangePercent() : 0;

                    long totalValue = group.stream()
                            .mapToLong(inv -> currentValues.getOrDefault((long) inv.id(), 0L))
                            .sum();

                    if (totalValue <= 0) continue; // ignorar tickers sem valor atual

                    String displayName = group.size() == 1 ? group.get(0).name() : ticker;
                    entries.add(new RankEntry(displayName, ticker, change, totalValue));
                }
            }

            // Renda fixa: variação diária estimada = taxa anual / 252
            for (InvestmentType inv : withoutTicker) {
                long value = currentValues.getOrDefault((long) inv.id(), 0L);
                if (value <= 0) continue; // ignorar ativos sem valor atual
                double dailyChange = inv.profitability() != null
                        ? inv.profitability().doubleValue() / 252.0
                        : 0;
                entries.add(new RankEntry(inv.name(), null, dailyChange, value));
            }

            // Retornar lista completa; separação em altas/baixas feita no Platform.runLater
            return entries;
        }).thenAcceptAsync(entries -> Platform.runLater(() -> {
            rankPanelAltas.getChildren().removeIf(n -> !(n instanceof Label l && l.getStyleClass().contains("card-title")));
            rankPanelBaixas.getChildren().removeIf(n -> !(n instanceof Label l && l.getStyleClass().contains("card-title")));

            if (entries.isEmpty()) {
                Label empty = new Label("Nenhum ativo");
                empty.getStyleClass().add("muted");
                rankPanelAltas.getChildren().add(empty);
                return;
            }

            List<RankEntry> altas = entries.stream()
                    .filter(e -> e.changePercent() >= 0)
                    .sorted((a, b) -> Double.compare(b.changePercent(), a.changePercent()))
                    .limit(4)
                    .toList();

            List<RankEntry> baixas = entries.stream()
                    .filter(e -> e.changePercent() < 0)
                    .sorted((a, b) -> Double.compare(a.changePercent(), b.changePercent()))
                    .limit(4)
                    .toList();

            if (altas.isEmpty()) {
                Label empty = new Label("Sem altas hoje");
                empty.getStyleClass().add("muted");
                rankPanelAltas.getChildren().add(empty);
            } else {
                for (RankEntry entry : altas) {
                    rankPanelAltas.getChildren().add(buildRankRow(entry));
                }
            }

            if (baixas.isEmpty()) {
                Label empty = new Label("Sem baixas hoje");
                empty.getStyleClass().add("muted");
                rankPanelBaixas.getChildren().add(empty);
            } else {
                for (RankEntry entry : baixas) {
                    rankPanelBaixas.getChildren().add(buildRankRow(entry));
                }
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

        // Agrupar por ticker normalizado: trim + uppercase evita "AURE3" vs "aure3 " separados
        Map<String, List<InvestmentType>> grouped = new LinkedHashMap<>();
        List<InvestmentType> nonTickered = new ArrayList<>();

        for (InvestmentType inv : investments) {
            if (inv.ticker() != null && !inv.ticker().isBlank()) {
                String tickerKey = inv.ticker().trim().toUpperCase();
                grouped.computeIfAbsent(tickerKey, k -> new ArrayList<>()).add(inv);
            } else {
                nonTickered.add(inv);
            }
        }

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
        // ─── Calcular consolidado (null quantity tratado como 0) ───
        int qtdTotal = 0;
        double somaPrecoQtd = 0;
        long totalInvestido = 0;
        LocalDate dataInvestimento = null;

        for (InvestmentType inv : investments) {
            int qty = (inv.quantity() != null) ? inv.quantity() : 0;
            double preco = (inv.purchasePrice() != null) ? inv.purchasePrice().doubleValue() : 0.0;

            qtdTotal += qty;
            somaPrecoQtd += preco * qty;
            totalInvestido += (long)(preco * qty * 100);

            // Data mais recente do grupo — calculada no mesmo loop
            if (inv.investmentDate() != null) {
                if (dataInvestimento == null || inv.investmentDate().isAfter(dataInvestimento)) {
                    dataInvestimento = inv.investmentDate();
                }
            }
        }

        // Cópias finais para captura nas lambdas
        final double precoMedio = (qtdTotal > 0) ? somaPrecoQtd / qtdTotal : 0.0;
        final int qtdFinal = qtdTotal;
        final double precoMedioFinal = precoMedio;
        final String tickerFinal = ticker;
        final double alocacao = totalPatrimony > 0 ? (totalValueCents * 100.0) / totalPatrimony : 0;

        // ─── Construir UI ───
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 12; -fx-background-color: rgba(255,255,255,0.04); " +
                "-fx-background-radius: 8;");

        VBox mainInfo = new VBox(4);
        Label nameLabel = new Label(ticker);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label qtdSubLabel = new Label("Qtd: " + qtdFinal);
        qtdSubLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
        mainInfo.getChildren().addAll(nameLabel, qtdSubLabel);
        row.getChildren().add(mainInfo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);

        // Ticker Badge
        VBox tickerBox = new VBox(2);
        tickerBox.setAlignment(Pos.CENTER_RIGHT);
        Label tickerLabel = new Label(ticker);
        tickerLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label tickerHint = new Label("Ticker");
        tickerHint.setStyle("-fx-font-size: 10px; -fx-opacity: 0.5;");
        tickerBox.getChildren().addAll(tickerLabel, tickerHint);
        row.getChildren().add(tickerBox);

        // Labels atualizados assincronamente (Posição Atual e Rentabilidade)
        Label rentLabel = new Label("...");
        rentLabel.setStyle("-fx-opacity: 0.5; -fx-font-size: 12px;");

        Label posicaoLabel = new Label(daily.brl(totalValueCents));
        posicaoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        row.getChildren().add(createInfoBox("Valor Investido", daily.brl(totalInvestido)));
        row.getChildren().add(createInfoBox("Posição Atual", posicaoLabel));
        row.getChildren().add(createInfoBox("Rentabilidade", rentLabel));
        row.getChildren().add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));
        // Qtd Total e Preço Médio são dados locais — sempre visíveis, independente de token
        row.getChildren().add(createInfoBox("Qtd Total", String.valueOf(qtdFinal)));
        row.getChildren().add(createInfoBox("Preço Médio", String.format("R$ %.2f", precoMedioFinal)));

        if (dataInvestimento != null) {
            row.getChildren().add(createInfoBox("Data Investimento",
                    dataInvestimento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }

        // ─── Busca assíncrona do preço atual via Brapi ───
        if (BrapiClient.hasToken() && qtdFinal > 0) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return BrapiClient.fetchStockData(tickerFinal);
                } catch (Exception e) {
                    return null;
                }
            }).thenAcceptAsync(data -> Platform.runLater(() -> {
                if (data != null && data.isValid()) {
                    double precoAtual = data.regularMarketPrice();
                    long posicaoAtualCents = (long)(precoAtual * qtdFinal * 100);
                    double rent = precoMedioFinal > 0
                            ? ((precoAtual - precoMedioFinal) / precoMedioFinal) * 100
                            : 0;

                    posicaoLabel.setText(daily.brl(posicaoAtualCents));
                    posicaoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

                    rentLabel.setText(String.format("%+.2f%%", rent));
                    rentLabel.setStyle(
                            (rent >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                            " -fx-font-weight: bold; -fx-font-size: 12px;");
                } else {
                    // Brapi falhou: fallback para totalValueCents
                    posicaoLabel.setText(daily.brl(totalValueCents));
                    posicaoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                    rentLabel.setText("—");
                    rentLabel.setStyle("-fx-opacity: 0.45; -fx-font-size: 12px;");
                }
            }));
        } else {
            // Sem token: mostrar "—" na rentabilidade
            rentLabel.setText("—");
            rentLabel.setStyle("-fx-opacity: 0.45; -fx-font-size: 12px;");
        }

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
            nodes.add(createInfoBox("Último Preço", String.format("R$ %.2f",
                    BrapiClient.hasToken() ? ultimoPreco : precoMedio)));
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