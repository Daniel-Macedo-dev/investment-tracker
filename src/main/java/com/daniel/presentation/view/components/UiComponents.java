package com.daniel.presentation.view.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

/**
 * Reusable UI component factory for the dark premium theme.
 * Centralizes the creation of common visual elements.
 */
public final class UiComponents {

    private UiComponents() {}

    // ── Page scaffold ──────────────────────────────────────

    /**
     * Wraps page content in a ScrollPane with consistent styling.
     */
    public static ScrollPane pageScroll(VBox content) {
        content.setPadding(new Insets(24));
        content.setSpacing(20);
        content.getStyleClass().add("page");

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().addAll("scroll-pane", "page");
        sp.setStyle("-fx-background-color: transparent;");
        return sp;
    }

    /**
     * Page header with title + subtitle.
     */
    public static VBox pageHeader(String title, String subtitle) {
        Label h1 = new Label(title);
        h1.getStyleClass().add("h1");

        Label sub = new Label(subtitle);
        sub.getStyleClass().add("muted");

        VBox header = new VBox(4, h1, sub);
        header.getStyleClass().add("page-header");
        return header;
    }

    /**
     * Page header with title + subtitle + right-side actions.
     */
    public static HBox pageHeaderWithActions(String title, String subtitle, Region... actions) {
        VBox left = pageHeader(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(left);
        row.getChildren().add(spacer);
        for (Region a : actions) row.getChildren().add(a);
        return row;
    }

    // ── KPI / Stats Card ───────────────────────────────────

    /**
     * Creates a KPI card with icon, label, and big value.
     * @param iconChar Unicode char for the icon (e.g. "$", "↑")
     * @param iconBoxClass CSS class for the icon box (kpi-icon-box, kpi-icon-box-blue, etc.)
     * @param labelText Description label
     * @param valueLabel Label instance to be populated externally
     */
    public static VBox kpiCard(String iconChar, String iconBoxClass, String labelText, Label valueLabel) {
        Label icon = new Label(iconChar);
        icon.setStyle("-fx-font-size: 16px; -fx-text-fill: #F5F7FB;");

        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().add(iconBoxClass);

        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("card-label");

        valueLabel.getStyleClass().add("big-value");

        VBox textBox = new VBox(2, lbl, valueLabel);

        HBox top = new HBox(12, iconBox, textBox);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(top);
        card.getStyleClass().add("card-kpi");
        return card;
    }

    /**
     * Simple KPI card with just label and value (no icon).
     */
    public static VBox simpleKpiCard(String labelText, Label valueLabel) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("card-label");

        valueLabel.getStyleClass().add("big-value");

        VBox card = new VBox(6, lbl, valueLabel);
        card.getStyleClass().add("card-kpi");
        return card;
    }

    // ── Section Title ──────────────────────────────────────

    public static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    public static HBox sectionTitleWithAction(String text, Region action) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, l, spacer, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Empty State ────────────────────────────────────────

    public static VBox emptyState(String iconChar, String message) {
        Label icon = new Label(iconChar);
        icon.getStyleClass().add("empty-state-icon");

        Label text = new Label(message);
        text.getStyleClass().add("empty-state-text");
        text.setWrapText(true);

        VBox box = new VBox(12, icon, text);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        return box;
    }

    // ── Chip / Badge ───────────────────────────────────────

    public static Label chip(String text, String colorClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("chip", colorClass);
        return l;
    }

    public static Label badge(String text, String badgeClass) {
        Label l = new Label(text);
        l.getStyleClass().add(badgeClass);
        return l;
    }

    // ── Progress Card ──────────────────────────────────────

    /**
     * Creates a progress card with label, value text, and progress bar.
     */
    public static VBox progressCard(String title, String valueText, double progress) {
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("card-title");

        Label valueLbl = new Label(valueText);
        valueLbl.getStyleClass().add("big-value");

        ProgressBar bar = new ProgressBar(Math.min(1.0, Math.max(0.0, progress)));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(8);

        String pctText = String.format("%.0f%%", progress * 100);
        Label pctLbl = new Label(pctText);
        pctLbl.getStyleClass().add("muted");

        HBox bottomRow = new HBox();
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        bottomRow.getChildren().addAll(valueLbl, sp, pctLbl);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, titleLbl, bar, bottomRow);
        card.getStyleClass().add("card");
        return card;
    }

    // ── List item row ──────────────────────────────────────

    /**
     * Creates a styled list-item row with left content and right content.
     */
    public static HBox listItemRow(Region left, Region right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, left, spacer, right);
        row.getStyleClass().add("list-item");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Helpers ────────────────────────────────────────────

    /**
     * Creates a grid of cards that fills width evenly.
     */
    public static GridPane cardGrid(int columns) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);

        for (int i = 0; i < columns; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setPercentWidth(100.0 / columns);
            grid.getColumnConstraints().add(cc);
        }

        return grid;
    }

    /**
     * Formats profit text with sign.
     */
    public static String profitText(long cents, java.util.function.LongFunction<String> formatter) {
        if (cents == 0) return "—";
        String sign = cents >= 0 ? "+ " : "- ";
        return sign + formatter.apply(Math.abs(cents));
    }

    /**
     * Applies profit styling to a label.
     */
    public static void styleProfitLabel(Label label, long cents) {
        label.getStyleClass().removeAll("pos", "neg", "muted");
        if (cents == 0) {
            label.getStyleClass().add("muted");
        } else {
            label.getStyleClass().add(cents > 0 ? "pos" : "neg");
        }
    }
}
