package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.ARCADiversificationStrategy;
import com.daniel.core.service.ARCADiversificationStrategy.*;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.core.service.DiversificationCalculator;
import com.daniel.core.service.DiversificationCalculator.*;
import com.daniel.core.util.Money;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.util.*;

public final class DiversificationPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox root = new VBox(16);

    private final ToggleGroup methodGroup = new ToggleGroup();
    private final RadioButton arcaRadio = new RadioButton("Método ARCA (Primo Rico)");
    private final RadioButton customRadio = new RadioButton("Personalizado");

    private final ToggleGroup calculationTypeGroup = new ToggleGroup();
    private final RadioButton rebalanceByContributionRadio = new RadioButton("Rebalancear por Aporte");
    private final RadioButton rebalanceByTargetRadio = new RadioButton("Patrimônio Alvo");

    private final TextField targetPatrimonyField = new TextField();
    private final VBox customInputsBox = new VBox(12);
    private final Map<CategoryEnum, TextField> customPercentages = new HashMap<>();

    private final TableView<AllocationRow> currentTable = new TableView<>();
    private final TableView<AllocationRow> idealTable = new TableView<>();
    private final TableView<SuggestionRow> suggestionsTable = new TableView<>();

    private final Label totalPatrimonyLabel = new Label("—");

    public DiversificationPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Calculadora de Diversificação");
        h1.getStyleClass().add("h1");

        Label subtitle = new Label("Analise e otimize a distribuição dos seus investimentos");
        subtitle.getStyleClass().add("muted");

        VBox patrimonyBox = buildPatrimonyCard();
        VBox methodBox = buildMethodSelector();
        VBox calculationBox = buildCalculationType();

        HBox tablesRow = new HBox(12);
        VBox currentBox = buildCurrentAllocationTable();
        VBox idealBox = buildIdealAllocationTable();
        HBox.setHgrow(currentBox, Priority.ALWAYS);
        HBox.setHgrow(idealBox, Priority.ALWAYS);
        tablesRow.getChildren().addAll(currentBox, idealBox);

        VBox suggestionsBox = buildSuggestionsTable();

        root.getChildren().addAll(h1, subtitle, patrimonyBox, calculationBox, methodBox, tablesRow, suggestionsBox);
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        refreshData();
    }

    // Seletor de tipo de cálculo
    private VBox buildCalculationType() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");

        Label title = new Label("Tipo de Cálculo");
        title.getStyleClass().add("card-title");

        rebalanceByContributionRadio.setToggleGroup(calculationTypeGroup);
        rebalanceByTargetRadio.setToggleGroup(calculationTypeGroup);
        rebalanceByContributionRadio.setSelected(true);

        Label contributionHint = new Label("💰 Recomenda apenas APORTES nas categorias abaixo do ideal (sem vender)");
        contributionHint.getStyleClass().add("muted");
        contributionHint.setStyle("-fx-font-size: 11px;");

        Label targetHint = new Label("🎯 Calcula quanto aportar em cada categoria para atingir um patrimônio alvo");
        targetHint.getStyleClass().add("muted");
        targetHint.setStyle("-fx-font-size: 11px;");

        // Campo de patrimônio alvo (visível apenas no modo alvo)
        Label targetLabel = new Label("Patrimônio Alvo:");
        targetLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        targetPatrimonyField.setPromptText("R$ 100.000,00");
        targetPatrimonyField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(targetPatrimonyField);

        VBox targetBox = new VBox(8, targetLabel, targetPatrimonyField, targetHint);
        targetBox.setVisible(false);
        targetBox.setManaged(false);

        calculationTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isTarget = newVal == rebalanceByTargetRadio;
            targetBox.setVisible(isTarget);
            targetBox.setManaged(isTarget);
            refreshData();
        });

        Button recalculateBtn = new Button("Recalcular");
        recalculateBtn.getStyleClass().add("primary-btn");
        recalculateBtn.setOnAction(e -> refreshData());

        box.getChildren().addAll(
                title,
                rebalanceByContributionRadio, contributionHint,
                rebalanceByTargetRadio, targetBox,
                recalculateBtn
        );

        return box;
    }

    private VBox buildMethodSelector() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");

        Label title = new Label("Método de Diversificação");
        title.getStyleClass().add("card-title");

        arcaRadio.setToggleGroup(methodGroup);
        customRadio.setToggleGroup(methodGroup);
        arcaRadio.setSelected(true);

        Label arcaHint = new Label("📊 Renda Fixa 40% • Ações 30% • Outros 25% • Cripto 5%");
        arcaHint.getStyleClass().add("muted");
        arcaHint.setStyle("-fx-font-size: 11px;");

        buildCustomInputs();
        customInputsBox.setVisible(false);
        customInputsBox.setManaged(false);

        methodGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCustom = newVal == customRadio;
            customInputsBox.setVisible(isCustom);
            customInputsBox.setManaged(isCustom);
            refreshData();
        });

        box.getChildren().addAll(title, arcaRadio, arcaHint, customRadio, customInputsBox);
        return box;
    }

    private void buildCustomInputs() {
        Label customTitle = new Label("Configure as porcentagens desejadas:");
        customTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        int row = 0;
        for (CategoryEnum cat : CategoryEnum.values()) {
            Circle circle = new Circle(5);
            circle.setFill(Color.web(cat.getColor()));

            Label label = new Label(cat.getDisplayName());

            TextField field = new TextField("0.0");
            field.setPrefWidth(80);
            field.setPromptText("0.0");

            Label percent = new Label("%");

            customPercentages.put(cat, field);

            grid.add(circle, 0, row);
            grid.add(label, 1, row);
            grid.add(field, 2, row);
            grid.add(percent, 3, row);

            row++;
        }

        Label hint = new Label("💡 As porcentagens devem somar 100%");
        hint.getStyleClass().add("muted");
        hint.setStyle("-fx-font-size: 11px;");

        customInputsBox.getChildren().addAll(customTitle, grid, hint);
    }

    private VBox buildPatrimonyCard() {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");

        Label title = new Label("Patrimônio Atual");
        title.getStyleClass().add("card-title");

        totalPatrimonyLabel.getStyleClass().add("big-value");
        totalPatrimonyLabel.setStyle("-fx-font-size: 24px;");

        box.getChildren().addAll(title, totalPatrimonyLabel);
        return box;
    }

    private VBox buildCurrentAllocationTable() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");

        Label title = new Label("Distribuição Atual");
        title.getStyleClass().add("card-title");

        currentTable.getStyleClass().add("table");
        currentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AllocationRow, String> catCol = new TableColumn<>("Categoria");
        catCol.setCellValueFactory(c -> c.getValue().categoryProperty());
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    AllocationRow row = getTableRow().getItem();
                    if (row != null) {
                        setGraphic(createCategoryBadge(row.getCategory(), item));
                        setText(null);
                    } else {
                        setText(item);
                        setGraphic(null);
                    }
                }
            }
        });

        TableColumn<AllocationRow, String> valueCol = new TableColumn<>("Valor");
        valueCol.setCellValueFactory(c -> c.getValue().valueProperty());

        TableColumn<AllocationRow, String> percentCol = new TableColumn<>("%");
        percentCol.setCellValueFactory(c -> c.getValue().percentageProperty());

        currentTable.getColumns().setAll(catCol, valueCol, percentCol);
        currentTable.setPlaceholder(new Label("Nenhum dado disponível"));

        box.getChildren().addAll(title, currentTable);
        VBox.setVgrow(currentTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildIdealAllocationTable() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");

        Label title = new Label("Distribuição Ideal");
        title.getStyleClass().add("card-title");

        idealTable.getStyleClass().add("table");
        idealTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AllocationRow, String> catCol = new TableColumn<>("Categoria");
        catCol.setCellValueFactory(c -> c.getValue().categoryProperty());
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    AllocationRow row = getTableRow().getItem();
                    if (row != null) {
                        setGraphic(createCategoryBadge(row.getCategory(), item));
                        setText(null);
                    } else {
                        setText(item);
                        setGraphic(null);
                    }
                }
            }
        });

        TableColumn<AllocationRow, String> valueCol = new TableColumn<>("Valor");
        valueCol.setCellValueFactory(c -> c.getValue().valueProperty());

        TableColumn<AllocationRow, String> percentCol = new TableColumn<>("%");
        percentCol.setCellValueFactory(c -> c.getValue().percentageProperty());

        idealTable.getColumns().setAll(catCol, valueCol, percentCol);
        idealTable.setPlaceholder(new Label("Selecione um método"));

        box.getChildren().addAll(title, idealTable);
        VBox.setVgrow(idealTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildSuggestionsTable() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");

        Label title = new Label("Sugestões de Aporte");
        title.getStyleClass().add("card-title");

        suggestionsTable.getStyleClass().add("table");
        suggestionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<SuggestionRow, String> catCol = new TableColumn<>("Categoria");
        catCol.setCellValueFactory(c -> c.getValue().categoryProperty());
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    SuggestionRow row = getTableRow().getItem();
                    if (row != null) {
                        setGraphic(createCategoryBadge(row.getCategory(), item));
                        setText(null);
                    } else {
                        setText(item);
                        setGraphic(null);
                    }
                }
            }
        });

        TableColumn<SuggestionRow, String> actionCol = new TableColumn<>("Aporte Sugerido");
        actionCol.setCellValueFactory(c -> c.getValue().actionProperty());
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Investir") || item.contains("Aportar")) {
                        setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                    } else if (item.contains("balanceado")) {
                        setStyle("-fx-text-fill: #6b7280; -fx-font-style: italic;");
                    }
                }
            }
        });

        suggestionsTable.getColumns().setAll(catCol, actionCol);
        suggestionsTable.setPlaceholder(new Label("Sua carteira está perfeitamente balanceada!"));

        box.getChildren().addAll(title, suggestionsTable);
        return box;
    }

    private void refreshData() {
        LocalDate today = LocalDate.now();
        List<InvestmentType> investments = daily.listTypes();

        if (investments.isEmpty()) {
            totalPatrimonyLabel.setText("—");
            currentTable.getItems().clear();
            idealTable.getItems().clear();
            suggestionsTable.getItems().clear();
            return;
        }

        Map<Long, Long> currentValues = daily.getAllCurrentValues(today);
        long totalPatrimony = daily.getTotalPatrimony(today);

        totalPatrimonyLabel.setText(daily.brl(totalPatrimony));

        DiversificationData currentData = DiversificationCalculator.calculateCurrent(
                investments,
                currentValues
        );

        updateCurrentTable(currentData);

        if (arcaRadio.isSelected()) {
            updateARCAIdeal(totalPatrimony, currentData);
        } else {
            updateCustomIdeal(totalPatrimony, currentData);
        }
    }

    private void updateCurrentTable(DiversificationData data) {
        var rows = FXCollections.<AllocationRow>observableArrayList();

        for (CategoryAllocation alloc : data.allocations()) {
            rows.add(new AllocationRow(
                    alloc.category(),
                    daily.brl(alloc.valueCents()),
                    String.format("%.1f%%", alloc.percentage())
            ));
        }

        currentTable.setItems(rows);
    }

    private void updateARCAIdeal(long currentPatrimony, DiversificationData currentData) {
        Map<CategoryEnum, Double> profile = ARCADiversificationStrategy.getARCAProfile();

        List<DiversificationSuggestion> suggestions;
        long referencePatrimony;

        // Escolher metodo de cálculo
        if (rebalanceByTargetRadio.isSelected()) {
            long targetPatrimony = getTargetPatrimony(currentPatrimony);
            referencePatrimony = targetPatrimony;
            suggestions = ARCADiversificationStrategy.calculateSuggestionsByTarget(
                    currentPatrimony, targetPatrimony, currentData.valuesCents(), profile
            );
        } else {
            referencePatrimony = currentPatrimony;
            suggestions = ARCADiversificationStrategy.calculateSuggestionsByContribution(
                    currentPatrimony, currentData.valuesCents(), profile
            );
        }

        var idealRows = FXCollections.<AllocationRow>observableArrayList();
        for (var sug : suggestions) {
            idealRows.add(new AllocationRow(
                    sug.category(),
                    daily.brl(sug.idealCents()),
                    String.format("%.1f%%", (sug.idealCents() * 100.0 / referencePatrimony))
            ));
        }
        idealTable.setItems(idealRows);

        var suggestionRows = FXCollections.<SuggestionRow>observableArrayList();
        for (var sug : suggestions) {
            if (sug.aporteNecessarioCents() > 100_00) {
                String action = "Aportar " + daily.brl(sug.aporteNecessarioCents());
                suggestionRows.add(new SuggestionRow(sug.category(), action));
            }
        }

        if (suggestionRows.isEmpty()) {
            suggestionRows.add(new SuggestionRow(CategoryEnum.RENDA_FIXA, "✅ Carteira balanceada"));
        }

        suggestionsTable.setItems(suggestionRows);
    }

    private void updateCustomIdeal(long currentPatrimony, DiversificationData currentData) {
        double total = 0;
        Map<CategoryEnum, Double> customProfile = new HashMap<>();

        try {
            for (var entry : customPercentages.entrySet()) {
                double value = Double.parseDouble(entry.getValue().getText().replace(",", "."));
                customProfile.put(entry.getKey(), value / 100.0);
                total += value;
            }

            if (Math.abs(total - 100.0) > 0.1) {
                idealTable.setPlaceholder(new Label("⚠️ As porcentagens devem somar 100%!"));
                idealTable.getItems().clear();
                suggestionsTable.getItems().clear();
                return;
            }

            List<DiversificationSuggestion> suggestions;
            long referencePatrimony;

            if (rebalanceByTargetRadio.isSelected()) {
                long targetPatrimony = getTargetPatrimony(currentPatrimony);
                referencePatrimony = targetPatrimony;
                suggestions = ARCADiversificationStrategy.calculateSuggestionsByTarget(
                        currentPatrimony, targetPatrimony, currentData.valuesCents(), customProfile
                );
            } else {
                referencePatrimony = currentPatrimony;
                suggestions = ARCADiversificationStrategy.calculateSuggestionsByContribution(
                        currentPatrimony, currentData.valuesCents(), customProfile
                );
            }

            var idealRows = FXCollections.<AllocationRow>observableArrayList();
            for (var sug : suggestions) {
                idealRows.add(new AllocationRow(
                        sug.category(),
                        daily.brl(sug.idealCents()),
                        String.format("%.1f%%", (sug.idealCents() * 100.0 / referencePatrimony))
                ));
            }
            idealTable.setItems(idealRows);

            var suggestionRows = FXCollections.<SuggestionRow>observableArrayList();
            for (var sug : suggestions) {
                if (sug.aporteNecessarioCents() > 100_00) {
                    String action = "Aportar " + daily.brl(sug.aporteNecessarioCents());
                    suggestionRows.add(new SuggestionRow(sug.category(), action));
                }
            }

            if (suggestionRows.isEmpty()) {
                suggestionRows.add(new SuggestionRow(CategoryEnum.RENDA_FIXA, "✅ Carteira balanceada"));
            }

            suggestionsTable.setItems(suggestionRows);

        } catch (NumberFormatException e) {
            idealTable.setPlaceholder(new Label("⚠️ Valores inválidos nas porcentagens"));
            idealTable.getItems().clear();
            suggestionsTable.getItems().clear();
        }
    }

    private long getTargetPatrimony(long currentPatrimony) {
        String targetText = targetPatrimonyField.getText();
        if (targetText == null || targetText.trim().isEmpty()) {
            return currentPatrimony;
        }

        try {
            return Money.textToCentsSafe(targetText);
        } catch (Exception e) {
            return currentPatrimony;
        }
    }

    private HBox createCategoryBadge(CategoryEnum category, String text) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        Circle circle = new Circle(5);
        circle.setFill(Color.web(category.getColor()));

        Label label = new Label(text);

        box.getChildren().addAll(circle, label);
        return box;
    }

    private static class AllocationRow {
        private final CategoryEnum category;
        private final String value;
        private final String percentage;

        AllocationRow(CategoryEnum category, String value, String percentage) {
            this.category = category;
            this.value = value;
            this.percentage = percentage;
        }

        public SimpleStringProperty categoryProperty() {
            return new SimpleStringProperty(category.getDisplayName());
        }

        public SimpleStringProperty valueProperty() {
            return new SimpleStringProperty(value);
        }

        public SimpleStringProperty percentageProperty() {
            return new SimpleStringProperty(percentage);
        }

        public CategoryEnum getCategory() {
            return category;
        }
    }

    private static class SuggestionRow {
        private final CategoryEnum category;
        private final String action;

        SuggestionRow(CategoryEnum category, String action) {
            this.category = category;
            this.action = action;
        }

        public SimpleStringProperty categoryProperty() {
            return new SimpleStringProperty(category.getDisplayName());
        }

        public SimpleStringProperty actionProperty() {
            return new SimpleStringProperty(action);
        }

        public CategoryEnum getCategory() {
            return category;
        }
    }
}