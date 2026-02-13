package com.daniel.ui;

import com.daniel.service.DailyService;
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

    private final StackPane content = new StackPane();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Button> nav = new LinkedHashMap<>();

    public AppShell(DailyService dailyService) {
        pages.put("Dashboard", new DashboardPage(dailyService));
        pages.put("Registro Diário", new DailyEntryPage(dailyService));
        pages.put("Tipos de Investimento", new InvestmentTypesPage(dailyService));
        pages.put("Gráficos", new ChartsPage(dailyService));
        pages.put("Relatórios", new ReportsPage());
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.setLeft(sidebar());
        root.setCenter(content);
        go("Dashboard");
        return root;
    }

    private Parent sidebar() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sidebar");
        box.setPadding(new Insets(14));
        box.setPrefWidth(250);

        Label title = new Label("Investment Tracker");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label sub = new Label("Registro diário • Lucro por variação");
        sub.setStyle("-fx-opacity: 0.75;");

        box.getChildren().addAll(title, sub, new Separator());

        for (String k : pages.keySet()) {
            Button b = new Button(k);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> go(k));
            nav.put(k, b);
            box.getChildren().add(b);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("v0.2.0");
        footer.setStyle("-fx-opacity: 0.6;");
        box.getChildren().addAll(spacer, footer);
        return box;
    }

    private void go(String key) {
        Page p = pages.get(key);
        if (p == null) return;

        nav.values().forEach(b -> b.getStyleClass().remove("active"));
        nav.get(key).getStyleClass().add("active");

        content.getChildren().setAll(p.view());
        p.onShow();
    }
}
