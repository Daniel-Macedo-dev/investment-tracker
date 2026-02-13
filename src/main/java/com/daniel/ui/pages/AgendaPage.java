package com.daniel.ui.pages;

import com.daniel.service.DailyService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class AgendaPage implements Page {

    private final DailyService daily;
    private final java.util.function.Consumer<LocalDate> openDailyAt;

    private final BorderPane root = new BorderPane();
    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AgendaPage(DailyService daily, java.util.function.Consumer<LocalDate> openDailyAt) {
        this.daily = daily;
        this.openDailyAt = openDailyAt;

        root.setPadding(new Insets(16));
        root.setTop(top());
        root.setCenter(center());
        buildTable();
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        YearMonth now = YearMonth.now();
        monthPicker.setItems(FXCollections.observableArrayList(
                now.minusMonths(6), now.minusMonths(5), now.minusMonths(4),
                now.minusMonths(3), now.minusMonths(2), now.minusMonths(1),
                now, now.plusMonths(1)
        ));
        if (monthPicker.getValue() == null) monthPicker.getSelectionModel().select(now);
        reload();
    }

    private Parent top() {
        Label h1 = new Label("Agenda");
        h1.getStyleClass().add("h1");

        monthPicker.setPrefWidth(160);
        monthPicker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(YearMonth ym) {
                return ym == null ? "" : String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
            }
            @Override public YearMonth fromString(String s) { return null; }
        });

        Button openToday = new Button("Abrir hoje");
        openToday.setOnAction(e -> openDailyAt.accept(LocalDate.now()));

        monthPicker.setOnAction(e -> reload());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, h1, new Label("Mês:"), monthPicker, spacer, openToday);
    }

    private Parent center() {
        VBox box = new VBox(10);
        box.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void buildTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Row, String> colDate = new TableColumn<>("Data");
        colDate.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().date.format(BR)));

        TableColumn<Row, String> colTotal = new TableColumn<>("Total do dia");
        colTotal.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().totalText));

        TableColumn<Row, String> colProfit = new TableColumn<>("Lucro/Prejuízo (mercado)");
        colProfit.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().profitText));

        table.getColumns().setAll(colDate, colTotal, colProfit);

        table.setRowFactory(tv -> {
            TableRow<Row> r = new TableRow<>() {
                @Override protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("row-today","row-yesterday","row-empty");
                    if (empty || item == null) return;

                    if (!item.hasData) getStyleClass().add("row-empty");
                    if (item.date.equals(LocalDate.now())) getStyleClass().add("row-today");
                    if (item.date.equals(LocalDate.now().minusDays(1))) getStyleClass().add("row-yesterday");
                }
            };

            r.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !r.isEmpty()) {
                    openDailyAt.accept(r.getItem().date);
                }
            });
            return r;
        });

        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Row sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) openDailyAt.accept(sel.date);
            }
        });
    }

    private void reload() {
        YearMonth ym = monthPicker.getValue();
        if (ym == null) return;

        var items = FXCollections.<Row>observableArrayList();

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate d = ym.atDay(day);
            boolean has = daily.hasAnyDataPublic(d);
            var s = daily.summaryFor(d);

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
