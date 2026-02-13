package com.daniel.ui.pages;

import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.ArrayList;

public final class ChartsPage implements Page {

    private final DailyService daily;
    private final VBox root = new VBox(12);

    private final ComboBox<InvestmentType> picker = new ComboBox<>();
    private final CategoryAxis x = new CategoryAxis();
    private final NumberAxis y = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(x, y);

    public ChartsPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Gráficos");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

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

        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);

        picker.setOnAction(e -> reload());

        root.getChildren().addAll(h1, picker, chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
    }

    @Override
    public Parent view() { return root; }

    @Override
    public void onShow() {
        var types = new ArrayList<>(daily.listTypes());
        types.add(0, new InvestmentType(-1, "TOTAL (Cash + Investimentos)"));
        picker.setItems(FXCollections.observableArrayList(types));
        picker.getSelectionModel().select(0);
        reload();
    }

    private void reload() {
        InvestmentType sel = picker.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        chart.getData().clear();
        XYChart.Series<String, Number> s = new XYChart.Series<>();

        if (sel.id() == -1) {
            for (var p : daily.seriesTotalLastDays(60)) {
                s.getData().add(new XYChart.Data<>(p.date().toString(), p.valueCents() / 100.0));
            }
        } else {
            for (var p : daily.seriesForInvestment(sel.id())) {
                s.getData().add(new XYChart.Data<>(p.date().toString(), p.valueCents() / 100.0));
            }
        }

        chart.getData().add(s);
    }
}
