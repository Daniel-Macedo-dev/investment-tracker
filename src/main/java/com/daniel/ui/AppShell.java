package com.daniel.ui;

import com.daniel.repository.AccountRepository;
import com.daniel.repository.TransactionRepository;
import com.daniel.service.CashService;
import com.daniel.ui.pages.*;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AppShell {

    private final long cashAccountId;
    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final CashService cashService;

    private final StackPane content = new StackPane();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    public AppShell(long cashAccountId,
                    AccountRepository accountRepo,
                    TransactionRepository txRepo,
                    CashService cashService) {
        this.cashAccountId = cashAccountId;
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.cashService = cashService;
    }

    public Parent build() {
        pages.put("Dashboard", new DashboardPage(cashAccountId, accountRepo, txRepo));
        pages.put("Dinheiro Livre", new CashPage(cashAccountId, txRepo, cashService));
        pages.put("Investimentos", new InvestmentsPage(cashAccountId, accountRepo, txRepo, cashService));
        pages.put("Relatórios", new ReportsPage());

        BorderPane root = new BorderPane();
        root.setLeft(buildSidebar());
        root.setCenter(content);

        navigateTo("Dashboard");
        return root;
    }

    private Parent buildSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(14));
        sidebar.setPrefWidth(230);

        Label title = new Label("Investment Tracker");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("Local • Dark • JavaFX");
        subtitle.setStyle("-fx-opacity: 0.75;");

        sidebar.getChildren().addAll(title, subtitle, new Separator());

        for (String key : pages.keySet()) {
            Button b = new Button(key);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> navigateTo(key));
            navButtons.put(key, b);
            sidebar.getChildren().add(b);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("v0.1.0");
        footer.setStyle("-fx-opacity: 0.6;");

        sidebar.getChildren().addAll(spacer, footer);
        return sidebar;
    }

    private void navigateTo(String key) {
        Page p = pages.get(key);
        if (p == null) return;
        navButtons.values().forEach(b -> b.getStyleClass().remove("active"));
        Button active = navButtons.get(key);
        if (active != null) active.getStyleClass().add("active");

        content.getChildren().setAll(p.view());
        p.onShow();
    }
}
