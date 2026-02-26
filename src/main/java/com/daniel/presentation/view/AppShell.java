package com.daniel.presentation.view;

import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.pages.*;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppShell {

    private final VBox contentWrap = new VBox();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, Label> navButtons = new LinkedHashMap<>();

    private final DailyEntryPage dailyEntryPage;
    private String currentPage = "dashboard";

    public AppShell(DailyTrackingUseCase dailyTrackingUseCase) {
        this.dailyEntryPage = new DailyEntryPage(dailyTrackingUseCase);

        pages.put("dashboard", new DashboardPage(dailyTrackingUseCase));
        pages.put("agenda", new AgendaPage(dailyTrackingUseCase, this::goToDaily));
        pages.put("daily-entry", dailyEntryPage);
        pages.put("investment-types", new InvestmentTypesPage(dailyTrackingUseCase));
        pages.put("charts", new ChartsPage(dailyTrackingUseCase));
        pages.put("reports", new ReportsPage(dailyTrackingUseCase));
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.setLeft(createSidebar());
        root.setCenter(contentWrap);

        contentWrap.getStyleClass().add("content-wrap");
        contentWrap.setFillWidth(true);
        VBox.setVgrow(contentWrap, Priority.ALWAYS);

        go("dashboard");
        return root;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);

        // Header
        Label titleLabel = new Label("Investment");
        titleLabel.getStyleClass().add("sidebar-title");

        Label subtitleLabel = new Label("Tracker");
        subtitleLabel.getStyleClass().add("accent-text");

        VBox headerBox = new VBox(titleLabel, subtitleLabel);
        headerBox.setSpacing(2);
        headerBox.setPadding(new Insets(0, 0, 20, 0));

        sidebar.getChildren().add(headerBox);

        // Navigation
        VBox navContainer = new VBox();
        navContainer.setSpacing(8);

        navContainer.getChildren().addAll(
                createNavButton("▣ Dashboard", "dashboard"),
                createNavButton("📅 Agenda", "agenda"),
                createNavButton("✎ Registro", "daily-entry"),
                createNavButton("◈ Tipos", "investment-types"),
                createNavButton("◉ Gráficos", "charts"),
                createNavButton("▤ Relatórios", "reports")
        );

        sidebar.getChildren().add(navContainer);

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        // Footer
        Label versionLabel = new Label("v1.0.0");
        versionLabel.getStyleClass().add("sidebar-footer");
        versionLabel.setPadding(new Insets(10, 0, 0, 0));
        sidebar.getChildren().add(versionLabel);

        return sidebar;
    }

    private Label createNavButton(String text, String pageKey) {
        Label btn = new Label(text);
        btn.getStyleClass().add("nav-btn");

        btn.setOnMouseClicked(e -> go(pageKey));

        navButtons.put(pageKey, btn);
        return btn;
    }

    public void go(String pageKey) {
        Page page = pages.get(pageKey);
        if (page == null) return;

        currentPage = pageKey;
        updateSidebarActive();

        Parent view = page.view();

        // Scroll to top if it's a ScrollPane
        if (view instanceof ScrollPane scrollPane) {
            scrollPane.setVvalue(0);
        }

        swapWithAnimation(view);
        page.onShow();
    }

    public void goToDaily(LocalDate date) {
        if (date == null) date = LocalDate.now();
        if (dailyEntryPage instanceof DailyEntryPage dep) {
            dep.setDate(date);
        }
        go("daily-entry");
    }

    private void updateSidebarActive() {
        for (Map.Entry<String, Label> entry : navButtons.entrySet()) {
            Label label = entry.getValue();
            if (entry.getKey().equals(currentPage)) {
                label.getStyleClass().add("active");
            } else {
                label.getStyleClass().remove("active");
            }
        }
    }

    private void swapWithAnimation(Node newNode) {
        if (contentWrap.getChildren().isEmpty()) {
            contentWrap.getChildren().setAll(newNode);
            return;
        }

        Node old = contentWrap.getChildren().get(0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(120), old);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        fadeOut.setOnFinished(ev -> {
            contentWrap.getChildren().setAll(newNode);

            newNode.setOpacity(0.0);
            newNode.setTranslateY(10);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), newNode);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slide = new TranslateTransition(Duration.millis(180), newNode);
            slide.setFromY(10);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            fadeIn.play();
            slide.play();
        });

        fadeOut.play();
    }
}