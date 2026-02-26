package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.components.UiComponents;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public final class ChartsPage implements Page {

    private final DailyTrackingUseCase daily;
    private final ScrollPane root;

    private final ComboBox<InvestmentType> picker = new ComboBox<>();
    private final ComboBox<Integer> range = new ComboBox<>();

    private final CategoryAxis x = new CategoryAxis();
    private final NumberAxis y = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(x, y);

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM");

    public ChartsPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        VBox content = new VBox(12);

        // Header with title and subtitle
        VBox header = UiComponents.pageHeader(
                "Gráficos",
                "Evolução do valor por investimento (janela configurável)."
        );
        content.getChildren().add(header);

        // Filters in a soft card
        picker.setItems(FXCollections.observableArrayList(daily.listTypes()));
        picker.setPromptText("Selecione um investimento...");

        range.setItems(FXCollections.observableArrayList(30, 60, 90, 180, 365));
        range.setValue(90);

        HBox filterRow = new HBox(12,
                new Label("Investimento:"), picker,
                new Label("Janela (dias):"), range
        );
        filterRow.setAlignment(Pos.CENTER_LEFT);

        VBox filterCard = new VBox(10, filterRow);
        filterCard.getStyleClass().add("card-soft");
        filterCard.setPadding(new Insets(12));

        content.getChildren().add(filterCard);

        // Chart in a big card
        chart.setAnimated(true);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(400);

        VBox chartCard = new VBox(chart);
        chartCard.getStyleClass().add("card");
        chartCard.setVgrow(chart, Priority.ALWAYS);

        content.getChildren().add(chartCard);

        // Wrap in scroll pane
        root = UiComponents.pageScroll(content);

        // Event listeners
        picker.valueProperty().addListener((o, a, b) -> reload());
        range.valueProperty().addListener((o, a, b) -> reload());
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        picker.setItems(FXCollections.observableArrayList(daily.listTypes()));
        if (!picker.getItems().isEmpty() && picker.getValue() == null) {
            picker.setValue(picker.getItems().get(0));
        }
        reload();
    }

    private void reload() {
        InvestmentType t = picker.getValue();
        if (t == null) {
            chart.getData().clear();
            return;
        }

        var points = daily.seriesForInvestment(t.id());
        int days = range.getValue() == null ? 90 : range.getValue();

        if (points.size() > days) {
            points = new ArrayList<>(points.subList(points.size() - days, points.size()));
        }

        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (var p : points) {
            var node = new XYChart.Data<String, Number>(DMY.format(p.date()), p.valueCents() / 100.0);
            s.getData().add(node);
        }

        chart.getData().setAll(s);

        // Install tooltips on data points
        for (var d : s.getData()) {
            d.nodeProperty().addListener((obs, oldN, n) -> {
                if (n != null) {
                    Tooltip.install(n, new Tooltip("R$ " + d.getYValue()));
                }
            });
        }
    }
}
