package com.daniel.ui.pages;

import com.daniel.repository.AccountRepository;
import com.daniel.repository.TransactionRepository;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.util.Locale;

public final class DashboardPage implements Page {

    private final long cashAccountId;
    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final Label cashLabel = new Label();
    private final Label investedLabel = new Label();
    private final VBox root = new VBox(12);

    public DashboardPage(long cashAccountId, AccountRepository accountRepo, TransactionRepository txRepo) {
        this.cashAccountId = cashAccountId;
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Dashboard");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        HBox cards = new HBox(12,
                card("Dinheiro Livre", cashLabel),
                card("Total em Investimentos", investedLabel)
        );

        Label hint = new Label("Crie investimentos e transfira dinheiro do Caixa para organizar por “caixinhas”.");
        hint.setStyle("-fx-opacity: 0.8;");

        root.getChildren().addAll(h1, cards, hint);
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        long cash = txRepo.balanceCents(cashAccountId);

        long totalInvested = 0;
        for (var a : accountRepo.listInvestmentAccounts()) {
            totalInvested += txRepo.balanceCents(a.id());
        }

        cashLabel.setText(brl.format(cash / 100.0));
        investedLabel.setText(brl.format(totalInvested / 100.0));
    }

    private Pane card(String title, Label value) {
        VBox box = new VBox(6);
        box.getStyleClass().add("card");
        box.setPrefWidth(360);

        Label t = new Label(title);
        t.setStyle("-fx-opacity: 0.85;");

        value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        box.getChildren().addAll(t, value);
        return box;
    }
}
