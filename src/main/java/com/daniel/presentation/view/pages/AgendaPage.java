package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.DailySummary;
import com.daniel.core.service.DailyTrackingUseCase;
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

    private final DailyTrackingUseCase daily;
    private final java.util.function.Consumer<LocalDate> openDailyAt;

    private final BorderPane root = new BorderPane();
    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AgendaPage(DailyTrackingUseCase daily, java.util.function.Consumer<LocalDate> openDailyAt) {
        this.daily = daily;
        this.openDailyAt = openDailyAt;

        root.setPadding(new Insets(16));
        root.setTop(top());
        root.setCenter(center());
        buildTable();
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        if (monthPicker.getValue() == null) monthPicker.setValue(YearMonth.now());
        reload();
    }

    private Parent top() {
        Label h1 = new Label("Agenda");
        h1.getStyleClass().add("h1");

        monthPicker.getItems().setAll(
                YearMonth.now().minusMonths(6),
                YearMonth.now().minusMonths(5),
                YearMonth.now().minusMonths(4),
                YearMonth.now().minusMonths(3),
                YearMonth.now().minusMonths(2),
                YearMonth.now().minusMonths(1),
                YearMonth.now(),
                YearMonth.now().plusMonths(1),
                YearMonth.now().plusMonths(2)
        );
        monthPicker.setValue(YearMonth.now());
        monthPicker.valueProperty().addListener((o,a,b) -> reload());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, h1, spacer, new Label("Mês:"), monthPicker);
        bar.getStyleClass().add("header-row");
        return bar;
    }

    private Parent center() {
        VBox box = new VBox(10);
        Label tip = new Label("Dê duplo clique em um dia para abrir o Registro Diário nessa data.");
        tip.getStyleClass().add("muted");
        box.getChildren().addAll(tip, table);
        return box;
    }

    private void buildTable() {
        table.getStyleClass().add("table");

        TableColumn<Row, String> colDate = new TableColumn<>("Data");
        colDate.setCellValueFactory(v -> new SimpleStringProperty(BR.format(v.getValue().date)));

        TableColumn<Row, String> colTotal = new TableColumn<>("Total do dia");
        colTotal.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().totalText));

        TableColumn<Row, String> colProfit = new TableColumn<>("Lucro/Prejuízo (mercado)");
        colProfit.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().profitText));

        table.getColumns().setAll(colDate, colTotal, colProfit);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

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
                if (e.getClickCount() == 2 && !r.isEmpty()) openDailyAt.accept(r.getItem().date);
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

            String total = "—";
            String prof = "—";

            if (has) {
                DailySummary s = daily.summaryFor(d);
                total = daily.brl(s.totalTodayCents());

                long p = s.totalProfitTodayCents();
                prof = (p == 0) ? "—" : ((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p)));
            }

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
