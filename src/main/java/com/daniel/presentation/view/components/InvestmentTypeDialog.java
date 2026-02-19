package com.daniel.presentation.view.components;

import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.domain.entity.Enums.LiquidityEnum;
import com.daniel.core.util.Money;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class InvestmentTypeDialog extends Dialog<InvestmentTypeDialog.InvestmentTypeData> {

    private final TextField nameField = new TextField();
    private final ComboBox<CategoryEnum> categoryCombo = new ComboBox<>();
    private final ComboBox<LiquidityEnum> liquidityCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();
    private final TextField profitabilityField = new TextField();
    private final TextField investedValueField = new TextField();

    private final Label previewLabel = new Label();

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
        Tab tab2 = new Tab("Rentabilidade & Valor", buildFinancialTab());
        Tab tab3 = new Tab("Preview", buildPreviewTab());

        tabPane.getTabs().addAll(tab1, tab2, tab3);

        getDialogPane().setContent(tabPane);
        getDialogPane().setMinWidth(600);

        if (existing != null) {
            fillExistingData(existing);
        } else {
            datePicker.setValue(LocalDate.now());
        }

        profitabilityField.textProperty().addListener((o, a, b) -> updatePreview());
        investedValueField.textProperty().addListener((o, a, b) -> updatePreview());

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

    private VBox buildFinancialTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        Label profitLabel = new Label("Rentabilidade Anual (%) *");
        profitLabel.setStyle("-fx-font-weight: bold;");
        profitabilityField.setPromptText("Ex: 13.75");
        profitabilityField.setTextFormatter(createDecimalFormatter());

        Label profitHint = new Label("💡 Taxa anual esperada em porcentagem");
        profitHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        Label valueLabel = new Label("Valor Investido *");
        valueLabel.setStyle("-fx-font-weight: bold;");
        investedValueField.setPromptText("R$ 0,00");
        investedValueField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(investedValueField);

        Label valueHint = new Label("💡 Quanto você colocou inicialmente");
        valueHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        box.getChildren().addAll(
                profitLabel, profitabilityField, profitHint,
                valueLabel, investedValueField, valueHint
        );

        return box;
    }

    private VBox buildPreviewTab() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(16));

        Label title = new Label("📊 Simulação de Ganhos");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        previewLabel.setWrapText(true);
        previewLabel.setStyle("-fx-font-size: 13px;");

        updatePreview();

        box.getChildren().addAll(title, previewLabel);
        return box;
    }

    private void updatePreview() {
        try {
            String profitText = profitabilityField.getText().replace(",", ".").trim();
            String valueText = investedValueField.getText();

            if (profitText.isEmpty() || valueText.isEmpty()) {
                previewLabel.setText("Preencha os campos para ver a simulação.");
                return;
            }

            double profitRate = Double.parseDouble(profitText) / 100.0;
            long investedCents = Money.textToCentsOrZero(valueText);

            if (investedCents == 0) {
                previewLabel.setText("Preencha o valor investido.");
                return;
            }

            long gain1Year = Math.round(investedCents * profitRate);
            long total1Year = investedCents + gain1Year;

            long gain5Years = Math.round(investedCents * Math.pow(1 + profitRate, 5) - investedCents);
            long total5Years = investedCents + gain5Years;

            StringBuilder preview = new StringBuilder();
            preview.append("Investimento: ").append(Money.centsToText(investedCents)).append("\n");
            preview.append("Taxa: ").append(String.format("%.2f", profitRate * 100)).append("% a.a.\n\n");
            preview.append("Projeção:\n");
            preview.append("• 1 ano: ").append(Money.centsToText(gain1Year))
                    .append(" → ").append(Money.centsToText(total1Year)).append("\n");
            preview.append("• 5 anos: ").append(Money.centsToText(gain5Years))
                    .append(" → ").append(Money.centsToText(total5Years)).append("\n\n");
            preview.append("⚠️ Simulação baseada em juros compostos.");

            previewLabel.setText(preview.toString());

        } catch (Exception e) {
            previewLabel.setText("Erro ao calcular. Verifique os valores.");
        }
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

        if (profitabilityField.getText().isBlank()) {
            errors.append("• Rentabilidade é obrigatória\n");
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

        BigDecimal profitability = new BigDecimal(profitabilityField.getText().replace(",", "."));

        long cents = Money.textToCentsSafe(investedValueField.getText());
        BigDecimal investedValue = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100));

        return new InvestmentTypeData(name, category, liquidity, date, profitability, investedValue);
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
            BigDecimal investedValue
    ) {}
}