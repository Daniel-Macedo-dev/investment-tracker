package com.daniel.ui.pages;

import com.daniel.domain.Transaction;
import com.daniel.domain.TransactionType;
import com.daniel.repository.TransactionRepository;
import com.daniel.service.CashService;
import com.daniel.ui.Dialogs;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class CashPage implements Page {

    private final long cashAccountId;
    private final TransactionRepository txRepo;
    private final CashService cashService;

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ObservableList<Transaction> items = FXCollections.observableArrayList();
    private final Label balanceLabel = new Label();
    private final BorderPane root = new BorderPane();

    public CashPage(long cashAccountId, TransactionRepository txRepo, CashService cashService) {
        this.cashAccountId = cashAccountId;
        this.txRepo = txRepo;
        this.cashService = cashService;

        root.setPadding(new Insets(16));
        root.setTop(buildTop());
        root.setCenter(buildTable());
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        refresh();
    }

    private Parent buildTop() {
        Label h1 = new Label("Dinheiro Livre");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        balanceLabel.setStyle("-fx-font-size: 16px;");

        Button deposit = new Button("+ Depósito");
        deposit.setOnAction(e -> onDeposit());

        Button withdraw = new Button("- Retirada");
        withdraw.setOnAction(e -> onWithdraw());

        HBox actions = new HBox(8, deposit, withdraw);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, new VBox(6, h1, balanceLabel), spacer, actions);
    }

    private Parent buildTable() {
        TableView<Transaction> table = new TableView<>(items);

        TableColumn<Transaction, String> date = new TableColumn<>("Data");
        date.setCellValueFactory(v -> new SimpleStringProperty(dtf.format(v.getValue().createdAt())));
        date.setPrefWidth(190);

        TableColumn<Transaction, String> type = new TableColumn<>("Tipo");
        type.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().type().name()));
        type.setPrefWidth(120);

        TableColumn<Transaction, String> amount = new TableColumn<>("Valor");
        amount.setCellValueFactory(v -> {
            Transaction t = v.getValue();
            boolean positive = (t.type() == TransactionType.DEPOSIT)
                    || (t.type() == TransactionType.TRANSFER && t.toAccountId() != null && t.toAccountId() == cashAccountId);
            String s = (positive ? "+ " : "- ") + brl.format(t.amountCents() / 100.0);
            return new SimpleStringProperty(s);
        });
        amount.setCellFactory(col -> signedCell());
        amount.setPrefWidth(170);

        TableColumn<Transaction, String> note = new TableColumn<>("Obs");
        note.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().note() == null ? "" : v.getValue().note()));
        note.setPrefWidth(420);

        table.getColumns().addAll(date, type, amount, note);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private TableCell<Transaction, String> signedCell() {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.trim().startsWith("+")) setStyle("-fx-text-fill: #1fbf72; -fx-font-weight: bold;");
                else setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
            }
        };
    }

    private void onDeposit() {
        Long cents = Dialogs.askAmountCents("Depósito", "Valor (ex: 123,45)");
        if (cents == null) return;

        String note = Dialogs.askText("Observação", "Texto (opcional):");
        try {
            cashService.deposit(cashAccountId, cents, note);
            refresh();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void onWithdraw() {
        Long cents = Dialogs.askAmountCents("Retirada", "Valor (ex: 50,00)");
        if (cents == null) return;

        String note = Dialogs.askText("Observação", "Texto (opcional):");
        try {
            cashService.withdraw(cashAccountId, cents, note);
            refresh();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void refresh() {
        long bal = txRepo.balanceCents(cashAccountId);
        balanceLabel.setText("Saldo: " + brl.format(bal / 100.0));
        items.setAll(txRepo.listForAccount(cashAccountId));
    }
}
