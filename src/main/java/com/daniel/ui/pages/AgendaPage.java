package com.daniel.ui.pages;

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

public final class AgendaPage implements Page {

    private final DailyService daily;
    private final java.util.function.Consumer<String> navigator;

    private final BorderPane root = new BorderPane();
    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AgendaPage(DailyService daily, java.util.function.Consumer<String> navigator) {
        this.daily = daily;
        this.navigator = navigator;

        root.setPadding(new Insets(16));
        root.setTop(top());
        root.setCenter(center());
        buildTable();
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        var now = YearMonth.now();
        monthPicker.setItems(FXCollections.observableArrayList(
                now.minusMonths(6), now.minusMonths(5), now.minusMonths(4),
                now.minusMonths(3), now.minusMonths(2), now.minusMonths(1),
                now, now.plusMonths(1)
        ));
        monthPicker.getSelectionModel().select(now);
        reload();
    }

    private Parent top() {
        Label h1 = new Label("Agenda");
        h1.getStyleClass().add("h1");

        monthPicker.setPrefWidth(160);
        monthPicker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(YearMonth ym) { return ym == null ? "" : ym.getMonthValue() + "/" + ym.getYear(); }
            @Override public YearMonth fromString(String s) { return null; }
        });

        Button openDaily = new Button("Abrir Registro Diário");
        openDaily.setOnAction(e -> navigator.accept("Registro Diário"));

        monthPicker.setOnAction(e -> reload());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, h1, new Label("Mês:"), monthPicker, spacer, openDaily);
    }

    private Parent center() {
        VBox box = new VBox(10);
        box.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void buildTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        var colDate = new TableColumn<Row, String>("Data");
        colDate.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().date.format(BR)));

        var colTotal = new TableColumn<Row, String>("Total do dia");
        colTotal.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().totalText));

        var colProfit = new TableColumn<Row, String>("Lucro/Prejuízo (mercado)");
        colProfit.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().profitText));

        table.getColumns().setAll(colDate, colTotal, colProfit);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Row item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-today","row-yesterday","row-empty");
                if (empty || item == null) return;

                if (!item.hasData) getStyleClass().add("row-empty");
                if (item.date.equals(LocalDate.now())) getStyleClass().add("row-today");
                if (item.date.equals(LocalDate.now().minusDays(1))) getStyleClass().add("row-yesterday");
            }
        });
    }

    private void reload() {
        var ym = monthPicker.getValue();
        if (ym == null) return;

        var items = FXCollections.<Row>observableArrayList();

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate d = ym.atDay(day);
            var s = daily.summaryFor(d);

            boolean has = daily.hasAnyDataPublic(d); // método novo no DailyService (patch abaixo)

            String total = has ? daily.brl(s.totalTodayCents()) : "—";
            long p = s.totalProfitTodayCents();
            String prof = has ? ((p >= 0 ? "+ " : "- ") + daily.brl(Math.abs(p))) : "—";

            items.add(new Row(d, total, prof, has));
        }

        table.setItems(items);
    }

    private static final class Row {
        final LocalDate date;
        final String totalText;
        final String profitText;
        final boolean hasData;

        Row(LocalDate date, String totalText, String profitText, boolean hasData) {
            this.date = date;
            this.totalText = totalText;
            this.profitText = profitText;
            this.hasData = hasData;
        }
    }
}
