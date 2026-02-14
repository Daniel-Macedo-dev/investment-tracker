package com.daniel.ui.pages;

import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public final class ChartsPage implements Page {

    private final DailyService daily;
    private final VBox root = new VBox(12);

    private final ComboBox<InvestmentType> picker = new ComboBox<>();
    private final ComboBox<Integer> range = new ComboBox<>();

    private final CategoryAxis x = new CategoryAxis();
    private final NumberAxis y = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(x, y);

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM");

    public ChartsPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Gráficos");
        h1.getStyleClass().add("h1");

        picker.setItems(FXCollections.observableArrayList(daily.listTypes()));
        picker.setPromptText("Selecione um investimento...");

        range.setItems(FXCollections.observableArrayList(30, 60, 90, 180, 365));
        range.setValue(90);

        HBox top = new HBox(10, new Label("Investimento:"), picker, new Label("Janela (dias):"), range);
        top.getStyleClass().add("card");

        chart.setAnimated(true);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);

        root.getChildren().addAll(h1, top, chart);

        picker.valueProperty().addListener((o,a,b) -> reload());
        range.valueProperty().addListener((o,a,b) -> reload());
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        picker.setItems(FXCollections.observableArrayList(daily.listTypes()));
        if (!picker.getItems().isEmpty() && picker.getValue() == null) picker.setValue(picker.getItems().get(0));
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

        // corta pra janela final
        if (points.size() > days) points = new ArrayList<>(points.subList(points.size() - days, points.size()));

        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (var p : points) {
            var node = new XYChart.Data<String, Number>(DMY.format(p.date()), p.valueCents() / 100.0);
            s.getData().add(node);
        }

        chart.getData().setAll(s);

        // tooltip por ponto
        for (var d : s.getData()) {
            d.nodeProperty().addListener((obs, oldN, n) -> {
                if (n != null) {
                    Tooltip.install(n, new Tooltip("R$ " + d.getYValue()));
                }
            });
        }
    }
}
