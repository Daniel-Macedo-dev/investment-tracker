package com.daniel.ui.pages;

import com.daniel.domain.Transaction;
import com.daniel.repository.AccountRepository;
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

public final class InvestmentsPage implements Page {

    private final long cashAccountId;
    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final CashService cashService;

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final BorderPane root = new BorderPane();
    private final Label totalLabel = new Label();

    private final ListView<AccountRepository.AccountRow> list = new ListView<>();
    private final ObservableList<Transaction> items = FXCollections.observableArrayList();
    private long selectedInvestmentId = -1;
    private final Label selectedTitle = new Label("Selecione um investimento");

    public InvestmentsPage(long cashAccountId,
                           AccountRepository accountRepo,
                           TransactionRepository txRepo,
                           CashService cashService) {
        this.cashAccountId = cashAccountId;
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.cashService = cashService;

        root.setPadding(new Insets(16));
        root.setTop(buildTop());
        root.setCenter(buildCenter());
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        refreshList();
        refreshTotal();
        if (selectedInvestmentId > 0) refreshTx();
    }

    private Parent buildTop() {
        Label h1 = new Label("Investimentos");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        totalLabel.setStyle("-fx-font-size: 16px;");

        Button create = new Button("+ Criar investimento");
        create.setOnAction(e -> onCreate());

        Button transfer = new Button("Transferir do Caixa");
        transfer.setOnAction(e -> onTransfer());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, new VBox(6, h1, totalLabel), spacer, new HBox(8, create, transfer));
    }

    private Parent buildCenter() {
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(AccountRepository.AccountRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                long bal = txRepo.balanceCents(item.id());
                setText(item.name() + "  •  " + brl.format(bal / 100.0));
            }
        });

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectedInvestmentId = newV.id();
                selectedTitle.setText(newV.name());
                refreshTx();
            }
        });

        TableView<Transaction> table = new TableView<>(items);

        TableColumn<Transaction, String> date = new TableColumn<>("Data");
        date.setCellValueFactory(v -> new SimpleStringProperty(dtf.format(v.getValue().createdAt())));
        date.setPrefWidth(190);

        TableColumn<Transaction, String> type = new TableColumn<>("Tipo");
        type.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().type().name()));
        type.setPrefWidth(120);

        TableColumn<Transaction, String> amount = new TableColumn<>("Valor");
        amount.setCellValueFactory(v ->
                new SimpleStringProperty(brl.format(v.getValue().amountCents() / 100.0))
        );
        amount.setPrefWidth(160);

        TableColumn<Transaction, String> note = new TableColumn<>("Obs");
        note.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().note() == null ? "" : v.getValue().note()));
        note.setPrefWidth(380);

        table.getColumns().addAll(date, type, amount, note);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        selectedTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        VBox right = new VBox(10, selectedTitle, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        SplitPane split = new SplitPane(list, right);
        split.setDividerPositions(0.32);
        return split;
    }

    private void onCreate() {
        String name = Dialogs.askText("Novo investimento", "Nome (ex: Ouro, Previdência, LCI):");
        if (name == null || name.isBlank()) return;

        try {
            accountRepo.createInvestmentAccount(name);
            refreshList();
            refreshTotal();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void onTransfer() {
        if (selectedInvestmentId <= 0) {
            Dialogs.error("Selecione um investimento na lista.");
            return;
        }

        Long cents = Dialogs.askAmountCents("Transferência", "Valor do Caixa → Investimento");
        if (cents == null) return;

        String note = Dialogs.askText("Observação", "Texto (opcional):");
        try {
            cashService.transfer(cashAccountId, selectedInvestmentId, cents, note);
            refreshList();
            refreshTotal();
            refreshTx();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void refreshList() {
        var all = accountRepo.listInvestmentAccounts();
        list.getItems().setAll(all);
        if (selectedInvestmentId > 0) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id() == selectedInvestmentId) {
                    list.getSelectionModel().select(i);
                    return;
                }
            }
        }
        if (!all.isEmpty()) list.getSelectionModel().select(0);
    }

    private void refreshTotal() {
        long total = 0;
        for (var a : accountRepo.listInvestmentAccounts()) {
            total += txRepo.balanceCents(a.id());
        }
        totalLabel.setText("Total alocado: " + brl.format(total / 100.0));
    }

    private void refreshTx() {
        if (selectedInvestmentId <= 0) {
            items.clear();
            return;
        }
        items.setAll(txRepo.listForAccount(selectedInvestmentId));
    }
}
