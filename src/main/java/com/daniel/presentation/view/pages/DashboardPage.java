package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.core.service.DiversificationCalculator;
import com.daniel.core.service.DiversificationCalculator.*;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    public DashboardPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        // Criar eixos para waterfallChart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Investimentos");
        yAxis.setLabel("Valor (R$)");
        waterfallChart = new BarChart<>(xAxis, yAxis);
        waterfallChart.setTitle("Composição do Patrimônio");
        waterfallChart.setLegendVisible(false);

        // Criar eixos para comparisonChart
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

        chartsRow.getChildren().addAll(pieBox, waterfallBox);

        VBox comparisonBox = new VBox(8);
        comparisonBox.getStyleClass().add("card");
        Label comparisonTitle = new Label("Comparação com Mercado");
        comparisonTitle.getStyleClass().add("card-title");
        comparisonBox.getChildren().addAll(comparisonTitle, comparisonChart);

        root.getChildren().addAll(h1, cards, h2, chartsRow, comparisonBox, investmentsByCategoryContainer);

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
        refreshData();
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
                0.135
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

    private void updateComparisonChart(List<InvestmentType> investments,
                                       Map<Long, Long> currentValues,
                                       LocalDate today) {
        comparisonChart.getData().clear();

        // Encontrar data mais antiga
        LocalDate oldestDate = today;
        for (InvestmentType inv : investments) {
            if (inv.investmentDate() != null && inv.investmentDate().isBefore(oldestDate)) {
                oldestDate = inv.investmentDate();
            }
        }

        long monthsRange = java.time.temporal.ChronoUnit.MONTHS.between(oldestDate, today);
        if (monthsRange < 1) monthsRange = 1;
        if (monthsRange > 24) monthsRange = 24; // Limitar a 2 anos

        // Calcular valor total investido
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

        // Série da Carteira
        XYChart.Series<Number, Number> portfolioSeries = new XYChart.Series<>();
        portfolioSeries.setName("Carteira");

        // Série CDI (13.5% a.a.)
        XYChart.Series<Number, Number> cdiSeries = new XYChart.Series<>();
        cdiSeries.setName("CDI (13,5% a.a.)");

        // Série SELIC (11.75% a.a.)
        XYChart.Series<Number, Number> selicSeries = new XYChart.Series<>();
        selicSeries.setName("SELIC (11,75% a.a.)");

        // Série IPCA (4.5% a.a.)
        XYChart.Series<Number, Number> ipcaSeries = new XYChart.Series<>();
        ipcaSeries.setName("IPCA (4,5% a.a.)");

        double cdiRate = 0.135;
        double selicRate = 0.1175;
        double ipcaRate = 0.045;

        double monthlyRateCDI = Math.pow(1 + cdiRate, 1.0/12) - 1;
        double monthlyRateSELIC = Math.pow(1 + selicRate, 1.0/12) - 1;
        double monthlyRateIPCA = Math.pow(1 + ipcaRate, 1.0/12) - 1;

        for (int month = 0; month <= monthsRange; month++) {
            // Rentabilidade CDI
            double rentCDI = (Math.pow(1 + monthlyRateCDI, month) - 1) * 100;
            cdiSeries.getData().add(new XYChart.Data<>(month, rentCDI));

            // Rentabilidade SELIC
            double rentSELIC = (Math.pow(1 + monthlyRateSELIC, month) - 1) * 100;
            selicSeries.getData().add(new XYChart.Data<>(month, rentSELIC));

            // Rentabilidade IPCA
            double rentIPCA = (Math.pow(1 + monthlyRateIPCA, month) - 1) * 100;
            ipcaSeries.getData().add(new XYChart.Data<>(month, rentIPCA));

            // Rentabilidade da Carteira
            long currentTotal = daily.getTotalPatrimony(today);
            double rentPortfolio = ((currentTotal - totalInvested) * 100.0) / totalInvested;
            portfolioSeries.getData().add(new XYChart.Data<>(month, rentPortfolio));
        }

        comparisonChart.getData().addAll(portfolioSeries, cdiSeries, selicSeries, ipcaSeries);

        // Estilizar as linhas
        portfolioSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #22c55e; -fx-stroke-width: 3px;");
            }
        });

        cdiSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #3b82f6; -fx-stroke-width: 2px;");
            }
        });

        selicSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #f59e0b; -fx-stroke-width: 2px;");
            }
        });

        ipcaSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-stroke: #ef4444; -fx-stroke-width: 2px;");
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

        investments.sort((a, b) -> {
            long valA = currentValues.getOrDefault((long)a.id(), 0L);
            long valB = currentValues.getOrDefault((long)b.id(), 0L);
            return Long.compare(valB, valA);
        });

        for (InvestmentType inv : investments) {
            long currentValue = currentValues.getOrDefault((long)inv.id(), 0L);
            if (currentValue == 0) continue;

            HBox invRow = buildInvestmentRow(inv, currentValue, totalPatrimony);
            investmentsList.getChildren().add(invRow);
        }

        section.getChildren().addAll(header, new Separator(), investmentsList);
        return section;
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

            // Valor Investido
            long valorInvestidoCents = (long)(precoMedio * qtdTotal * 100);
            nodes.add(createInfoBox("Valor Investido", daily.brl(valorInvestidoCents)));

            // Posição Atual
            Label posicaoLabel = new Label(daily.brl(currentValueCents));
            posicaoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Posição Atual", posicaoLabel));

            // Rentabilidade
            double rentabilidade = ((ultimoPreco - precoMedio) / precoMedio) * 100;
            Label rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
            rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                    " -fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Rentabilidade", rentLabel));

            // % Alocação
            double alocacao = (currentValueCents * 100.0) / totalPatrimony;
            nodes.add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));

            // Preço Médio
            nodes.add(createInfoBox("Preço Médio", String.format("R$ %.2f", precoMedio)));

            // Último Preço
            nodes.add(createInfoBox("Último Preço", String.format("R$ %.2f", ultimoPreco)));

            // Quantidade
            nodes.add(createInfoBox("Qtd Total", String.valueOf(qtdTotal)));

            // Data Investimento
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

        // Valor Investido
        if (inv.investedValue() != null) {
            long aplicado = inv.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
            nodes.add(createInfoBox("Valor Investido", daily.brl(aplicado)));

            // Posição Atual
            nodes.add(createInfoBox("Posição Atual", daily.brl(currentValueCents)));

            // Rentabilidade
            long lucro = currentValueCents - aplicado;
            double rentabilidade = (lucro * 100.0) / aplicado;
            Label rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
            rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                    " -fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Rentabilidade", rentLabel));
        }

        // % Alocação
        nodes.add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));

        // Taxa
        if (inv.profitability() != null) {
            String taxa = String.format("%.2f%% a.a.", inv.profitability());
            nodes.add(createInfoBox("Taxa", taxa));
        }

        // Data Investimento
        if (inv.investmentDate() != null) {
            nodes.add(createInfoBox("Data Investimento",
                    inv.investmentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }

        return nodes;
    }

    private List<javafx.scene.Node> buildGenericInfo(InvestmentType inv, long currentValueCents, long totalPatrimony) {
        List<javafx.scene.Node> nodes = new ArrayList<>();

        double alocacao = (currentValueCents * 100.0) / totalPatrimony;

        // Valor Investido
        if (inv.investedValue() != null) {
            long investido = inv.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
            nodes.add(createInfoBox("Valor Investido", daily.brl(investido)));

            // Posição Atual
            nodes.add(createInfoBox("Posição Atual", daily.brl(currentValueCents)));

            // Rentabilidade
            long lucro = currentValueCents - investido;
            double rentabilidade = investido > 0 ? (lucro * 100.0) / investido : 0;
            Label rentLabel = new Label(String.format("%+.2f%%", rentabilidade));
            rentLabel.setStyle((rentabilidade >= 0 ? "-fx-text-fill: #22c55e;" : "-fx-text-fill: #ef4444;") +
                    " -fx-font-weight: bold; -fx-font-size: 12px;");
            nodes.add(createInfoBox("Rentabilidade", rentLabel));
        }

        // % Alocação
        nodes.add(createInfoBox("% Alocação", String.format("%.1f%%", alocacao)));

        // Data Investimento
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