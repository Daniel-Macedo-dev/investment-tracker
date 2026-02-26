package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.*;
import com.daniel.core.domain.entity.Enums.FlowKind;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.core.util.Money;
import com.daniel.presentation.view.components.MoneyEditingCell;
import com.daniel.presentation.view.components.UiComponents;
import com.daniel.presentation.view.util.Dialogs;
import com.daniel.presentation.viewmodel.InvestmentValueRow;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Daily entry page with cash tracking, investment values, and flow management.
 * Uses UiComponents utility for consistent styling and layout.
 */
public final class DailyEntryPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox contentBox = new VBox(20);
    private final ScrollPane root;

    // Header & Navigation
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Button btnPrev = new Button("◀");
    private final Button btnNext = new Button("▶");
    private final Button btnToday = new Button("Hoje");
    private final Label hint = new Label();

    // Top cards
    private final TextField cashField = new TextField();
    private final Label totalLabel = new Label("—");
    private final Label profitLabel = new Label("—");

    // Investment table
    private final TableView<InvestmentValueRow> invTable = new TableView<>();
    private final ObservableList<InvestmentValueRow> invRows = FXCollections.observableArrayList();

    // Flows table
    private final TableView<FlowRow> flowTable = new TableView<>();
    private final ObservableList<FlowRow> flowRows = FXCollections.observableArrayList();

    // Actions
    private final Button btnAddFlow = new Button("+ Fluxo");
    private final Button btnRemoveFlow = new Button("Remover fluxo");
    private final Button btnSave = new Button("Salvar");

    // State
    private LocalDate currentDate = LocalDate.now();
    private boolean loading = false;
    private boolean dirty = false;

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public DailyEntryPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        contentBox.setSpacing(20);
        contentBox.setPadding(new Insets(0));

        contentBox.getChildren().addAll(
                buildHeader(),
                buildTopCards(),
                buildTables(),
                buildBottomBar()
        );

        root = UiComponents.pageScroll(contentBox);
        hookEvents();
        setDate(LocalDate.now());
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        loadFor(currentDate);
    }

    /**
     * Navigate to a specific date with dirty-state checking.
     */
    public void setDate(LocalDate date) {
        if (date == null) return;

        if (!ensureCanNavigate(date)) {
            loading = true;
            datePicker.setValue(currentDate);
            loading = false;
            return;
        }

        currentDate = date;

        loading = true;
        datePicker.setValue(date);
        loading = false;

        loadFor(date);
    }

    /**
     * Builds the header: title, date navigation, date picker, and hint.
     */
    private Parent buildHeader() {
        VBox header = UiComponents.pageHeader("Registro Diário",
                "Preencha o total do fim do dia e os fluxos (para separar aporte/resgate do lucro de mercado).");

        // Date navigation controls
        btnPrev.getStyleClass().add("icon-btn");
        btnNext.getStyleClass().add("icon-btn");
        btnToday.getStyleClass().add("ghost-btn");

        HBox nav = new HBox(8, btnPrev, btnNext, new Separator(), btnToday);
        nav.getStyleClass().add("date-nav");

        datePicker.getStyleClass().add("date-picker");
        datePicker.setEditable(false);

        hint.getStyleClass().add("muted");

        // Build header row with date controls
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox dateRow = new HBox(10, hint, datePicker, nav);
        dateRow.setAlignment(Pos.CENTER_RIGHT);

        VBox headerContainer = new VBox(12, header, dateRow);
        return headerContainer;
    }

    /**
     * Builds top cards: cash input and summary display.
     */
    private Parent buildTopCards() {
        // Cash card
        VBox cashCard = new VBox(8);
        cashCard.getStyleClass().add("card");

        Label cashTitle = new Label("Dinheiro livre (CASH)");
        cashTitle.getStyleClass().add("card-title");

        cashField.getStyleClass().add("money-field");
        cashField.setPromptText("R$ 0,00");
        cashField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(cashField);

        cashCard.getChildren().addAll(cashTitle, cashField);

        // Summary card
        VBox summaryCard = new VBox(8);
        summaryCard.getStyleClass().add("card");

        Label summaryTitle = new Label("Resumo (mercado)");
        summaryTitle.getStyleClass().add("card-title");

        totalLabel.getStyleClass().add("big-value");
        profitLabel.getStyleClass().add("big-value");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        Label totalLbl = new Label("Total do dia:");
        totalLbl.getStyleClass().add("muted");
        Label profitLbl = new Label("Lucro/Prejuízo:");
        profitLbl.getStyleClass().add("muted");

        grid.add(totalLbl, 0, 0);
        grid.add(totalLabel, 1, 0);
        grid.add(profitLbl, 0, 1);
        grid.add(profitLabel, 1, 1);

        summaryCard.getChildren().addAll(summaryTitle, grid);

        // Two-column layout for cards
        HBox topRow = new HBox(12, cashCard, summaryCard);
        HBox.setHgrow(cashCard, Priority.ALWAYS);
        HBox.setHgrow(summaryCard, Priority.ALWAYS);

        return topRow;
    }

    /**
     * Builds investment and flow tables.
     */
    private Parent buildTables() {
        buildInvTable();
        buildFlowTable();

        // Investment table card (left column)
        VBox leftCard = new VBox(10);
        leftCard.getStyleClass().add("card");
        leftCard.getChildren().addAll(
                UiComponents.sectionTitle("Valores dos investimentos (TOTAL no fim do dia)"),
                invTable
        );

        // Flow table card (right column)
        btnAddFlow.getStyleClass().add("primary-btn");
        btnRemoveFlow.getStyleClass().add("danger-btn");
        HBox flowActions = new HBox(8, btnAddFlow, btnRemoveFlow);

        VBox rightCard = new VBox(10);
        rightCard.getStyleClass().add("card");
        rightCard.getChildren().addAll(
                UiComponents.sectionTitle("Fluxos (CASH ↔ investimentos / investimento ↔ investimento)"),
                flowActions,
                flowTable
        );

        // Two-column layout
        HBox row = new HBox(12, leftCard, rightCard);
        HBox.setHgrow(leftCard, Priority.ALWAYS);
        HBox.setHgrow(rightCard, Priority.ALWAYS);

        return row;
    }

    /**
     * Builds bottom action bar with save button.
     */
    private Parent buildBottomBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("bottom-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnSave.getStyleClass().add("primary-btn");

        bar.getChildren().addAll(spacer, btnSave);
        return bar;
    }

    /**
     * Builds the investment values table with editable value column.
     */
    private void buildInvTable() {
        invTable.getStyleClass().add("table");
        invTable.setEditable(true);

        TableColumn<InvestmentValueRow, String> nameCol = new TableColumn<>("Investimento");
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        nameCol.setPrefWidth(320);

        TableColumn<InvestmentValueRow, Long> valueCol = new TableColumn<>("Valor do dia");
        valueCol.setCellValueFactory(c -> c.getValue().valueCentsProperty().asObject());
        valueCol.setCellFactory(col -> new MoneyEditingCell<>());
        valueCol.setOnEditCommit(e -> {
            e.getRowValue().setValueCents(e.getNewValue() == null ? 0L : e.getNewValue());
            dirty = true;
            refreshSummaryPreview();
        });
        valueCol.setPrefWidth(220);

        TableColumn<InvestmentValueRow, String> profitCol = new TableColumn<>("Lucro (mercado)");
        profitCol.setCellValueFactory(c -> new SimpleStringProperty(""));
        profitCol.setPrefWidth(200);

        // Custom cell for profit display with styling
        profitCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("pos", "neg", "muted");
                if (empty) {
                    setText(null);
                    return;
                }

                InvestmentValueRow row = getTableView().getItems().get(getIndex());
                long profit = row.getProfitCents();
                if (profit == 0) {
                    setText("—");
                    getStyleClass().add("muted");
                } else {
                    setText((profit >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(profit)));
                    getStyleClass().add(profit >= 0 ? "pos" : "neg");
                }
            }
        });

        invTable.getColumns().setAll(nameCol, valueCol, profitCol);
        invTable.setItems(invRows);
        invTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    /**
     * Builds the flow table.
     */
    private void buildFlowTable() {
        flowTable.getStyleClass().add("table");
        flowTable.setEditable(false);

        TableColumn<FlowRow, String> fromCol = new TableColumn<>("De");
        fromCol.setCellValueFactory(c -> c.getValue().fromTextProperty());

        TableColumn<FlowRow, String> toCol = new TableColumn<>("Para");
        toCol.setCellValueFactory(c -> c.getValue().toTextProperty());

        TableColumn<FlowRow, String> amtCol = new TableColumn<>("Valor");
        amtCol.setCellValueFactory(c -> c.getValue().amountTextProperty());

        TableColumn<FlowRow, String> noteCol = new TableColumn<>("Obs");
        noteCol.setCellValueFactory(c -> c.getValue().noteProperty());

        flowTable.getColumns().setAll(fromCol, toCol, amtCol, noteCol);
        flowTable.setItems(flowRows);
        flowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    /**
     * Hooks all event listeners.
     */
    private void hookEvents() {
        btnPrev.setOnAction(e -> setDate(currentDate.minusDays(1)));
        btnNext.setOnAction(e -> setDate(currentDate.plusDays(1)));
        btnToday.setOnAction(e -> setDate(LocalDate.now()));

        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (loading) return;
            if (newV == null) return;
            setDate(newV);
        });

        cashField.textProperty().addListener((obs, o, n) -> {
            if (!loading) {
                dirty = true;
                refreshSummaryPreview();
            }
        });

        btnSave.setOnAction(e -> {
            try {
                saveCurrentDay();
                Dialogs.info("Salvo", "Registro do dia salvo com sucesso.");
            } catch (Exception ex) {
                Dialogs.error(ex.getMessage());
            }
        });

        btnAddFlow.setOnAction(e -> addFlowDialog());
        btnRemoveFlow.setOnAction(e -> removeSelectedFlow());
    }

    /**
     * Loads data for the given date.
     */
    private void loadFor(LocalDate date) {
        try {
            loading = true;

            hint.setText(labelForDate(date));

            List<InvestmentType> types = daily.listTypes();
            DailyEntry entry = daily.loadEntry(date);

            cashField.setText(entry.cashCents() == 0 ? "" : Money.centsToText(entry.cashCents()));

            invRows.clear();
            for (InvestmentType t : types) {
                long value = entry.investmentValuesCents().getOrDefault(t.id(), 0L);
                invRows.add(new InvestmentValueRow(t.id(), t.name(), value));
            }

            flowRows.clear();
            for (Flow f : daily.flowsFor(date)) {
                flowRows.add(FlowRow.fromFlow(f, types, daily));
            }

            dirty = false;
            refreshSummaryPreview();

        } finally {
            loading = false;
        }
    }

    /**
     * Refreshes summary preview based on current input values.
     */
    private void refreshSummaryPreview() {
        try {
            long cashCents = Money.textToCentsOrZero(cashField.getText());

            Map<Long, Long> invMap = new HashMap<>();
            for (InvestmentValueRow r : invRows) {
                invMap.put(r.getInvestmentTypeId(), r.getValueCents());
            }

            DailySummary summary = daily.previewSummary(currentDate, cashCents, invMap);

            totalLabel.setText(daily.brl(summary.totalTodayCents()));

            long profit = summary.totalProfitTodayCents();
            profitLabel.setText(profit == 0 ? "—" : ((profit >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(profit))));
            profitLabel.getStyleClass().removeAll("pos", "neg", "muted");
            if (profit == 0) {
                profitLabel.getStyleClass().add("muted");
            } else {
                profitLabel.getStyleClass().add(profit >= 0 ? "pos" : "neg");
            }

            // Update profit display for each investment type
            for (InvestmentValueRow r : invRows) {
                long investmentProfit = summary.investmentProfitTodayCents().getOrDefault(r.getInvestmentTypeId(), 0L);
                r.setProfitCents(investmentProfit);
            }

            invTable.refresh();

        } catch (Exception ignored) {}
    }

    /**
     * Saves the current day's data.
     */
    private void saveCurrentDay() {
        if (invTable.getEditingCell() != null) {
            invTable.edit(-1, null);
        }

        long cashCents = Money.textToCentsSafe(cashField.getText());

        Map<Long, Long> invMap = new HashMap<>();
        for (InvestmentValueRow r : invRows) {
            invMap.put(r.getInvestmentTypeId(), r.getValueCents());
        }

        DailyEntry entry = new DailyEntry(currentDate, cashCents, invMap);
        daily.saveEntry(entry);

        dirty = false;
        loadFor(currentDate);
    }

    /**
     * Checks if we can navigate away from current date (with unsaved changes warning).
     */
    private boolean ensureCanNavigate(LocalDate target) {
        if (!dirty) return true;

        boolean save = Dialogs.confirm(
                "Alterações não salvas",
                "Você alterou valores e não salvou.",
                "Deseja salvar antes de trocar de dia?"
        );

        if (save) {
            try {
                saveCurrentDay();
                return true;
            } catch (Exception e) {
                Dialogs.error(e.getMessage());
                return false;
            }
        } else {
            dirty = false;
            return true;
        }
    }

    /**
     * Formats date label with relative descriptions (hoje/ontem/etc).
     */
    private String labelForDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Hoje • " + BR.format(date);
        if (date.equals(today.minusDays(1))) return "Ontem • " + BR.format(date);
        return BR.format(date);
    }

    /**
     * Opens dialog to add a new flow.
     */
    private void addFlowDialog() {
        // Placeholder for now - extends with full flow creation dialog
        Dialogs.info("Ainda em evolução", "Fluxos já funcionam, mas vamos refinar o dialog no próximo ajuste.");
    }

    /**
     * Removes the selected flow from the table.
     */
    private void removeSelectedFlow() {
        FlowRow selected = flowTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        boolean ok = Dialogs.confirm("Remover", "Remover este fluxo?", "Essa ação não pode ser desfeita.");
        if (!ok) return;

        daily.deleteFlow(selected.getId());
        loadFor(currentDate);
    }

    /**
     * Inner class representing a row in the flows table.
     */
    public static final class FlowRow {
        private final long id;
        private final StringProperty fromText = new SimpleStringProperty("");
        private final StringProperty toText = new SimpleStringProperty("");
        private final StringProperty amountText = new SimpleStringProperty("");
        private final StringProperty note = new SimpleStringProperty("");

        private FlowRow(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public StringProperty fromTextProperty() {
            return fromText;
        }

        public StringProperty toTextProperty() {
            return toText;
        }

        public StringProperty amountTextProperty() {
            return amountText;
        }

        public StringProperty noteProperty() {
            return note;
        }

        /**
         * Creates a FlowRow from a Flow domain object.
         */
        public static FlowRow fromFlow(Flow flow, List<InvestmentType> types, DailyTrackingUseCase daily) {
            Map<Long, String> nameById = new HashMap<>();
            for (InvestmentType t : types) {
                nameById.put(t.id(), t.name());
            }

            FlowRow row = new FlowRow(flow.id());

            String from;
            if (flow.fromKind() == FlowKind.CASH) {
                from = "CASH";
            } else {
                Long typeId = flow.fromInvestmentTypeId();
                from = (typeId == null) ? "INV" : nameById.getOrDefault(typeId, "INV");
            }

            String to;
            if (flow.toKind() == FlowKind.CASH) {
                to = "CASH";
            } else {
                Long typeId = flow.toInvestmentTypeId();
                to = (typeId == null) ? "INV" : nameById.getOrDefault(typeId, "INV");
            }

            row.fromText.set(from);
            row.toText.set(to);
            row.amountText.set(daily.brl(flow.amountCents()));
            row.note.set(flow.note() == null ? "" : flow.note());
            return row;
        }
    }
}
