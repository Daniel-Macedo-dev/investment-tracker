package com.daniel.ui;

import com.daniel.service.DailyService;
import com.daniel.ui.pages.*;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppShell {

    private final StackPane content = new StackPane();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Button> nav = new LinkedHashMap<>();

    private final DailyEntryPage dailyEntryPage;

    public AppShell(DailyService dailyService) {
        this.dailyEntryPage = new DailyEntryPage(dailyService);

        pages.put("Dashboard", new DashboardPage(dailyService));
        pages.put("Agenda", new AgendaPage(dailyService, this::goToDaily));
        pages.put("Registro Diário", dailyEntryPage);
        pages.put("Tipos de Investimento", new InvestmentTypesPage(dailyService));
        pages.put("Gráficos", new ChartsPage(dailyService));
        pages.put("Relatórios", new ReportsPage(dailyService));
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
        box.setPrefWidth(280);

        Label title = new Label("Investment Tracker");
        title.getStyleClass().add("sidebar-title");

        Label sub = new Label("Registro diário • lucro = (Δ) − fluxos");
        sub.getStyleClass().add("sidebar-sub");

        box.getChildren().addAll(title, sub, new Separator());

        for (String k : pages.keySet()) {
            Button b = new Button(k);
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("nav-btn");

            if ("Registro Diário".equals(k)) {
                b.setOnAction(e -> goToDaily(LocalDate.now()));
            } else {
                b.setOnAction(e -> go(k));
            }

            nav.put(k, b);
            box.getChildren().add(b);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("v0.4.0");
        footer.getStyleClass().add("sidebar-footer");

        box.getChildren().addAll(spacer, footer);
        return box;
    }

    public void go(String key) {
        Page p = pages.get(key);
        if (p == null) return;

        nav.values().forEach(b -> b.getStyleClass().remove("active"));
        if (nav.get(key) != null) nav.get(key).getStyleClass().add("active");

        Parent view = p.view();
        swapWithAnimation(view);

        p.onShow();
    }

    public void goToDaily(LocalDate date) {
        if (date == null) date = LocalDate.now();
        dailyEntryPage.setDate(date);
        go("Registro Diário");
    }

    private void swapWithAnimation(Node newNode) {
        if (content.getChildren().isEmpty()) {
            content.getChildren().setAll(newNode);
            return;
        }

        Node old = content.getChildren().get(0);

        FadeTransition fadeOut = new FadeTransition(javafx.util.Duration.millis(120), old);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        fadeOut.setOnFinished(ev -> {
            content.getChildren().setAll(newNode);

            newNode.setOpacity(0.0);
            newNode.setTranslateY(10);

            FadeTransition fadeIn = new FadeTransition(javafx.util.Duration.millis(180), newNode);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slide = new TranslateTransition(javafx.util.Duration.millis(180), newNode);
            slide.setFromY(10);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            fadeIn.play();
            slide.play();
        });

        fadeOut.play();
    }
}
