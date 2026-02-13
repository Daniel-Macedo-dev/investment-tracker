package com.daniel.ui.pages;

import com.daniel.domain.*;
import com.daniel.service.DailyService;
import com.daniel.ui.Dialogs;
import com.daniel.ui.components.MoneyEditingCell;
import com.daniel.ui.model.InvestmentValueRow;
import com.daniel.util.FxConverters;
import com.daniel.util.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public final class DailyEntryPage implements Page {

    private final DailyService daily;

    private final BorderPane root = new BorderPane();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());

    private final TextField cashField = new TextField();

    // investimentos do dia (tabela)
    private final ObservableList<InvestmentValueRow> invRows = FXCollections.observableArrayList();
    private final TableView<InvestmentValueRow> invTable = new TableView<>(invRows);

    // fluxos do dia
    private final ObservableList<Flow> flowItems = FXCollections.observableArrayList();
    private final TableView<Flow> flowTable = new TableView<>(flowItems);

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public DailyEntryPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));
        root.setTop(topBar());
        root.setCenter(center());

        cashField.setTextFormatter(Money.currencyFormatter());
        cashField.setPromptText("0,00");

        buildInvTable();
        buildFlowTable();
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

        box.getChildren().add(card("Dinheiro Livre (valor total do dia)", cashField));
        box.getChildren().add(investCard());
        box.getChildren().add(flowCard());

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("app-scroll");
        return sp;
    }

    private VBox card(String title, Control body) {
        VBox v = new VBox(6);
        v.getStyleClass().add("card");
        v.getChildren().addAll(new Label(title), body);
        return v;
    }

    private VBox investCard() {
        VBox v = new VBox(10);
        v.getStyleClass().add("card");

        Label title = new Label("Investimentos (valor total de cada tipo no dia)");
        title.getStyleClass().add("card-title");

        Button goTypes = new Button("Criar tipos");
        goTypes.setOnAction(e -> Dialogs.info("Crie tipos em: Tipos de Investimento"));

        HBox header = new HBox(8, title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, goTypes);

        v.getChildren().addAll(header, invTable);
        invTable.setPrefHeight(220);
        return v;
    }

    private VBox flowCard() {
        VBox v = new VBox(10);
        v.getStyleClass().add("card");

        Label title = new Label("Movimentações do dia (NÃO contam como lucro)");
        title.getStyleClass().add("card-title");

        Button addFlow = new Button("+ Transferir / Aportar");
        addFlow.setOnAction(e -> openFlowDialog());

        Button delFlow = new Button("Excluir selecionado");
        delFlow.setOnAction(e -> deleteSelectedFlow());

        HBox actions = new HBox(8, addFlow, delFlow);

        v.getChildren().addAll(title, actions, flowTable);
        flowTable.setPrefHeight(260);
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
            InvestmentValueRow row = ev.getRowValue();
            long cents = Math.round(ev.getNewValue().doubleValue() * 100);
            row.setValueCents(Math.max(0, cents));
        });

        invTable.getColumns().setAll(colType, colValue);
    }

    private void buildFlowTable() {
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
    }

    private String flowEndpointText(Flow f, boolean from) {
        FlowKind kind = from ? f.fromKind() : f.toKind();
        Long invId = from ? f.fromInvestmentTypeId() : f.toInvestmentTypeId();

        if (kind == FlowKind.CASH) return "Dinheiro Livre";
        InvestmentType t = daily.listTypes().stream().filter(x -> x.id() == invId).findFirst().orElse(null);
        return t == null ? "Investimento" : t.name();
    }

    private void loadFor(LocalDate date) {
        List<InvestmentType> types = daily.listTypes();

        DailyEntry entry = daily.loadEntry(date);

        cashField.setText(entry.cashCents() == 0 ? "" : Money.centsToText(entry.cashCents()));

        invRows.clear();
        if (types.isEmpty()) {
            // tabela vazia, mas com UX clara
            invTable.setPlaceholder(new Label("Nenhum tipo criado. Vá em 'Tipos de Investimento' e crie os seus."));
        } else {
            invTable.setPlaceholder(new Label("Sem dados. Digite os valores do dia e salve."));
            for (InvestmentType t : types) {
                long v = entry.investmentValuesCents().getOrDefault(t.id(), 0L);
                invRows.add(new InvestmentValueRow(t, v));
            }
        }

        flowItems.setAll(daily.flowsFor(date));
    }

    private void copyYesterday() {
        LocalDate d = datePicker.getValue();
        LocalDate y = d.minusDays(1);

        DailyEntry prev = daily.loadEntry(y);

        cashField.setText(prev.cashCents() == 0 ? "" : Money.centsToText(prev.cashCents()));

        // copia por tipo (mantém linhas)
        Map<Long, Long> prevMap = prev.investmentValuesCents();
        for (InvestmentValueRow row : invRows) {
            long v = prevMap.getOrDefault(row.getType().id(), 0L);
            row.setValueCents(v);
        }

        invTable.refresh();
    }

    private void saveDay() {
        LocalDate date = datePicker.getValue();

        long cash = Money.textToCentsOrZero(cashField.getText());
        if (cash < 0) {
            Dialogs.error("Dinheiro livre inválido.");
            return;
        }

        Map<Long, Long> inv = new HashMap<>();
        for (InvestmentValueRow row : invRows) {
            inv.put(row.getType().id(), Math.max(0, row.getValueCents()));
        }

        try {
            daily.saveEntry(new DailyEntry(date, cash, inv));
            Dialogs.info("Dia salvo com sucesso.");
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
        fromInv.setConverter(FxConverters.investmentTypeConverter());
        toInv.setConverter(FxConverters.investmentTypeConverter());

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
                if (cents <= 0) throw new IllegalArgumentException("Valor deve ser > 0.");

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
