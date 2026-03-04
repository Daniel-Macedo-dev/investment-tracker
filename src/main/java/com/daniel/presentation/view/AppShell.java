package com.daniel.presentation.view;

import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.pages.*;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.Scene;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AppShell {

    private final StackPane content = new StackPane();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Button> nav = new LinkedHashMap<>();

    public AppShell(DailyTrackingUseCase dailyTrackingUseCase) {
        pages.put("Dashboard", new DashboardPage(dailyTrackingUseCase));
        pages.put("Cadastrar Investimento", new InvestmentTypesPage(dailyTrackingUseCase));
        pages.put("Diversificação", new DiversificationPage(dailyTrackingUseCase));
        pages.put("Simulação", new SimulationPage());
        pages.put("Extrato de Investimentos", new ReportsPage(dailyTrackingUseCase));
        pages.put("Configurações", new ConfiguracoesPage());
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        content.getStyleClass().add("content-host");
        content.setAlignment(Pos.TOP_LEFT);
        root.setLeft(sidebar());
        root.setCenter(content);

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyCompact(root, newScene.getWidth());
                newScene.widthProperty().addListener((o, old, w) ->
                        applyCompact(root, w.doubleValue()));
            }
        });

        go("Dashboard");
        return root;
    }

    private static void applyCompact(BorderPane root, double width) {
        if (width < 1100) root.getStyleClass().add("compact");
        else root.getStyleClass().remove("compact");
    }

    private Parent sidebar() {
        VBox box = new VBox();
        box.getStyleClass().add("sidebar");

        // ── Brand block ──────────────────────────────
        VBox brand = new VBox(4);
        brand.getStyleClass().add("sidebar-brand");

        Label title = new Label("Investment Tracker");
        title.getStyleClass().add("sidebar-title");

        Label sub = new Label("Controle de investimentos");
        sub.getStyleClass().add("sidebar-sub");

        brand.getChildren().addAll(title, sub);

        // ── Nav section ──────────────────────────────
        VBox navBox = new VBox(2);
        navBox.getStyleClass().add("sidebar-nav");

        String[] navOrder = {
            "Dashboard",
            "Cadastrar Investimento",
            "Diversificação",
            "Simulação",
            "Extrato de Investimentos"
        };

        String[] navLabels = {
            "  Dashboard",
            "  Carteira",
            "  Diversificação",
            "  Simulação",
            "  Extrato"
        };

        for (int i = 0; i < navOrder.length; i++) {
            String key = navOrder[i];
            Button b = new Button(navLabels[i]);
            b.getStyleClass().add("nav-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> go(key));
            nav.put(key, b);
            navBox.getChildren().add(b);
        }

        // ── Spacer ───────────────────────────────────
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ── Footer ───────────────────────────────────
        VBox footer = new VBox(4);
        footer.getStyleClass().add("sidebar-footer");

        Button configBtn = new Button("  Configurações");
        configBtn.getStyleClass().add("nav-btn");
        configBtn.setMaxWidth(Double.MAX_VALUE);
        configBtn.setOnAction(e -> go("Configurações"));
        nav.put("Configurações", configBtn);

        Label version = new Label("v0.5.0");
        version.getStyleClass().add("sidebar-footer-version");

        footer.getChildren().addAll(configBtn, version);

        box.getChildren().addAll(brand, navBox, spacer, footer);
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

    private void swapWithAnimation(Node newNode) {
        if (content.getChildren().isEmpty()) {
            content.getChildren().setAll(newNode);
            return;
        }

        Node old = content.getChildren().get(0);

        FadeTransition fadeOut = new FadeTransition(javafx.util.Duration.millis(100), old);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        fadeOut.setOnFinished(ev -> {
            content.getChildren().setAll(newNode);

            newNode.setOpacity(0.0);
            newNode.setTranslateY(8);

            FadeTransition fadeIn = new FadeTransition(javafx.util.Duration.millis(160), newNode);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slide = new TranslateTransition(javafx.util.Duration.millis(160), newNode);
            slide.setFromY(8);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            fadeIn.play();
            slide.play();
        });

        fadeOut.play();
    }
}
