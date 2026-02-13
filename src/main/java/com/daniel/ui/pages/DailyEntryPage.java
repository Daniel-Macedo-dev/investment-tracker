package com.daniel.ui.pages;

import com.daniel.domain.*;
import com.daniel.service.DailyService;
import com.daniel.ui.Dialogs;
import com.daniel.ui.FxConverters;
import com.daniel.ui.components.MoneyEditingCell;
import com.daniel.ui.model.InvestmentValueRow;
import com.daniel.util.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;

public final class DailyEntryPage implements Page {

    private final DailyService daily;

    private final BorderPane root = new BorderPane();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());

    private final TextField cashField = new TextField();

    private final ObservableList<InvestmentValueRow> invRows = FXCollections.observableArrayList();
    private final TableView<InvestmentValueRow> invTable = new TableView<>(invRows);

    private final ObservableList<Flow> flowItems = FXCollections.observableArrayList();
    private final TableView<Flow> flowTable = new TableView<>(flowItems);

    private final Map<Long, String> typeNameCache = new HashMap<>();

    private boolean dirty = false;
    private boolean loading = false;

    public DailyEntryPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));
        root.setTop(topBar());
        root.setCenter(center());

        cashField.setTextFormatter(Money.currencyFormatterEditable());
        cashField.setPromptText("R$ 0,00");
        Money.applyCurrencyFormatOnBlur(cashField);

        cashField.textProperty().addListener((o, a, b) -> {
            if (!loading) dirty = true;
        });

        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (loading) return;
            if (Objects.equals(oldV, newV)) return;

            if (!ensureCanNavigate()) {
                loading = true;
                datePicker.setValue(oldV);
                loading = false;
                return;
            }
            loadFor(newV);
        });

        buildInvTable();
        buildFlowTable();
    }

    @Override public Parent view() { return root; }
    @Override public void onShow() { loadFor(datePicker.getValue()); }

    private Parent topBar() {
        Label h1 = new Label("Registro Diário");
        h1.getStyleClass().add("h1");

        Button load = new Button("Carregar");
        load.setOnAction(e -> {
            if (!ensureCanNavigate()) return;
            loadFor(datePicker.getValue());
        });

        Button copyYesterday = new Button("Copiar ontem");
        copyYesterday.setTooltip(new Tooltip("Copia os valores totais do dia anterior para você ajustar o dia."));
        copyYesterday.setOnAction(e -> copyYesterday());

        Button save = new Button("Salvar dia");
        save.setOnAction(e -> saveDay());

        HBox right = new HBox(8, load, copyYesterday, save);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, h1, spacer, new Label("Data:"), datePicker, right);
    }

    private Parent center() {
        VBox box = new VBox(12);

        box.getChildren().add(card("Dinheiro Livre (valor total do dia)", cashField));
        box.getChildren().add(investCard());
        box.getChildren().add(flowCard());

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("app-scroll");
        return sp;
    }

    private VBox card(String title, Control body) {
        VBox v = new VBox(8);
        v.getStyleClass().add("card");
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        v.getChildren().addAll(t, body);
        return v;
    }

    private VBox investCard() {
        VBox v = new VBox(10);
        v.getStyleClass().add("card");

        Label title = new Label("Investimentos (valor total de cada tipo no dia)");
        title.getStyleClass().add("card-title");

        v.getChildren().addAll(title, invTable);
        invTable.setPrefHeight(240);
        return v;
    }

    private VBox flowCard() {
        VBox v = new VBox(10);
        v.getStyleClass().add("card");

        Label title = new Label("Movimentações do dia (não contam como lucro)");
        title.getStyleClass().add("card-title");

        Button addFlow = new Button("+ Transferir / Aportar");
        addFlow.setOnAction(e -> openFlowDialog());

        Button delFlow = new Button("Excluir selecionado");
        delFlow.setOnAction(e -> deleteSelectedFlow());

        HBox actions = new HBox(8, addFlow, delFlow);

        v.getChildren().addAll(title, actions, flowTable);
        flowTable.setPrefHeight(280);
        return v;
    }

    private void buildInvTable() {
        invTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        invTable.setEditable(true);

        TableColumn<InvestmentValueRow, String> colType = new TableColumn<>("Tipo");
        colType.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getType().name()));
        colType.setEditable(false);

        TableColumn<InvestmentValueRow, Number> colValue = new TableColumn<>("Valor do dia");
        colValue.setCellValueFactory(v -> v.getValue().valueCentsProperty().divide(100.0));
        colValue.setCellFactory(c -> new MoneyEditingCell<>());
        colValue.setOnEditCommit(ev -> {
            long cents = Math.round(ev.getNewValue().doubleValue() * 100);
            ev.getRowValue().setValueCents(Math.max(0, cents));
            dirty = true;
        });

        invTable.getColumns().setAll(colType, colValue);

        invTable.getItems().addListener((javafx.collections.ListChangeListener<InvestmentValueRow>) c -> {
            if (!loading) dirty = true;
        });
    }

    private void buildFlowTable() {
        flowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Flow, String> colFrom = new TableColumn<>("De");
        colFrom.setCellValueFactory(v -> new SimpleStringProperty(endpointText(v.getValue(), true)));

        TableColumn<Flow, String> colTo = new TableColumn<>("Para");
        colTo.setCellValueFactory(v -> new SimpleStringProperty(endpointText(v.getValue(), false)));

        TableColumn<Flow, String> colValue = new TableColumn<>("Valor");
        colValue.setCellValueFactory(v -> new SimpleStringProperty(Money.centsToCurrencyText(v.getValue().amountCents())));

        TableColumn<Flow, String> colNote = new TableColumn<>("Obs");
        colNote.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().note() == null ? "" : v.getValue().note()));

        flowTable.getColumns().setAll(colFrom, colTo, colValue, colNote);
    }

    private String endpointText(Flow f, boolean from) {
        FlowKind kind = from ? f.fromKind() : f.toKind();
        Long invId = from ? f.fromInvestmentTypeId() : f.toInvestmentTypeId();

        if (kind == FlowKind.CASH) return "Dinheiro Livre";
        if (invId == null) return "Investimento"; // null-safe

        String name = typeNameCache.get(invId);
        return (name == null || name.isBlank()) ? "Investimento" : name;
    }

    private void rebuildTypeCache(List<InvestmentType> types) {
        typeNameCache.clear();
        for (InvestmentType t : types) {
            typeNameCache.put(t.id(), t.name());
        }
    }

    private void commitPendingEdits() {
        if (invTable.getEditingCell() != null) {
            invTable.edit(-1, null);
        }
        root.requestFocus();
    }

    private boolean ensureCanNavigate() {
        commitPendingEdits();
        if (!dirty) return true;

        boolean save = Dialogs.confirm(
                "Alterações não salvas",
                "Você tem alterações não salvas.\nDeseja salvar antes de trocar/carregar a data?"
        );
        if (save) {
            return saveDayInternal(false);
        }
        dirty = false;
        return true;
    }

    private void loadFor(LocalDate date) {
        if (date == null) return;

        commitPendingEdits();

        loading = true;
        try {
            List<InvestmentType> types = daily.listTypes();
            rebuildTypeCache(types);

            DailyEntry entry = daily.loadEntry(date);

            cashField.setText(entry.cashCents() == 0 ? "" : Money.centsToCurrencyText(entry.cashCents()));

            invRows.clear();
            if (types.isEmpty()) {
                invTable.setPlaceholder(new Label("Nenhum tipo criado. Vá em 'Tipos de Investimento'."));
            } else {
                invTable.setPlaceholder(new Label("Digite os valores do dia e salve."));
                for (InvestmentType t : types) {
                    long v = entry.investmentValuesCents().getOrDefault(t.id(), 0L);
                    invRows.add(new InvestmentValueRow(t, v));
                }
            }

            flowItems.setAll(daily.flowsFor(date));

            dirty = false;
        } catch (Exception ex) {
            Dialogs.error(ex);
        } finally {
            loading = false;
        }
    }

    private void copyYesterday() {
        commitPendingEdits();
        try {
            LocalDate d = datePicker.getValue();
            if (d == null) return;

            DailyEntry prev = daily.loadEntry(d.minusDays(1));

            cashField.setText(prev.cashCents() == 0 ? "" : Money.centsToCurrencyText(prev.cashCents()));

            Map<Long, Long> prevMap = prev.investmentValuesCents();
            for (InvestmentValueRow row : invRows) {
                row.setValueCents(prevMap.getOrDefault(row.getType().id(), 0L));
            }
            invTable.refresh();
            dirty = true;
        } catch (Exception ex) {
            Dialogs.error(ex);
        }
    }

    private void saveDay() {
        commitPendingEdits();
        saveDayInternal(true);
    }

    private boolean saveDayInternal(boolean showInfo) {
        try {
            LocalDate date = datePicker.getValue();
            if (date == null) return false;

            long cash = Money.textToCentsOrZero(cashField.getText());
            if (cash < 0) { Dialogs.error("Dinheiro livre inválido."); return false; }

            Map<Long, Long> inv = new HashMap<>();
            for (InvestmentValueRow row : invRows) inv.put(row.getType().id(), Math.max(0, row.getValueCents()));

            daily.saveEntry(new DailyEntry(date, cash, inv));

            dirty = false;
            if (showInfo) Dialogs.info("Dia salvo.");
            return true;
        } catch (Exception ex) {
            Dialogs.error(ex);
            return false;
        }
    }

    private void openFlowDialog() {
        commitPendingEdits();

        LocalDate date = datePicker.getValue();
        if (date == null) return;

        var types = daily.listTypes();
        rebuildTypeCache(types);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Movimentação do dia");
        dialog.setHeaderText("Transferência / Aporte / Retirada (não é lucro)");

        ButtonType ok = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        ComboBox<String> fromKind = new ComboBox<>(FXCollections.observableArrayList("Dinheiro Livre", "Investimento"));
        ComboBox<String> toKind = new ComboBox<>(FXCollections.observableArrayList("Dinheiro Livre", "Investimento"));
        fromKind.getSelectionModel().select(0);
        toKind.getSelectionModel().select(1);

        ComboBox<InvestmentType> fromInv = new ComboBox<>(FXCollections.observableArrayList(types));
        ComboBox<InvestmentType> toInv = new ComboBox<>(FXCollections.observableArrayList(types));
        FxConverters.applyInvestmentTypeRenderer(fromInv);
        FxConverters.applyInvestmentTypeRenderer(toInv);

        fromInv.setDisable(true);
        toInv.setDisable(false);

        TextField value = new TextField();
        value.setTextFormatter(Money.currencyFormatterEditable());
        value.setPromptText("R$ 0,00");
        Money.applyCurrencyFormatOnBlur(value);

        TextField note = new TextField();
        note.setPromptText("Obs (opcional)");

        fromKind.setOnAction(e -> fromInv.setDisable(!"Investimento".equals(fromKind.getValue())));
        toKind.setOnAction(e -> toInv.setDisable(!"Investimento".equals(toKind.getValue())));

        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("De:"), fromKind, fromInv);
        gp.addRow(1, new Label("Para:"), toKind, toInv);
        gp.addRow(2, new Label("Valor:"), value);
        gp.addRow(3, new Label("Obs:"), note);

        dialog.getDialogPane().setContent(gp);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ok) return;
            try {
                long cents = Money.textToCentsOrZero(value.getText());
                if (cents <= 0) throw new IllegalArgumentException("Valor deve ser > 0.");

                FlowKind fk = "Investimento".equals(fromKind.getValue()) ? FlowKind.INVESTMENT : FlowKind.CASH;
                FlowKind tk = "Investimento".equals(toKind.getValue()) ? FlowKind.INVESTMENT : FlowKind.CASH;

                Long fId = fk == FlowKind.INVESTMENT ? Objects.requireNonNull(fromInv.getValue(), "Selecione origem.").id() : null;
                Long tId = tk == FlowKind.INVESTMENT ? Objects.requireNonNull(toInv.getValue(), "Selecione destino.").id() : null;

                if (fk == tk && Objects.equals(fId, tId)) throw new IllegalArgumentException("Origem e destino não podem ser iguais.");

                daily.addFlow(date, fk, fId, tk, tId, cents, note.getText());
                flowItems.setAll(daily.flowsFor(date));
                dirty = true;
            } catch (Exception ex) {
                Dialogs.error(ex);
            }
        });
    }

    private void deleteSelectedFlow() {
        Flow sel = flowTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!Dialogs.confirm("Excluir", "Excluir a movimentação selecionada?")) return;

        try {
            daily.deleteFlow(sel.id());
            flowItems.setAll(daily.flowsFor(datePicker.getValue()));
            dirty = true;
        } catch (Exception ex) {
            Dialogs.error(ex);
        }
    }
}
