package com.daniel.ui;

import com.daniel.domain.Transaction;
import com.daniel.repository.TransactionRepository;
import com.daniel.service.CashService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MainView {

    private final long cashAccountId;
    private final TransactionRepository txRepo;
    private final CashService cashService;

    private final ObservableList<Transaction> txItems = FXCollections.observableArrayList();
    private final Label balanceLabel = new Label();

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public MainView(long cashAccountId, TransactionRepository txRepo, CashService cashService) {
        this.cashAccountId = cashAccountId;
        this.txRepo = txRepo;
        this.cashService = cashService;
    }

    public Node build() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        root.setTop(buildTopBar());
        root.setCenter(buildTable());

        refresh();
        return root;
    }

    private Node buildTopBar() {
        Label title = new Label("Dinheiro Livre");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        balanceLabel.setStyle("-fx-font-size: 16px;");

        Button depositBtn = new Button("+ Depósito");
        depositBtn.setOnAction(e -> onDeposit());

        Button withdrawBtn = new Button("- Retirada");
        withdrawBtn.setOnAction(e -> onWithdraw());

        VBox left = new VBox(6, title, balanceLabel);

        HBox right = new HBox(8, depositBtn, withdrawBtn);
        right.setPadding(new Insets(8, 0, 0, 0));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, left, spacer, right);
    }

    private Node buildTable() {
        TableView<Transaction> table = new TableView<>(txItems);

        TableColumn<Transaction, String> dateCol = new TableColumn<>("Data");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(dtf.format(c.getValue().createdAt())));
        dateCol.setPrefWidth(200);

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Tipo");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type().name()));
        typeCol.setPrefWidth(130);

        TableColumn<Transaction, String> amountCol = new TableColumn<>("Valor");
        amountCol.setCellValueFactory(c ->
                new SimpleStringProperty(brl.format(c.getValue().amountCents() / 100.0)));
        amountCol.setPrefWidth(160);

        TableColumn<Transaction, String> noteCol = new TableColumn<>("Observação");
        noteCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().note() == null ? "" : c.getValue().note()
        ));
        noteCol.setPrefWidth(420);

        table.getColumns().addAll(dateCol, typeCol, amountCol, noteCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    private void onDeposit() {
        Long cents = dialogsAskAmountCents("Novo depósito", "Valor do depósito (ex: 123,45)");
        if (cents == null) return;

        String note = dialogsAskNote("Observação (opcional)");
        try {
            cashService.deposit(cashAccountId, cents, note);
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onWithdraw() {
        Long cents = dialogsAskAmountCents("Nova retirada", "Valor da retirada (ex: 50,00)");
        if (cents == null) return;

        String note = dialogsAskNote("Observação (opcional)");
        try {
            cashService.withdraw(cashAccountId, cents, note);
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private Long dialogsAskAmountCents(String title, String header) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Valor:");

        return dialog.showAndWait()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(this::parseToCents)
                .orElse(null);
    }

    private String dialogsAskNote(String title) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText("Texto:");
        return dialog.showAndWait().map(String::trim).orElse(null);
    }

    private long parseToCents(String input) {
        // aceita: "123,45", "123.45", "1.234,56"
        String s = input.replace("R$", "").trim();

        if (s.contains(",") && s.contains(".")) {
            // pt-BR: "." milhar, "," decimal
            s = s.replace(".", "").replace(",", ".");
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }

        double value = Double.parseDouble(s);
        return Math.round(value * 100.0);
    }

    private void refresh() {
        long balanceCents = txRepo.computeCashBalanceCents(cashAccountId);
        balanceLabel.setText("Saldo: " + brl.format(balanceCents / 100.0));

        List<Transaction> list = txRepo.findAllByAccount(cashAccountId);
        txItems.setAll(list);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "Ocorreu um erro." : message);
        alert.showAndWait();
    }
}
