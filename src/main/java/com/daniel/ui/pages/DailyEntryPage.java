package com.daniel.ui.pages;

import com.daniel.domain.*;
import com.daniel.service.DailyService;
import com.daniel.ui.Dialogs;
import com.daniel.util.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public final class DailyEntryPage implements Page {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final DailyService daily;

    private final BorderPane root = new BorderPane();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());

    private final TextField cashField = new TextField();
    private final GridPane grid = new GridPane();
    private final Map<Long, TextField> fields = new HashMap<>();

    private final ObservableList<Flow> flowItems = FXCollections.observableArrayList();
    private final TableView<Flow> flowTable = new TableView<>(flowItems);

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public DailyEntryPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));
        root.setTop(topBar());
        root.setCenter(center());

        // formatter de moeda no cash
        cashField.setTextFormatter(Money.currencyFormatter());
        cashField.setPromptText("0,00");
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        loadFor(datePicker.getValue());
    }

    private Parent topBar() {
        Label h1 = new Label("Registro Diário");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Button load = new Button("Carregar");
        load.setOnAction(e -> loadFor(datePicker.getValue()));

        Button copyYesterday = new Button("Copiar ontem");
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

        VBox cashCard = card("Dinheiro Livre (valor total do dia)", cashField);

        VBox invCard = new VBox(10);
        invCard.getStyleClass().add("card");
        invCard.getChildren().addAll(
                new Label("Investimentos (valor total de cada tipo no dia)"),
                buildInvestGrid()
        );

        VBox flowCard = new VBox(10);
        flowCard.getStyleClass().add("card");

        HBox flowActions = new HBox(8);
        Button addFlow = new Button("+ Transferir / Aportar");
        addFlow.setOnAction(e -> openFlowDialog());

        Button delFlow = new Button("Excluir selecionado");
        delFlow.setOnAction(e -> deleteSelectedFlow());

        flowActions.getChildren().addAll(addFlow, delFlow);

        flowCard.getChildren().addAll(
                new Label("Movimentações do dia (NÃO contam como lucro)"),
                flowActions,
                buildFlowTable()
        );

        box.getChildren().addAll(cashCard, invCard, flowCard);
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent;");
        return sp;
    }

    private VBox card(String title, Control body) {
        VBox v = new VBox(6);
        v.getStyleClass().add("card");
        v.getChildren().addAll(new Label(title), body);
        return v;
    }

    private Parent buildInvestGrid() {
        grid.setHgap(12);
        grid.setVgap(10);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent;");
        return scroll;
    }

    private Parent buildFlowTable() {
        flowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Flow, String> colFrom = new TableColumn<>("De");
        colFrom.setCellValueFactory(v -> new SimpleStringProperty(flowEndpointText(v.getValue(), true)));

        TableColumn<Flow, String> colTo = new TableColumn<>("Para");
        colTo.setCellValueFactory(v -> new SimpleStringProperty(flowEndpointText(v.getValue(), false)));

        TableColumn<Flow, String> colValue = new TableColumn<>("Valor");
        colValue.setCellValueFactory(v -> new SimpleStringProperty(brl.format(v.getValue().amountCents() / 100.0)));

        TableColumn<Flow, String> colNote = new TableColumn<>("Obs");
        colNote.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().note() == null ? "" : v.getValue().note()));

        flowTable.getColumns().setAll(colFrom, colTo, colValue, colNote);
        VBox.setVgrow(flowTable, Priority.ALWAYS);
        flowTable.setPrefHeight(260);

        return flowTable;
    }

    private String flowEndpointText(Flow f, boolean from) {
        FlowKind kind = from ? f.fromKind() : f.toKind();
        Long invId = from ? f.fromInvestmentTypeId() : f.toInvestmentTypeId();

        if (kind == FlowKind.CASH) return "Dinheiro Livre";
        InvestmentType t = daily.listTypes().stream().filter(x -> x.id() == invId).findFirst().orElse(null);
        return t == null ? "Investimento" : t.name();
    }

    private void buildGrid(List<InvestmentType> types) {
        grid.getChildren().clear();
        fields.clear();

        if (types.isEmpty()) {
            Label empty = new Label("Você ainda não criou tipos de investimento.\nVá em \"Tipos de Investimento\" e crie os seus.");
            empty.setStyle("-fx-opacity: 0.9; -fx-padding: 10;");
            grid.add(empty, 0, 0);
            return;
        }

        int r = 0;
        for (InvestmentType t : types) {
            Label name = new Label(t.name());
            name.setStyle("-fx-font-weight: bold;");

            TextField value = new TextField();
            value.setPromptText("0,00");
            value.setTextFormatter(Money.currencyFormatter());

            // remove estado inválido quando digita
            value.textProperty().addListener((obs, oldV, newV) -> value.pseudoClassStateChanged(INVALID, false));

            fields.put(t.id(), value);
            grid.addRow(r++, name, value);
        }
    }

    private void loadFor(LocalDate date) {
        List<InvestmentType> types = daily.listTypes();
        buildGrid(types);

        DailyEntry entry = daily.loadEntry(date);

        cashField.setText(entry.cashCents() == 0 ? "" : Money.centsToText(entry.cashCents()));
        cashField.pseudoClassStateChanged(INVALID, false);

        for (InvestmentType t : types) {
            long v = entry.investmentValuesCents().getOrDefault(t.id(), 0L);
            TextField tf = fields.get(t.id());
            if (tf != null) {
                tf.setText(v == 0 ? "" : Money.centsToText(v));
                tf.pseudoClassStateChanged(INVALID, false);
            }
        }

        flowItems.setAll(daily.flowsFor(date));
    }

    private void copyYesterday() {
        LocalDate d = datePicker.getValue();
        LocalDate y = d.minusDays(1);

        DailyEntry prev = daily.loadEntry(y);

        cashField.setText(prev.cashCents() == 0 ? "" : Money.centsToText(prev.cashCents()));
        cashField.pseudoClassStateChanged(INVALID, false);

        for (var e : prev.investmentValuesCents().entrySet()) {
            TextField tf = fields.get(e.getKey());
            if (tf != null) {
                tf.setText(e.getValue() == 0 ? "" : Money.centsToText(e.getValue()));
                tf.pseudoClassStateChanged(INVALID, false);
            }
        }
    }

    private void saveDay() {
        LocalDate date = datePicker.getValue();

        long cash = Money.textToCentsOrZero(cashField.getText());
        cashField.pseudoClassStateChanged(INVALID, cash < 0);

        Map<Long, Long> inv = new HashMap<>();
        boolean hasInvalid = (cash < 0);

        for (var e : fields.entrySet()) {
            TextField tf = e.getValue();
            long val = Money.textToCentsOrZero(tf.getText());

            boolean invalid = val < 0;
            tf.pseudoClassStateChanged(INVALID, invalid);
            if (invalid) hasInvalid = true;

            inv.put(e.getKey(), val);
        }

        if (hasInvalid) {
            Dialogs.error("Existem valores inválidos (negativos). Corrija os campos marcados.");
            return;
        }

        try {
            daily.saveEntry(new DailyEntry(date, cash, inv));
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void openFlowDialog() {
        LocalDate date = datePicker.getValue();
        var types = daily.listTypes();

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
        fromInv.setDisable(true);
        toInv.setDisable(false);

        TextField value = new TextField();
        value.setPromptText("0,00");
        value.setTextFormatter(Money.currencyFormatter());

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
                if (cents <= 0) throw new IllegalArgumentException("Valor deve ser maior que zero.");

                FlowKind fk = "Investimento".equals(fromKind.getValue()) ? FlowKind.INVESTMENT : FlowKind.CASH;
                FlowKind tk = "Investimento".equals(toKind.getValue()) ? FlowKind.INVESTMENT : FlowKind.CASH;

                Long fId = (fk == FlowKind.INVESTMENT)
                        ? Objects.requireNonNull(fromInv.getValue(), "Selecione o investimento de origem.").id()
                        : null;

                Long tId = (tk == FlowKind.INVESTMENT)
                        ? Objects.requireNonNull(toInv.getValue(), "Selecione o investimento de destino.").id()
                        : null;

                daily.addFlow(date, fk, fId, tk, tId, cents, note.getText());
                flowItems.setAll(daily.flowsFor(date));

            } catch (Exception ex) {
                Dialogs.error(ex.getMessage());
            }
        });
    }

    private void deleteSelectedFlow() {
        Flow sel = flowTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            daily.deleteFlow(sel.id());
            flowItems.setAll(daily.flowsFor(datePicker.getValue()));
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }
}
