package com.daniel.ui.pages;

import com.daniel.domain.*;
import com.daniel.service.DailyService;
import com.daniel.ui.Dialogs;
import com.daniel.ui.components.MoneyEditingCell;
import com.daniel.ui.model.InvestmentValueRow;
import com.daniel.util.Money;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DailyEntryPage implements Page {

    private final DailyService daily;

    private final VBox root = new VBox(12);

    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Button btnPrev = new Button("◀");
    private final Button btnNext = new Button("▶");
    private final Button btnToday = new Button("Hoje");

    private final Label hint = new Label();

    private final TextField cashField = new TextField();
    private final TableView<InvestmentValueRow> invTable = new TableView<>();
    private final ObservableList<InvestmentValueRow> invRows = FXCollections.observableArrayList();

    private final TableView<FlowRow> flowTable = new TableView<>();
    private final ObservableList<FlowRow> flowRows = FXCollections.observableArrayList();

    private final Button btnAddFlow = new Button("+ Fluxo");
    private final Button btnRemoveFlow = new Button("Remover fluxo");
    private final Button btnSave = new Button("Salvar");

    private final Label totalLabel = new Label("—");
    private final Label profitLabel = new Label("—");

    private LocalDate currentDate = LocalDate.now();
    private boolean loading = false;
    private boolean dirty = false;

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public DailyEntryPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));
        root.getStyleClass().add("page");

        root.getChildren().addAll(
                buildHeader(),
                buildTopCards(),
                buildTables(),
                buildBottomBar()
        );

        hookEvents();
        setDate(LocalDate.now());
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() { loadFor(currentDate); }

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

    private Parent buildHeader() {
        Label h1 = new Label("Registro Diário");
        h1.getStyleClass().add("h1");

        btnPrev.getStyleClass().add("icon-btn");
        btnNext.getStyleClass().add("icon-btn");
        btnToday.getStyleClass().add("ghost-btn");

        HBox nav = new HBox(8, btnPrev, btnNext, new Separator(), btnToday);
        nav.getStyleClass().add("date-nav");

        datePicker.getStyleClass().add("date-picker");
        datePicker.setEditable(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        hint.getStyleClass().add("muted");

        HBox row = new HBox(10, h1, spacer, hint, datePicker, nav);
        row.getStyleClass().add("header-row");
        return row;
    }

    private Parent buildTopCards() {
        VBox cashCard = new VBox(8);
        cashCard.getStyleClass().add("card");
        Label cashTitle = new Label("Dinheiro livre (CASH)");
        cashTitle.getStyleClass().add("card-title");

        cashField.getStyleClass().add("money-field");
        cashField.setPromptText("R$ 0,00");
        cashField.setTextFormatter(Money.currencyFormatterEditable());
        Money.applyFormatOnBlur(cashField);

        cashCard.getChildren().addAll(cashTitle, cashField);

        VBox summaryCard = new VBox(8);
        summaryCard.getStyleClass().add("card");

        Label t = new Label("Resumo (mercado)");
        t.getStyleClass().add("card-title");

        totalLabel.getStyleClass().add("big-value");
        profitLabel.getStyleClass().add("big-value");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);

        var l1 = new Label("Total do dia:");
        l1.getStyleClass().add("muted");
        var l2 = new Label("Lucro/Prejuízo:");
        l2.getStyleClass().add("muted");

        g.add(l1, 0, 0);
        g.add(totalLabel, 1, 0);
        g.add(l2, 0, 1);
        g.add(profitLabel, 1, 1);

        summaryCard.getChildren().addAll(t, g);

        HBox row = new HBox(12, cashCard, summaryCard);
        HBox.setHgrow(cashCard, Priority.ALWAYS);
        HBox.setHgrow(summaryCard, Priority.ALWAYS);
        return row;
    }

    private Parent buildTables() {
        buildInvTable();
        buildFlowTable();

        VBox left = new VBox(10);
        left.getChildren().addAll(sectionTitle("Valores dos investimentos (TOTAL no fim do dia)"), invTable);

        VBox right = new VBox(10);
        HBox flowActions = new HBox(8, btnAddFlow, btnRemoveFlow);
        btnAddFlow.getStyleClass().add("primary-btn");
        btnRemoveFlow.getStyleClass().add("danger-btn");
        right.getChildren().addAll(sectionTitle("Fluxos (CASH ↔ investimentos / investimento ↔ investimento)"), flowActions, flowTable);

        HBox row = new HBox(12, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        return row;
    }

    private Parent buildBottomBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("bottom-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnSave.getStyleClass().add("primary-btn");

        bar.getChildren().addAll(spacer, btnSave);
        return bar;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

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
        profitCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(""));
        profitCol.setPrefWidth(200);

        invTable.getColumns().setAll(nameCol, valueCol, profitCol);
        invTable.setItems(invRows);
        invTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        profitCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("pos","neg","muted");
                if (empty) { setText(null); return; }

                InvestmentValueRow row = getTableView().getItems().get(getIndex());
                long p = row.getProfitCents();
                if (p == 0) {
                    setText("—");
                    getStyleClass().add("muted");
                } else {
                    setText((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p)));
                    getStyleClass().add(p >= 0 ? "pos" : "neg");
                }
            }
        });
    }

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

    private void loadFor(LocalDate date) {
        try {
            loading = true;

            hint.setText(labelForDate(date));

            List<InvestmentType> types = daily.listTypes();
            DailyEntry entry = daily.loadEntry(date);

            cashField.setText(entry.cashCents() == 0 ? "" : Money.centsToText(entry.cashCents()));

            invRows.clear();
            for (InvestmentType t : types) {
                long v = entry.investmentValuesCents().getOrDefault(t.id(), 0L);
                invRows.add(new InvestmentValueRow(t.id(), t.name(), v));
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

    private void refreshSummaryPreview() {
        try {
            long cashCents = Money.textToCentsOrZero(cashField.getText());

            Map<Long, Long> invMap = new HashMap<>();
            for (InvestmentValueRow r : invRows) invMap.put(r.getInvestmentTypeId(), r.getValueCents());

            DailySummary s = daily.previewSummary(currentDate, cashCents, invMap);

            totalLabel.setText(daily.brl(s.totalTodayCents()));

            long p = s.totalProfitTodayCents();
            profitLabel.setText(p == 0 ? "—" : ((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p))));
            profitLabel.getStyleClass().removeAll("pos", "neg", "muted");
            if (p == 0) profitLabel.getStyleClass().add("muted");
            else profitLabel.getStyleClass().add(p >= 0 ? "pos" : "neg");

            for (InvestmentValueRow r : invRows) {
                long prof = s.investmentProfitTodayCents().getOrDefault(r.getInvestmentTypeId(), 0L);
                r.setProfitCents(prof);
            }

            invTable.refresh();

        } catch (Exception ignored) {}
    }

    private void saveCurrentDay() {
        if (invTable.getEditingCell() != null) invTable.edit(-1, null);

        long cashCents = Money.textToCentsSafe(cashField.getText());

        Map<Long, Long> invMap = new HashMap<>();
        for (InvestmentValueRow r : invRows) invMap.put(r.getInvestmentTypeId(), r.getValueCents());

        DailyEntry entry = new DailyEntry(currentDate, cashCents, invMap);
        daily.saveEntry(entry);

        dirty = false;
        loadFor(currentDate);
    }

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

    private String labelForDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Hoje • " + BR.format(date);
        if (date.equals(today.minusDays(1))) return "Ontem • " + BR.format(date);
        return BR.format(date);
    }

    private void addFlowDialog() {
        // (mantive como estava) — se der erro aqui depois, a gente ajusta com teu código real
        Dialogs.info("Ainda em evolução", "Fluxos já funcionam, mas vamos refinar o dialog no próximo ajuste.");
    }

    private void removeSelectedFlow() {
        FlowRow r = flowTable.getSelectionModel().getSelectedItem();
        if (r == null) return;

        boolean ok = Dialogs.confirm("Remover", "Remover este fluxo?", "Essa ação não pode ser desfeita.");
        if (!ok) return;

        daily.deleteFlow(r.getId());
        loadFor(currentDate);
    }

    public static final class FlowRow {
        private final long id;
        private final javafx.beans.property.StringProperty fromText = new javafx.beans.property.SimpleStringProperty("");
        private final javafx.beans.property.StringProperty toText = new javafx.beans.property.SimpleStringProperty("");
        private final javafx.beans.property.StringProperty amountText = new javafx.beans.property.SimpleStringProperty("");
        private final javafx.beans.property.StringProperty note = new javafx.beans.property.SimpleStringProperty("");

        private FlowRow(long id) { this.id = id; }

        public long getId() { return id; }
        public javafx.beans.property.StringProperty fromTextProperty() { return fromText; }
        public javafx.beans.property.StringProperty toTextProperty() { return toText; }
        public javafx.beans.property.StringProperty amountTextProperty() { return amountText; }
        public javafx.beans.property.StringProperty noteProperty() { return note; }

        // ✅ FIX AQUI: Long null-safe
        public static FlowRow fromFlow(Flow f, List<InvestmentType> types, DailyService daily) {
            Map<Long, String> nameById = new HashMap<>();
            for (InvestmentType t : types) nameById.put(t.id(), t.name());

            FlowRow r = new FlowRow(f.id());

            String from;
            if (f.fromKind() == FlowKind.CASH) from = "CASH";
            else {
                Long id = f.fromInvestmentTypeId();
                from = (id == null) ? "INV" : nameById.getOrDefault(id, "INV");
            }

            String to;
            if (f.toKind() == FlowKind.CASH) to = "CASH";
            else {
                Long id = f.toInvestmentTypeId();
                to = (id == null) ? "INV" : nameById.getOrDefault(id, "INV");
            }

            r.fromText.set(from);
            r.toText.set(to);
            r.amountText.set(daily.brl(f.amountCents()));
            r.note.set(f.note() == null ? "" : f.note());
            return r;
        }
    }
}
