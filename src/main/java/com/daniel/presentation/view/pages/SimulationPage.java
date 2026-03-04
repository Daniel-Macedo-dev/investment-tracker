package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.Enums.InvestmentTypeEnum;
import com.daniel.core.service.InvestmentCalculator;
import com.daniel.core.util.Money;
import com.daniel.infrastructure.api.BcbClient;
import com.daniel.infrastructure.api.BrapiClient;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public final class SimulationPage implements Page {

    private final ScrollPane scrollPane = new ScrollPane();
    private final VBox root = new VBox(16);

    private enum RentabilityMode {
        FIXED_RATE("Taxa Fixa (% a.a.)"),
        BENCHMARK_PERCENT("% do Benchmark"),
        HYBRID("Híbrida (Índice + Taxa)");

        private final String display;
        RentabilityMode(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private final TextField initialValueField = new TextField();
    private final ComboBox<Integer> monthsCombo = new ComboBox<>();

    private final ComboBox<RentabilityMode> rentabilityModeCombo = new ComboBox<>();
    private RentabilityMode currentRentabilityMode = RentabilityMode.FIXED_RATE;

    private final TextField fixedRateField = new TextField();
    private final ComboBox<String> benchmarkCombo = new ComboBox<>();
    private final TextField benchmarkPercentField = new TextField();
    private final TextField indexRateField = new TextField();
    private final TextField hybridFixedField = new TextField();

    private final TextField tickerField = new TextField();
    private final TextField purchasePriceField = new TextField();
    private final TextField quantityField = new TextField();
    private final TextField currentPriceField = new TextField();
    private final TextField dividendsField = new TextField();
    private final Slider priceVariationSlider = new Slider(-20, 20, 0);
    private final Label sliderValueLabel = new Label("0%");

    private final Label resultLabel = new Label("—");
    private final LineChart<Number, Number> projectionChart;

    private final Label ratesStatusLabel = new Label();

    private double rateCdi = 0.135;
    private double rateSelic = 0.15;
    private double rateIpca = 0.045;

    private InvestmentTypeEnum currentType = InvestmentTypeEnum.PREFIXADO;

    public SimulationPage() {
        root.setPadding(new Insets(16));

        Label h1 = new Label("Simulação de Investimentos");
        h1.getStyleClass().add("h1");

        HBox typeSelector = buildTypeSelector();
        VBox inputsBox = buildInputsBox();

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Meses");
        yAxis.setLabel("Valor (R$)");
        projectionChart = new LineChart<>(xAxis, yAxis);
        projectionChart.setTitle("Projeção de Rentabilidade");
        projectionChart.setMinHeight(400);
        projectionChart.setCreateSymbols(false);

        VBox resultBox = buildResultBox();

        root.getChildren().addAll(h1, typeSelector, inputsBox, resultBox, projectionChart);

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
        fetchRealRates();
    }

    private void fetchRealRates() {
        ratesStatusLabel.setText("Buscando taxas...");
        applyRatesStatusStyle("text-helper");

        CompletableFuture.supplyAsync(() -> {
            var cdi = BcbClient.fetchCdi();
            var selic = BcbClient.fetchSelic();
            var ipca = BcbClient.fetchIpca();
            return new double[]{
                    cdi.orElse(-1.0), selic.orElse(-1.0), ipca.orElse(-1.0)
            };
        }).thenAcceptAsync(rates -> Platform.runLater(() -> {
            boolean anyFailed = false;

            if (rates[0] > 0) { rateCdi = rates[0]; } else { anyFailed = true; }
            if (rates[1] > 0) { rateSelic = rates[1]; } else { anyFailed = true; }
            if (rates[2] > 0) { rateIpca = rates[2]; } else { anyFailed = true; }

            if (anyFailed) {
                ratesStatusLabel.setText("Algumas taxas usando valor estimado");
                applyRatesStatusStyle("text-xs", "state-warning");
            } else {
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                ratesStatusLabel.setText(String.format(
                        "Taxas atualizadas em %s — CDI: %.2f%% | SELIC: %.2f%% | IPCA: %.2f%%",
                        time, rateCdi * 100, rateSelic * 100, rateIpca * 100));
                applyRatesStatusStyle("text-xs", "state-positive");
            }
        }));
    }

    private HBox buildTypeSelector() {
        HBox box = new HBox(10);
        box.getStyleClass().add("card");

        Label label = new Label("Tipo de Investimento:");
        label.getStyleClass().add("form-label");

        ToggleGroup group = new ToggleGroup();

        RadioButton prefixadoRadio = new RadioButton("Prefixado");
        RadioButton posfixadoRadio = new RadioButton("Pós-fixado");
        RadioButton hibridoRadio = new RadioButton("Híbrido");
        RadioButton acaoRadio = new RadioButton("Ação/FII");

        prefixadoRadio.setToggleGroup(group);
        posfixadoRadio.setToggleGroup(group);
        hibridoRadio.setToggleGroup(group);
        acaoRadio.setToggleGroup(group);

        prefixadoRadio.setSelected(true);

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == prefixadoRadio) currentType = InvestmentTypeEnum.PREFIXADO;
            else if (newVal == posfixadoRadio) currentType = InvestmentTypeEnum.POS_FIXADO;
            else if (newVal == hibridoRadio) currentType = InvestmentTypeEnum.HIBRIDO;
            else if (newVal == acaoRadio) currentType = InvestmentTypeEnum.ACAO;

            updateInputsVisibility();
        });

        box.getChildren().addAll(label, prefixadoRadio, posfixadoRadio, hibridoRadio, acaoRadio);
        return box;
    }

    private VBox buildInputsBox() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");

        Label initialLabel = new Label("Valor Inicial:");
        initialLabel.getStyleClass().add("form-label");
        initialValueField.setPromptText("R$ 10.000,00");
        initialValueField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(initialValueField);

        Label monthsLabel = new Label("Período (meses):");
        monthsLabel.getStyleClass().add("form-label");
        monthsCombo.getItems().addAll(1, 3, 6, 12, 24, 36, 48, 60, 120);
        monthsCombo.setValue(12);

        Label modeLabel = new Label("Modalidade de Rentabilidade:");
        modeLabel.getStyleClass().add("form-label");
        rentabilityModeCombo.getItems().addAll(RentabilityMode.values());
        rentabilityModeCombo.setValue(RentabilityMode.FIXED_RATE);
        rentabilityModeCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(RentabilityMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplay());
            }
        });
        rentabilityModeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(RentabilityMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getDisplay());
            }
        });

        rentabilityModeCombo.valueProperty().addListener((obs, old, newVal) -> {
            currentRentabilityMode = newVal;
            updateRentabilityInputs();
        });

        Label fixedLabel = new Label("Taxa Anual (%):");
        fixedLabel.getStyleClass().add("form-label");
        fixedRateField.setPromptText("12.50");

        Label benchmarkLabel = new Label("Benchmark:");
        benchmarkLabel.getStyleClass().add("form-label");
        benchmarkCombo.getItems().addAll("CDI", "SELIC", "IPCA");
        benchmarkCombo.setValue("CDI");

        Label benchmarkPercentLabel = new Label("Percentual do Benchmark:");
        benchmarkPercentLabel.getStyleClass().add("form-label");
        benchmarkPercentField.setPromptText("110 (= 110% do CDI)");

        Label hybridLabel = new Label("Taxa Fixa (Híbrido):");
        hybridLabel.getStyleClass().add("form-label");
        hybridFixedField.setPromptText("5.0");

        Label indexLabel = new Label("Taxa do Índice (% a.a.):");
        indexLabel.getStyleClass().add("form-label");
        indexRateField.setPromptText("4.5 (IPCA estimado)");

        Label tickerLabel = new Label("Ticker:");
        tickerLabel.getStyleClass().add("form-label");
        tickerField.setPromptText("PETR4, VALE3, HGLG11...");

        tickerField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.length() >= 4) {
                loadStockDataFromBrapi(newVal);
            }
        });

        Label purchasePriceLabel = new Label("Preço de Compra:");
        purchasePriceLabel.getStyleClass().add("form-label");
        purchasePriceField.setPromptText("R$ 35,50");
        purchasePriceField.setTextFormatter(Money.currencyFormatterEditable());

        Label quantityLabel = new Label("Quantidade:");
        quantityLabel.getStyleClass().add("form-label");
        quantityField.setPromptText("100");

        Label currentPriceLabel = new Label("Preço Atual (automático):");
        currentPriceLabel.getStyleClass().add("form-label");
        currentPriceField.setPromptText("Será preenchido automaticamente");
        currentPriceField.setTextFormatter(Money.currencyFormatterEditable());
        currentPriceField.setDisable(true);

        Label dividendsLabel = new Label("Dividendos Estimados (automático):");
        dividendsLabel.getStyleClass().add("form-label");
        dividendsField.setPromptText("Será calculado automaticamente");
        dividendsField.setTextFormatter(Money.currencyFormatterEditable());
        dividendsField.setDisable(true);

        Label variationLabel = new Label("Variação de Preço (%):");
        variationLabel.getStyleClass().add("form-label");
        priceVariationSlider.setShowTickLabels(true);
        priceVariationSlider.setShowTickMarks(true);
        priceVariationSlider.setMajorTickUnit(10);
        priceVariationSlider.setMinorTickCount(5);
        priceVariationSlider.setBlockIncrement(1);
        priceVariationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            sliderValueLabel.setText(String.format("%.1f%%", newVal.doubleValue()));
            if (currentType == InvestmentTypeEnum.ACAO) {
                calculateStock();
            }
        });

        Button calculateBtn = new Button("Calcular Simulação");
        calculateBtn.getStyleClass().add("primary-btn");
        calculateBtn.setOnAction(e -> calculate());

        box.getChildren().addAll(
                initialLabel, initialValueField,
                monthsLabel, monthsCombo,
                modeLabel, rentabilityModeCombo,
                fixedLabel, fixedRateField,
                benchmarkLabel, benchmarkCombo, ratesStatusLabel,
                benchmarkPercentLabel, benchmarkPercentField,
                hybridLabel, hybridFixedField,
                indexLabel, indexRateField,
                tickerLabel, tickerField,
                purchasePriceLabel, purchasePriceField,
                quantityLabel, quantityField,
                currentPriceLabel, currentPriceField,
                dividendsLabel, dividendsField,
                variationLabel, new HBox(10, priceVariationSlider, sliderValueLabel),
                calculateBtn
        );

        updateInputsVisibility();
        updateRentabilityInputs();
        return box;
    }

    private VBox buildResultBox() {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");

        Label title = new Label("Resultado da Simulação");
        title.getStyleClass().add("card-title");

        resultLabel.getStyleClass().add("simulation-result");

        box.getChildren().addAll(title, resultLabel);
        return box;
    }

    private void updateInputsVisibility() {
        fixedRateField.setVisible(false);
        fixedRateField.setManaged(false);
        benchmarkCombo.setVisible(false);
        benchmarkCombo.setManaged(false);
        benchmarkPercentField.setVisible(false);
        benchmarkPercentField.setManaged(false);
        hybridFixedField.setVisible(false);
        hybridFixedField.setManaged(false);
        indexRateField.setVisible(false);
        indexRateField.setManaged(false);
        tickerField.setVisible(false);
        tickerField.setManaged(false);
        purchasePriceField.setVisible(false);
        purchasePriceField.setManaged(false);
        quantityField.setVisible(false);
        quantityField.setManaged(false);
        currentPriceField.setVisible(false);
        currentPriceField.setManaged(false);
        dividendsField.setVisible(false);
        dividendsField.setManaged(false);
        priceVariationSlider.setVisible(false);
        priceVariationSlider.setManaged(false);
        rentabilityModeCombo.setVisible(false);
        rentabilityModeCombo.setManaged(false);

        if (currentType == InvestmentTypeEnum.ACAO) {
            tickerField.setVisible(true);
            tickerField.setManaged(true);
            purchasePriceField.setVisible(true);
            purchasePriceField.setManaged(true);
            quantityField.setVisible(true);
            quantityField.setManaged(true);
            currentPriceField.setVisible(true);
            currentPriceField.setManaged(true);
            dividendsField.setVisible(true);
            dividendsField.setManaged(true);
            priceVariationSlider.setVisible(true);
            priceVariationSlider.setManaged(true);
        } else {
            rentabilityModeCombo.setVisible(true);
            rentabilityModeCombo.setManaged(true);
            updateRentabilityInputs();
        }
    }

    private void updateRentabilityInputs() {
        if (currentType == InvestmentTypeEnum.ACAO) return;

        fixedRateField.setVisible(false);
        fixedRateField.setManaged(false);
        benchmarkCombo.setVisible(false);
        benchmarkCombo.setManaged(false);
        benchmarkPercentField.setVisible(false);
        benchmarkPercentField.setManaged(false);
        hybridFixedField.setVisible(false);
        hybridFixedField.setManaged(false);
        indexRateField.setVisible(false);
        indexRateField.setManaged(false);

        switch (currentRentabilityMode) {
            case FIXED_RATE -> {
                fixedRateField.setVisible(true);
                fixedRateField.setManaged(true);
            }
            case BENCHMARK_PERCENT -> {
                benchmarkCombo.setVisible(true);
                benchmarkCombo.setManaged(true);
                benchmarkPercentField.setVisible(true);
                benchmarkPercentField.setManaged(true);
            }
            case HYBRID -> {
                hybridFixedField.setVisible(true);
                hybridFixedField.setManaged(true);
                indexRateField.setVisible(true);
                indexRateField.setManaged(true);
            }
        }
    }

    private void loadStockDataFromBrapi(String ticker) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return BrapiClient.fetchStockData(ticker);
            } catch (Exception e) {
                return null;
            }
        }).thenAcceptAsync(data -> {
            if (data != null && data.isValid()) {
                Platform.runLater(() -> {
                    currentPriceField.setText(Money.centsToText((long)(data.regularMarketPrice() * 100)));

                    double estimatedAnnualDividend = data.regularMarketPrice() * (data.dividendYield() / 100.0);
                    dividendsField.setText(Money.centsToText((long)(estimatedAnnualDividend * 100)));
                });
            }
        });
    }

    private void calculate() {
        try {
            if (currentType == InvestmentTypeEnum.ACAO) {
                calculateStock();
            } else {
                calculateFixedIncome();
            }
        } catch (Exception e) {
            resultLabel.setText("Erro: " + e.getMessage());
            applyResultStyle(false);
        }
    }

    private void calculateFixedIncome() {
        try {
            double capital = Money.textToCentsOrZero(initialValueField.getText()) / 100.0;
            int months = monthsCombo.getValue();

            if (capital == 0 || months == 0) {
                resultLabel.setText("Preencha valor inicial e período");
                return;
            }

            double annualRate = getAnnualRate();
            double monthlyRate = Math.pow(1 + annualRate, 1.0/12) - 1;

            double result = capital * Math.pow(1 + monthlyRate, months);
            double profit = result - capital;

            resultLabel.setText(String.format(
                    "Montante: R$ %.2f | Lucro: R$ %.2f (%.1f%%)",
                    result, profit, (profit / capital) * 100
            ));
            applyResultStyle(true);

            updateChartMonths(capital, monthlyRate, months, "Renda Fixa");

        } catch (Exception e) {
            resultLabel.setText("Erro: Verifique os campos");
            applyResultStyle(false);
        }
    }

    private double getAnnualRate() {
        switch (currentRentabilityMode) {
            case FIXED_RATE -> {
                return Double.parseDouble(fixedRateField.getText().replace(",", ".")) / 100.0;
            }
            case BENCHMARK_PERCENT -> {
                double benchmarkRate = getBenchmarkRate(benchmarkCombo.getValue());
                double percent = Double.parseDouble(benchmarkPercentField.getText().replace(",", ".")) / 100.0;
                return benchmarkRate * percent;
            }
            case HYBRID -> {
                double fixedPart = Double.parseDouble(hybridFixedField.getText().replace(",", ".")) / 100.0;
                double indexPart = Double.parseDouble(indexRateField.getText().replace(",", ".")) / 100.0;
                return (1 + indexPart) * (1 + fixedPart) - 1;
            }
        }
        return 0;
    }

    private double getBenchmarkRate(String benchmark) {
        return switch (benchmark) {
            case "CDI" -> rateCdi;
            case "SELIC" -> rateSelic;
            case "IPCA" -> rateIpca;
            default -> 0.10;
        };
    }

    private void calculateStock() {
        try {
            double purchasePrice = Money.textToCentsOrZero(purchasePriceField.getText()) / 100.0;
            String qtyText = quantityField.getText();

            if (qtyText == null || qtyText.isBlank()) {
                resultLabel.setText("Preencha a quantidade");
                applyResultStyle(false);
                return;
            }

            int quantity = Integer.parseInt(qtyText);
            double currentPrice = Money.textToCentsOrZero(currentPriceField.getText()) / 100.0;
            double dividends = Money.textToCentsOrZero(dividendsField.getText()) / 100.0;

            if (purchasePrice == 0 || quantity == 0) {
                resultLabel.setText("Preencha preço de compra e quantidade");
                applyResultStyle(false);
                return;
            }

            if (currentPrice == 0) {
                currentPrice = purchasePrice;
            }

            double variation = priceVariationSlider.getValue() / 100.0;
            double adjustedPrice = currentPrice * (1 + variation);

            double valorInvestido = purchasePrice * quantity;
            double valorAtual = adjustedPrice * quantity;
            double lucro = valorAtual - valorInvestido;
            double rentabilidade = (lucro / valorInvestido) * 100;
            double lucroTotal = (valorAtual + dividends) - valorInvestido;

            resultLabel.setText(String.format(
                    "Investido: R$ %.2f | Atual: R$ %.2f | Lucro: R$ %.2f (%.2f%%)",
                    valorInvestido, valorAtual, lucroTotal, rentabilidade
            ));

            applyResultStyle(lucroTotal >= 0);

            projectionChart.getData().clear();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Ação");
            series.getData().add(new XYChart.Data<>(0, valorInvestido));
            series.getData().add(new XYChart.Data<>(1, valorAtual + dividends));
            projectionChart.getData().add(series);

        } catch (NumberFormatException e) {
            resultLabel.setText("Erro: Quantidade deve ser um número");
            applyResultStyle(false);
        } catch (Exception e) {
            resultLabel.setText("Erro: Preencha todos os campos corretamente");
            applyResultStyle(false);
        }
    }

    private void updateChartMonths(double capital, double monthlyRate, int months, String title) {
        projectionChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(title);

        for (int month = 0; month <= months; month++) {
            double value = capital * Math.pow(1 + monthlyRate, month);
            series.getData().add(new XYChart.Data<>(month, value));
        }

        projectionChart.getData().add(series);
    }

    private void applyResultStyle(boolean positive) {
        resultLabel.getStyleClass().removeAll("pos", "neg");
        resultLabel.getStyleClass().add(positive ? "pos" : "neg");
    }

    private void applyRatesStatusStyle(String... classes) {
        ratesStatusLabel.getStyleClass().removeAll("text-helper", "text-xs", "state-warning", "state-positive");
        ratesStatusLabel.getStyleClass().addAll(java.util.List.of(classes));
    }
}