package com.daniel.presentation.view.components;

import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.domain.entity.Enums.LiquidityEnum;
import com.daniel.core.domain.entity.Enums.InvestmentTypeEnum;
import com.daniel.core.domain.entity.Enums.IndexTypeEnum;
import com.daniel.core.util.Money;
import com.daniel.infrastructure.api.BrapiClient;
import com.daniel.presentation.view.components.TickerAutocompleteField;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

public final class InvestmentTypeDialog extends Dialog<InvestmentTypeDialog.InvestmentTypeData> {

    private enum RentabilityMode {
        FIXED_RATE("Taxa Fixa (% a.a.)"),
        BENCHMARK_PERCENT("% do Benchmark"),
        HYBRID("Híbrida (Índice + Taxa)");

        private final String display;
        RentabilityMode(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private final TextField nameField = new TextField();
    private final ComboBox<CategoryEnum> categoryCombo = new ComboBox<>();
    private final ComboBox<LiquidityEnum> liquidityCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();

    private final ComboBox<RentabilityMode> rentabilityModeCombo = new ComboBox<>();
    private RentabilityMode currentRentabilityMode = RentabilityMode.FIXED_RATE;

    private final TextField profitabilityField = new TextField();
    private final ComboBox<String> benchmarkCombo = new ComboBox<>();
    private final TextField benchmarkPercentField = new TextField();
    private final TextField hybridFixedField = new TextField();
    private final TextField hybridIndexField = new TextField();

    private final TextField investedValueField = new TextField();

    private final ComboBox<InvestmentTypeEnum> typeCombo = new ComboBox<>();
    private final ComboBox<IndexTypeEnum> indexCombo = new ComboBox<>();
    private final TextField indexPercentageField = new TextField();

    private final TickerAutocompleteField tickerField = new TickerAutocompleteField();
    private final TextField purchasePriceField = new TextField();
    private final TextField quantityField = new TextField();

    // ✅ ADICIONAR: Timer para debounce
    private Timer debounceTimer;

    private final boolean isEdit;

    public InvestmentTypeDialog(String title, InvestmentTypeData existing) {
        this.isEdit = existing != null;

        setTitle(title);
        setHeaderText(isEdit ? "Edite os dados do investimento" : "Preencha os dados do novo investimento");

        ButtonType confirmButton = new ButtonType(isEdit ? "Atualizar" : "Criar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(confirmButton, ButtonType.CANCEL);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab tab1 = new Tab("Dados Básicos", buildBasicTab());
        Tab tab2 = new Tab("Tipo & Rentabilidade", buildTypeTab());

        tabPane.getTabs().addAll(tab1, tab2);

        getDialogPane().setContent(tabPane);
        getDialogPane().setMinWidth(700);
        getDialogPane().setMinHeight(900);

        if (existing != null) {
            fillExistingData(existing);
        } else {
            datePicker.setValue(LocalDate.now());
        }

        typeCombo.valueProperty().addListener((o, a, b) -> {
            updateTypeVisibility();
        });

        categoryCombo.valueProperty().addListener((o, a, b) -> updateRentabilityVisibility());

        // ✅ SUBSTITUIR: Listeners com debounce para auto-preenchimento
        purchasePriceField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> updateInvestedValueForStock());
                }
            }, 500);
        });

        quantityField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> updateInvestedValueForStock());
                }
            }, 500);
        });

        // Também calcular ao pressionar Enter
        purchasePriceField.setOnAction(e -> updateInvestedValueForStock());
        quantityField.setOnAction(e -> updateInvestedValueForStock());

        tickerField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.length() >= 4) {
                loadStockDataFromBrapi(newVal);
            }
        });

        rentabilityModeCombo.valueProperty().addListener((obs, old, newVal) -> {
            currentRentabilityMode = newVal;
            updateRentabilityModeInputs();
        });

        setResultConverter(buttonType -> {
            if (buttonType == confirmButton) {
                return buildResult();
            }
            return null;
        });

        Button btn = (Button) getDialogPane().lookupButton(confirmButton);
        btn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate()) {
                event.consume();
            }
        });

        updateTypeVisibility();
        updateRentabilityVisibility();
    }

    private VBox buildBasicTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        Label nameLabel = new Label("Nome do Investimento *");
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameField.setPromptText("Ex: Tesouro Selic 2027, Ações PETR4...");

        Label catLabel = new Label("Categoria *");
        catLabel.setStyle("-fx-font-weight: bold;");
        categoryCombo.getItems().addAll(CategoryEnum.values());
        categoryCombo.setPromptText("Selecione a categoria");
        categoryCombo.setCellFactory(lv -> new CategoryCell());
        categoryCombo.setButtonCell(new CategoryCell());

        Label liqLabel = new Label("Liquidez *");
        liqLabel.setStyle("-fx-font-weight: bold;");
        liquidityCombo.getItems().addAll(LiquidityEnum.values());
        liquidityCombo.setPromptText("Selecione a liquidez");
        liquidityCombo.setCellFactory(lv -> new LiquidityCell());
        liquidityCombo.setButtonCell(new LiquidityCell());

        Label dateLabel = new Label("Data do Investimento *");
        dateLabel.setStyle("-fx-font-weight: bold;");
        datePicker.setPromptText("Selecione a data");

        box.getChildren().addAll(
                nameLabel, nameField,
                catLabel, categoryCombo,
                liqLabel, liquidityCombo,
                dateLabel, datePicker
        );

        return box;
    }

    private VBox buildTypeTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        Label typeLabel = new Label("Tipo de Investimento");
        typeLabel.setStyle("-fx-font-weight: bold;");
        typeCombo.getItems().addAll(InvestmentTypeEnum.values());
        typeCombo.setPromptText("Opcional");

        Label indexLabel = new Label("Índice");
        indexLabel.setStyle("-fx-font-weight: bold;");
        indexCombo.getItems().addAll(IndexTypeEnum.values());
        indexCombo.setPromptText("CDI, Selic, IPCA");

        Label indexPercentLabel = new Label("Percentual do Índice");
        indexPercentLabel.setStyle("-fx-font-weight: bold;");
        indexPercentageField.setPromptText("1.0 = 100%, 1.05 = 105%");

        Label modeLabel = new Label("Modalidade de Rentabilidade:");
        modeLabel.setStyle("-fx-font-weight: bold;");
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

        Label profitLabel = new Label("Rentabilidade Anual (%)");
        profitLabel.setStyle("-fx-font-weight: bold;");
        profitabilityField.setPromptText("Ex: 13.75");
        profitabilityField.setTextFormatter(createDecimalFormatter());

        Label profitHint = new Label("💡 Opcional para Ações, Fundos Imobiliários e Fundos de Investimento");
        profitHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        Label benchmarkLabel = new Label("Benchmark:");
        benchmarkLabel.setStyle("-fx-font-weight: bold;");
        benchmarkCombo.getItems().addAll("CDI", "SELIC", "IPCA");
        benchmarkCombo.setValue("CDI");

        Label benchmarkPercentLabel = new Label("Percentual do Benchmark:");
        benchmarkPercentLabel.setStyle("-fx-font-weight: bold;");
        benchmarkPercentField.setPromptText("110 (= 110% do CDI)");

        Label hybridFixedLabel = new Label("Taxa Fixa (Híbrido):");
        hybridFixedLabel.setStyle("-fx-font-weight: bold;");
        hybridFixedField.setPromptText("5.0");

        Label hybridIndexLabel = new Label("Taxa do Índice:");
        hybridIndexLabel.setStyle("-fx-font-weight: bold;");
        hybridIndexField.setPromptText("4.5 (IPCA)");

        Label tickerLabel = new Label("Ticker (para ações/FIIs)");
        tickerLabel.setStyle("-fx-font-weight: bold;");

        Label purchaseLabel = new Label("Preço de Compra");
        purchaseLabel.setStyle("-fx-font-weight: bold;");
        purchasePriceField.setPromptText("R$ 35,50");
        purchasePriceField.setTextFormatter(Money.currencyFormatterEditable());

        Label qtyLabel = new Label("Quantidade");
        qtyLabel.setStyle("-fx-font-weight: bold;");
        quantityField.setPromptText("100");

        Label valueLabel = new Label("Valor Investido *");
        valueLabel.setStyle("-fx-font-weight: bold;");
        investedValueField.setPromptText("R$ 0,00");
        //investedValueField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(investedValueField);

        Label valueHint = new Label("💡 Para ações/FIIs, preenchido automaticamente (Preço × Quantidade)");
        valueHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        box.getChildren().addAll(
                typeLabel, typeCombo,
                indexLabel, indexCombo,
                indexPercentLabel, indexPercentageField,
                modeLabel, rentabilityModeCombo,
                profitLabel, profitabilityField, profitHint,
                benchmarkLabel, benchmarkCombo,
                benchmarkPercentLabel, benchmarkPercentField,
                hybridFixedLabel, hybridFixedField,
                hybridIndexLabel, hybridIndexField,
                tickerLabel, tickerField,
                purchaseLabel, purchasePriceField,
                qtyLabel, quantityField,
                valueLabel, investedValueField, valueHint
        );

        return box;
    }

    private void updateTypeVisibility() {
        InvestmentTypeEnum type = typeCombo.getValue();

        indexCombo.setVisible(false);
        indexCombo.setManaged(false);
        indexPercentageField.setVisible(false);
        indexPercentageField.setManaged(false);
        tickerField.setVisible(false);
        tickerField.setManaged(false);
        purchasePriceField.setVisible(false);
        purchasePriceField.setManaged(false);
        quantityField.setVisible(false);
        quantityField.setManaged(false);

        if (type == null) return;

        switch (type) {
            case POS_FIXADO, HIBRIDO -> {
                indexCombo.setVisible(true);
                indexCombo.setManaged(true);
                indexPercentageField.setVisible(true);
                indexPercentageField.setManaged(true);
            }
            case ACAO -> {
                tickerField.setVisible(true);
                tickerField.setManaged(true);
                purchasePriceField.setVisible(true);
                purchasePriceField.setManaged(true);
                quantityField.setVisible(true);
                quantityField.setManaged(true);
            }
        }
    }

    private void updateRentabilityVisibility() {
        CategoryEnum cat = categoryCombo.getValue();
        if (cat == null) return;

        boolean isVariableIncome = cat == CategoryEnum.ACOES ||
                cat == CategoryEnum.FUNDOS_IMOBILIARIOS ||
                cat == CategoryEnum.FUNDOS;

        rentabilityModeCombo.setVisible(!isVariableIncome);
        rentabilityModeCombo.setManaged(!isVariableIncome);

        if (isVariableIncome) {
            profitabilityField.setVisible(false);
            profitabilityField.setManaged(false);
            benchmarkCombo.setVisible(false);
            benchmarkCombo.setManaged(false);
            benchmarkPercentField.setVisible(false);
            benchmarkPercentField.setManaged(false);
            hybridFixedField.setVisible(false);
            hybridFixedField.setManaged(false);
            hybridIndexField.setVisible(false);
            hybridIndexField.setManaged(false);
        } else {
            updateRentabilityModeInputs();
        }
    }

    private void updateRentabilityModeInputs() {
        profitabilityField.setVisible(false);
        profitabilityField.setManaged(false);
        benchmarkCombo.setVisible(false);
        benchmarkCombo.setManaged(false);
        benchmarkPercentField.setVisible(false);
        benchmarkPercentField.setManaged(false);
        hybridFixedField.setVisible(false);
        hybridFixedField.setManaged(false);
        hybridIndexField.setVisible(false);
        hybridIndexField.setManaged(false);

        if (currentRentabilityMode == null) return;

        switch (currentRentabilityMode) {
            case FIXED_RATE -> {
                profitabilityField.setVisible(true);
                profitabilityField.setManaged(true);
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
                hybridIndexField.setVisible(true);
                hybridIndexField.setManaged(true);
            }
        }
    }

    private void updateInvestedValueForStock() {
        String priceText = purchasePriceField.getText();
        String qtyText = quantityField.getText();

        if (priceText == null || priceText.isBlank() || priceText.equals("R$ 0,00")) {
            return;
        }

        if (qtyText == null || qtyText.isBlank() || qtyText.equals("0")) {
            return;
        }

        long priceCents = Money.textToCentsOrZero(priceText);
        if (priceCents == 0) {
            return;
        }

        String cleanQty = qtyText.trim().replaceAll("[^0-9]", "");
        if (cleanQty.isEmpty()) {
            return;
        }

        int quantity = Integer.parseInt(cleanQty);
        if (quantity == 0) {
            return;
        }

        long totalCents = priceCents * quantity;
        double totalValue = totalCents / 100.0;
        String formatted = String.format("%.2f", totalValue).replace('.', ',');

        investedValueField.clear();
        Platform.runLater(() -> {
            investedValueField.setText(formatted);
            investedValueField.positionCaret(formatted.length());
        });
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
                    if (purchasePriceField.getText().isBlank()) {
                        purchasePriceField.setText(Money.centsToText((long)(data.regularMarketPrice() * 100)));
                    }
                });
            }
        });
    }

    private void fillExistingData(InvestmentTypeData data) {
        nameField.setText(data.name());

        if (data.category() != null) {
            try {
                categoryCombo.setValue(CategoryEnum.valueOf(data.category()));
            } catch (Exception ignored) {}
        }

        if (data.liquidity() != null) {
            try {
                liquidityCombo.setValue(LiquidityEnum.valueOf(data.liquidity()));
            } catch (Exception ignored) {}
        }

        if (data.investmentDate() != null) {
            datePicker.setValue(data.investmentDate());
        }

        if (data.profitability() != null) {
            profitabilityField.setText(data.profitability().toString());
        }

        if (data.investedValue() != null) {
            long cents = data.investedValue().multiply(BigDecimal.valueOf(100)).longValue();
            investedValueField.setText(Money.centsToText(cents));
        }

        if (data.typeOfInvestment() != null) {
            try {
                typeCombo.setValue(InvestmentTypeEnum.valueOf(data.typeOfInvestment()));
            } catch (Exception ignored) {}
        }

        if (data.indexType() != null) {
            try {
                indexCombo.setValue(IndexTypeEnum.valueOf(data.indexType()));
            } catch (Exception ignored) {}
        }

        if (data.indexPercentage() != null) {
            indexPercentageField.setText(data.indexPercentage().toString());
        }

        if (data.ticker() != null) {
            tickerField.setText(data.ticker());
        }

        if (data.purchasePrice() != null) {
            long cents = data.purchasePrice().multiply(BigDecimal.valueOf(100)).longValue();
            purchasePriceField.setText(Money.centsToText(cents));
        }

        if (data.quantity() != null) {
            quantityField.setText(data.quantity().toString());
        }
    }

    private boolean validate() {
        StringBuilder errors = new StringBuilder();

        if (nameField.getText().isBlank()) {
            errors.append("• Nome é obrigatório\n");
        }

        if (categoryCombo.getValue() == null) {
            errors.append("• Categoria é obrigatória\n");
        }

        if (liquidityCombo.getValue() == null) {
            errors.append("• Liquidez é obrigatória\n");
        }

        if (datePicker.getValue() == null) {
            errors.append("• Data é obrigatória\n");
        }

        CategoryEnum selectedCategory = categoryCombo.getValue();
        if (selectedCategory != null) {
            boolean isVariableIncome = selectedCategory == CategoryEnum.ACOES ||
                    selectedCategory == CategoryEnum.FUNDOS_IMOBILIARIOS ||
                    selectedCategory == CategoryEnum.FUNDOS;

            if (!isVariableIncome && profitabilityField.getText().isBlank() &&
                    currentRentabilityMode == RentabilityMode.FIXED_RATE) {
                errors.append("• Rentabilidade é obrigatória para " + selectedCategory.getDisplayName() + "\n");
            }
        }

        if (investedValueField.getText().isBlank()) {
            errors.append("• Valor é obrigatório\n");
        }

        if (errors.length() > 0) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validação");
            alert.setHeaderText("Corrija os erros:");
            alert.setContentText(errors.toString());
            alert.showAndWait();
            return false;
        }

        return true;
    }

    private InvestmentTypeData buildResult() {
        String name = nameField.getText().trim();
        String category = categoryCombo.getValue().name();
        String liquidity = liquidityCombo.getValue().name();
        LocalDate date = datePicker.getValue();

        BigDecimal profitability = null;
        if (!profitabilityField.getText().isBlank()) {
            profitability = new BigDecimal(profitabilityField.getText().replace(",", "."));
        }

        long cents = Money.textToCentsOrZero(investedValueField.getText());
        BigDecimal investedValue = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));

        String typeOfInv = typeCombo.getValue() != null ? typeCombo.getValue().name() : null;
        String indexType = indexCombo.getValue() != null ? indexCombo.getValue().name() : null;

        BigDecimal indexPerc = null;
        if (!indexPercentageField.getText().isBlank()) {
            indexPerc = new BigDecimal(indexPercentageField.getText().replace(",", "."));
        }

        String ticker = tickerField.getText().isBlank() ? null : tickerField.getText().trim().toUpperCase();

        BigDecimal purchasePrice = null;
        if (!purchasePriceField.getText().isBlank()) {
            long priceCents = Money.textToCentsOrZero(purchasePriceField.getText());
            purchasePrice = BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100));
        }

        Integer quantity = null;
        if (!quantityField.getText().isBlank()) {
            String cleanQty = quantityField.getText().trim().replaceAll("[^0-9]", "");
            if (!cleanQty.isEmpty()) {
                quantity = Integer.parseInt(cleanQty);
            }
        }

        return new InvestmentTypeData(
                name, category, liquidity, date, profitability, investedValue,
                typeOfInv, indexType, indexPerc, ticker, purchasePrice, quantity
        );
    }

    private TextFormatter<String> createDecimalFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            if (text.matches("\\d*[.,]?\\d{0,2}")) {
                return change;
            }
            return null;
        });
    }

    private static class CategoryCell extends ListCell<CategoryEnum> {
        @Override
        protected void updateItem(CategoryEnum item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox box = new HBox(8);
                box.setStyle("-fx-alignment: center-left;");

                Circle circle = new Circle(5);
                circle.setFill(Color.web(item.getColor()));

                Label label = new Label(item.getDisplayName());

                box.getChildren().addAll(circle, label);
                setGraphic(box);
                setText(null);
            }
        }
    }

    private static class LiquidityCell extends ListCell<LiquidityEnum> {
        @Override
        protected void updateItem(LiquidityEnum item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox box = new HBox(8);
                box.setStyle("-fx-alignment: center-left;");

                Circle circle = new Circle(5);
                circle.setFill(Color.web(item.getColor()));

                Label label = new Label(item.getDisplayName());

                box.getChildren().addAll(circle, label);
                setGraphic(box);
                setText(null);
            }
        }
    }

    public record InvestmentTypeData(
            String name,
            String category,
            String liquidity,
            LocalDate investmentDate,
            BigDecimal profitability,
            BigDecimal investedValue,
            String typeOfInvestment,
            String indexType,
            BigDecimal indexPercentage,
            String ticker,
            BigDecimal purchasePrice,
            Integer quantity
    ) {}
}