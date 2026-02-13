package com.daniel.ui.pages;

import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public final class ReportsPage implements Page {

    private final DailyService daily;
    private final VBox root = new VBox(12);

    private final ComboBox<YearMonth> month = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();
    private final Label totalProfit = new Label();

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ReportsPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Relatórios");
        h1.getStyleClass().add("h1");

        month.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(YearMonth ym) { return ym == null ? "" : ym.getMonthValue() + "/" + ym.getYear(); }
            @Override public YearMonth fromString(String s) { return null; }
        });

        Button refresh = new Button("Atualizar");
        refresh.setOnAction(e -> reload());

        HBox top = new HBox(10, new Label("Período:"), month, refresh, new Region(), new Label("Lucro do período:"), totalProfit);
        HBox.setHgrow(top.getChildren().get(3), Priority.ALWAYS);

        buildTable();

        root.getChildren().addAll(h1, top, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        month.setOnAction(e -> reload());
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        var now = YearMonth.now();
        month.setItems(FXCollections.observableArrayList(
                now.minusMonths(11), now.minusMonths(10), now.minusMonths(9), now.minusMonths(8),
                now.minusMonths(7), now.minusMonths(6), now.minusMonths(5), now.minusMonths(4),
                now.minusMonths(3), now.minusMonths(2), now.minusMonths(1), now
        ));
        month.getSelectionModel().select(now);
        reload();
    }

    private void buildTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        var colName = new TableColumn<Row, String>("Tipo");
        colName.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().name));

        var colProfit = new TableColumn<Row, String>("Lucro no período");
        colProfit.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().profit));

        var colBest = new TableColumn<Row, String>("Melhor dia");
        colBest.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().best));

        var colWorst = new TableColumn<Row, String>("Pior dia");
        colWorst.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().worst));

        table.getColumns().setAll(colName, colProfit, colBest, colWorst);
    }

    private void reload() {
        YearMonth ym = month.getValue();
        if (ym == null) return;

        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        var r = daily.rangeSummary(start, end);

        long total = r.totalProfitCents();
        totalProfit.setText((total >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(total)));

        var rows = FXCollections.<Row>observableArrayList();

        for (InvestmentType t : daily.listTypes()) {
            long p = r.profitByInvestmentCents().getOrDefault(t.id(), 0L);
            var ex = r.extremesByInvestment().get(t.id());

            String best = (ex != null && ex.bestDay() != null)
                    ? ex.bestDay().format(BR) + " (" + (ex.bestProfitCents() >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(ex.bestProfitCents())) + ")"
                    : "—";

            String worst = (ex != null && ex.worstDay() != null)
                    ? ex.worstDay().format(BR) + " (" + (ex.worstProfitCents() >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(ex.worstProfitCents())) + ")"
                    : "—";

            rows.add(new Row(t.name(), p, (p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p)), best, worst));
        }

        rows.sort(Comparator.comparingLong((Row rr) -> rr.profitCents).reversed());
        table.setItems(rows);
    }

    private static final class Row {
        final String name;
        final long profitCents;
        final String profit;
        final String best;
        final String worst;

        Row(String name, long profitCents, String profit, String best, String worst) {
            this.name = name;
            this.profitCents = profitCents;
            this.profit = profit;
            this.best = best;
            this.worst = worst;
        }
    }
}
