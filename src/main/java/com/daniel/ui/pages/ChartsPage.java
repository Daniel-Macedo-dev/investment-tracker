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

        range.setItems(FXCollections.observableArrayList(30, 90, 180, 365));
        range.getSelectionModel().select(Integer.valueOf(90));

        picker.setPromptText("Selecione (ou Total)");
        picker.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(InvestmentType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        picker.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(InvestmentType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Selecione..." : item.name());
            }
        });

        x.setTickLabelRotation(45);
        x.setTickLabelGap(8);

        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.getStyleClass().add("chart-card");

        picker.setOnAction(e -> reload());
        range.setOnAction(e -> reload());

        HBox controls = new HBox(10,
                new Label("Tipo:"), picker,
                new Region(),
                new Label("Período:"), range
        );
        HBox.setHgrow(controls.getChildren().get(2), Priority.ALWAYS);

        root.getChildren().addAll(h1, controls, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        var types = new ArrayList<>(daily.listTypes());
        types.add(0, new InvestmentType(-1, "TOTAL (Cash + Investimentos)"));
        picker.setItems(FXCollections.observableArrayList(types));
        picker.getSelectionModel().select(0);
        reload();
    }

    private void reload() {
        InvestmentType sel = picker.getSelectionModel().getSelectedItem();
        Integer days = range.getValue();
        if (sel == null || days == null) return;

        chart.getData().clear();
        XYChart.Series<String, Number> s = new XYChart.Series<>();

        if (sel.id() == -1) {
            for (var p : daily.seriesTotalLastDays(days)) {
                s.getData().add(point(p.date().format(DMY), p.valueCents(), p.date().toString()));
            }
        } else {
            var all = daily.seriesForInvestment(sel.id());
            int from = Math.max(0, all.size() - days);
            for (int i = from; i < all.size(); i++) {
                var p = all.get(i);
                s.getData().add(point(p.date().format(DMY), p.valueCents(), p.date().toString()));
            }
        }

        chart.getData().add(s);
    }

    private XYChart.Data<String, Number> point(String label, long cents, String fullDate) {
        XYChart.Data<String, Number> d = new XYChart.Data<>(label, cents / 100.0);
        d.nodeProperty().addListener((obs, o, n) -> {
            if (n != null) {
                Tooltip.install(n, new Tooltip(fullDate + "\n" + daily.brl(cents)));
                n.getStyleClass().add("chart-point");
            }
        });
        return d;
    }
}
