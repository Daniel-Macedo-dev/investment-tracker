package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.Enums.InvestmentTypeEnum;
import com.daniel.core.service.InvestmentCalculator;
import com.daniel.core.util.Money;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public final class SimulationPage implements Page {

    private final VBox root = new VBox(16);

    // Inputs comuns
    private final TextField initialValueField = new TextField();
    private final ComboBox<Integer> yearsCombo = new ComboBox<>();

    // Inputs por tipo
    private final TextField fixedRateField = new TextField();
    private final TextField indexRateField = new TextField();
    private final TextField indexPercentageField = new TextField();
    private final TextField inflationField = new TextField();

    // Inputs para ações
    private final TextField tickerField = new TextField();
    private final TextField purchasePriceField = new TextField();
    private final TextField quantityField = new TextField();
    private final TextField currentPriceField = new TextField();
    private final TextField dividendsField = new TextField();
    private final Slider priceVariationSlider = new Slider(-20, 20, 0);

    // Resultado
    private final Label resultLabel = new Label("—");
    private final LineChart<Number, Number> projectionChart;

    private InvestmentTypeEnum currentType = InvestmentTypeEnum.PREFIXADO;

    public SimulationPage() {
        root.setPadding(new Insets(16));

        Label h1 = new Label("Simulação de Investimentos");
        h1.getStyleClass().add("h1");

        // Seletor de tipo
        HBox typeSelector = buildTypeSelector();

        // Inputs
        VBox inputsBox = buildInputsBox();

        // Gráfico
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Anos");
        yAxis.setLabel("Valor (R$)");
        projectionChart = new LineChart<>(xAxis, yAxis);
        projectionChart.setTitle("Projeção de Rentabilidade");
        projectionChart.setMinHeight(400);
        projectionChart.setCreateSymbols(false);

        // Resultado
        VBox resultBox = buildResultBox();

        root.getChildren().addAll(h1, typeSelector, inputsBox, resultBox, projectionChart);
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        // Nada a fazer
    }

    private HBox buildTypeSelector() {
        HBox box = new HBox(10);
        box.getStyleClass().add("card");

        Label label = new Label("Tipo de Investimento:");
        label.setStyle("-fx-font-weight: bold;");

        ToggleGroup group = new ToggleGroup();

        RadioButton prefixadoRadio = new RadioButton("Prefixado");
        RadioButton posfixadoRadio = new RadioButton("Pós-fixado");
        RadioButton hibridoRadio = new RadioButton("Híbrido (Inflação)");
        RadioButton acaoRadio = new RadioButton("Ação");

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

        // Valor inicial
        Label initialLabel = new Label("Valor Inicial:");
        initialLabel.setStyle("-fx-font-weight: bold;");
        initialValueField.setPromptText("R$ 10.000,00");
        initialValueField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(initialValueField);

        // Anos
        Label yearsLabel = new Label("Período (anos):");
        yearsLabel.setStyle("-fx-font-weight: bold;");
        yearsCombo.getItems().addAll(1, 2, 3, 4, 5);
        yearsCombo.setValue(5);

        // Prefixado
        Label fixedLabel = new Label("Taxa Anual (%):");
        fixedLabel.setStyle("-fx-font-weight: bold;");
        fixedRateField.setPromptText("12.50");

        // Pós-fixado
        Label indexRateLabel = new Label("Taxa do Índice (% a.a.):");
        indexRateLabel.setStyle("-fx-font-weight: bold;");
        indexRateField.setPromptText("13.50");

        Label indexPercentLabel = new Label("Percentual do Índice:");
        indexPercentLabel.setStyle("-fx-font-weight: bold;");
        indexPercentageField.setPromptText("1.0 = 100%");

        // Híbrido
        Label inflationLabel = new Label("Taxa de Inflação (% a.a.):");
        inflationLabel.setStyle("-fx-font-weight: bold;");
        inflationField.setPromptText("4.50");

        // Ações
        Label tickerLabel = new Label("Ticker:");
        tickerLabel.setStyle("-fx-font-weight: bold;");
        tickerField.setPromptText("PETR4");

        Label purchasePriceLabel = new Label("Preço de Compra:");
        purchasePriceLabel.setStyle("-fx-font-weight: bold;");
        purchasePriceField.setPromptText("R$ 35,50");

        Label quantityLabel = new Label("Quantidade:");
        quantityLabel.setStyle("-fx-font-weight: bold;");
        quantityField.setPromptText("100");

        Label currentPriceLabel = new Label("Preço Atual:");
        currentPriceLabel.setStyle("-fx-font-weight: bold;");
        currentPriceField.setPromptText("R$ 38,75");

        Label dividendsLabel = new Label("Dividendos Recebidos:");
        dividendsLabel.setStyle("-fx-font-weight: bold;");
        dividendsField.setPromptText("R$ 200,00");

        Label variationLabel = new Label("Variação de Preço (%):");
        variationLabel.setStyle("-fx-font-weight: bold;");
        priceVariationSlider.setShowTickLabels(true);
        priceVariationSlider.setShowTickMarks(true);
        priceVariationSlider.setMajorTickUnit(10);
        priceVariationSlider.setMinorTickCount(5);
        priceVariationSlider.setBlockIncrement(1);
        Label sliderValueLabel = new Label("0%");
        priceVariationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            sliderValueLabel.setText(String.format("%.1f%%", newVal.doubleValue()));
            calculateStock();
        });

        // Botão calcular
        Button calculateBtn = new Button("Calcular Simulação");
        calculateBtn.getStyleClass().add("primary-btn");
        calculateBtn.setOnAction(e -> calculate());

        box.getChildren().addAll(
                initialLabel, initialValueField,
                yearsLabel, yearsCombo,
                fixedLabel, fixedRateField,
                indexRateLabel, indexRateField,
                indexPercentLabel, indexPercentageField,
                inflationLabel, inflationField,
                tickerLabel, tickerField,
                purchasePriceLabel, purchasePriceField,
                quantityLabel, quantityField,
                currentPriceLabel, currentPriceField,
                dividendsLabel, dividendsField,
                variationLabel, new HBox(10, priceVariationSlider, sliderValueLabel),
                calculateBtn
        );

        updateInputsVisibility();
        return box;
    }

    private VBox buildResultBox() {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");

        Label title = new Label("Resultado da Simulação");
        title.getStyleClass().add("card-title");

        resultLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        box.getChildren().addAll(title, resultLabel);
        return box;
    }

    private void updateInputsVisibility() {
        // Esconder todos
        fixedRateField.setVisible(false);
        fixedRateField.setManaged(false);

        indexRateField.setVisible(false);
        indexRateField.setManaged(false);
        indexPercentageField.setVisible(false);
        indexPercentageField.setManaged(false);

        inflationField.setVisible(false);
        inflationField.setManaged(false);

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

        // Mostrar relevantes
        switch (currentType) {
            case PREFIXADO -> {
                fixedRateField.setVisible(true);
                fixedRateField.setManaged(true);
            }
            case POS_FIXADO -> {
                indexRateField.setVisible(true);
                indexRateField.setManaged(true);
                indexPercentageField.setVisible(true);
                indexPercentageField.setManaged(true);
            }
            case HIBRIDO -> {
                fixedRateField.setVisible(true);
                fixedRateField.setManaged(true);
                inflationField.setVisible(true);
                inflationField.setManaged(true);
            }
            case ACAO -> {
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
            }
        }
    }

    private void calculate() {
        try {
            switch (currentType) {
                case PREFIXADO -> calculatePrefixado();
                case POS_FIXADO -> calculatePosfixado();
                case HIBRIDO -> calculateHibrido();
                case ACAO -> calculateStock();
            }
        } catch (Exception e) {
            resultLabel.setText("Erro: " + e.getMessage());
            resultLabel.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    private void calculatePrefixado() {
        double capital = Money.textToCentsOrZero(initialValueField.getText()) / 100.0;
        double rate = Double.parseDouble(fixedRateField.getText().replace(",", ".")) / 100.0;
        int years = yearsCombo.getValue();

        double result = InvestmentCalculator.calculatePrefixado(capital, rate, years);
        double profit = result - capital;

        resultLabel.setText(String.format("Montante: R$ %.2f | Lucro: R$ %.2f", result, profit));
        resultLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 20px; -fx-font-weight: bold;");

        updateChart(capital, rate, years, "Prefixado");
    }

    private void calculatePosfixado() {
        double capital = Money.textToCentsOrZero(initialValueField.getText()) / 100.0;
        double indexRate = Double.parseDouble(indexRateField.getText().replace(",", ".")) / 100.0;
        double percentage = Double.parseDouble(indexPercentageField.getText().replace(",", "."));
        int years = yearsCombo.getValue();

        double result = InvestmentCalculator.calculatePosfixado(capital, percentage, indexRate, years);
        double profit = result - capital;

        resultLabel.setText(String.format("Montante: R$ %.2f | Lucro: R$ %.2f", result, profit));
        resultLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 20px; -fx-font-weight: bold;");

        updateChart(capital, indexRate * percentage, years, "Pós-fixado");
    }

    private void calculateHibrido() {
        double capital = Money.textToCentsOrZero(initialValueField.getText()) / 100.0;
        double fixedRate = Double.parseDouble(fixedRateField.getText().replace(",", ".")) / 100.0;
        double inflation = Double.parseDouble(inflationField.getText().replace(",", ".")) / 100.0;
        int years = yearsCombo.getValue();

        double result = InvestmentCalculator.calculateHibrido(capital, fixedRate, inflation, years);
        double profit = result - capital;

        resultLabel.setText(String.format("Montante: R$ %.2f | Lucro: R$ %.2f", result, profit));
        resultLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 20px; -fx-font-weight: bold;");

        double effectiveRate = (1 + inflation) * (1 + fixedRate) - 1;
        updateChart(capital, effectiveRate, years, "Híbrido");
    }

    private void calculateStock() {
        double purchasePrice = Money.textToCentsOrZero(purchasePriceField.getText()) / 100.0;
        int quantity = Integer.parseInt(quantityField.getText());
        double currentPrice = Money.textToCentsOrZero(currentPriceField.getText()) / 100.0;
        double dividends = Money.textToCentsOrZero(dividendsField.getText()) / 100.0;

        // Aplicar variação do slider
        double variation = priceVariationSlider.getValue() / 100.0;
        double adjustedPrice = currentPrice * (1 + variation);

        var calc = InvestmentCalculator.calculateAcao(purchasePrice, quantity, adjustedPrice, dividends);

        resultLabel.setText(String.format(
                "Investido: R$ %.2f | Atual: R$ %.2f | Lucro Total: R$ %.2f (%.2f%%)",
                calc.valorInvestido(), calc.valorAtual(), calc.lucroTotal(),
                calc.rentabilidade() * 100
        ));

        if (calc.lucroTotal() >= 0) {
            resultLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 18px; -fx-font-weight: bold;");
        } else {
            resultLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 18px; -fx-font-weight: bold;");
        }

        // Gráfico simplificado para ações
        projectionChart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Valor da Ação");
        series.getData().add(new XYChart.Data<>(0, calc.valorInvestido()));
        series.getData().add(new XYChart.Data<>(1, calc.valorAtual() + dividends));
        projectionChart.getData().add(series);
    }

    private void updateChart(double capital, double rate, int years, String title) {
        projectionChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(title);

        for (int year = 0; year <= years; year++) {
            double value = capital * Math.pow(1 + rate, year);
            series.getData().add(new XYChart.Data<>(year, value));
        }

        projectionChart.getData().add(series);
    }
}