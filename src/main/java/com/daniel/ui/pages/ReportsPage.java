package com.daniel.ui.pages;

import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public final class ReportsPage implements Page {

    private final DailyService daily;

    private final BorderPane root = new BorderPane();
    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();

    private final Label totalMonth = new Label("—");
    private final Label profitMonth = new Label("—");

    private final TableView<Row> table = new TableView<>();

    public ReportsPage(DailyService daily) {
        this.daily = daily;

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
        Label h1 = new Label("Relatórios");
        h1.getStyleClass().add("h1");

        monthPicker.setItems(FXCollections.observableArrayList(
                YearMonth.now().minusMonths(6),
                YearMonth.now().minusMonths(5),
                YearMonth.now().minusMonths(4),
                YearMonth.now().minusMonths(3),
                YearMonth.now().minusMonths(2),
                YearMonth.now().minusMonths(1),
                YearMonth.now()
        ));
        monthPicker.setValue(YearMonth.now());
        monthPicker.valueProperty().addListener((o,a,b) -> reload());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, h1, spacer, new Label("Mês:"), monthPicker);
        bar.getStyleClass().add("header-row");
        return bar;
    }

    private Parent center() {
        VBox box = new VBox(12);

        HBox cards = new HBox(12,
                card("Total (último dia do mês)", totalMonth),
                card("Lucro/Prejuízo do mês (mercado)", profitMonth)
        );

        box.getChildren().addAll(cards, table);
        return box;
    }

    private VBox card(String title, Label value) {
        VBox b = new VBox(6);
        b.getStyleClass().add("card");
        Label t = new Label(title);
        t.getStyleClass().add("muted");
        value.getStyleClass().add("big-value");
        b.getChildren().addAll(t, value);
        return b;
    }

    private void buildTable() {
        TableColumn<Row, String> invCol = new TableColumn<>("Investimento");
        invCol.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(v.getValue().name));

        TableColumn<Row, String> profitCol = new TableColumn<>("Lucro/Prejuízo (mês)");
        profitCol.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(v.getValue().profitText));

        table.getColumns().setAll(invCol, profitCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("table");
    }

    private void reload() {
        YearMonth ym = monthPicker.getValue();
        if (ym == null) return;

        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        var summary = daily.rangeSummary(start, end);

        profitMonth.setText(summary.totalProfitCents() == 0
                ? "—"
                : (summary.totalProfitCents() >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(summary.totalProfitCents())));

        // total do último dia com dado
        LocalDate last = null;
        for (LocalDate d = end; !d.isBefore(start); d = d.minusDays(1)) {
            if (daily.hasAnyDataPublic(d)) { last = d; break; }
        }
        if (last == null) totalMonth.setText("—");
        else totalMonth.setText(daily.brl(daily.summaryFor(last).totalTodayCents()));

        List<Row> rows = new ArrayList<>();
        for (InvestmentType t : daily.listTypes()) {
            long p = summary.profitByInvestmentCents().getOrDefault(t.id(), 0L);
            String txt = p == 0 ? "—" : ((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p)));
            rows.add(new Row(t.name(), txt));
        }
        table.setItems(FXCollections.observableArrayList(rows));
    }

    private static final class Row {
        final String name;
        final String profitText;
        Row(String name, String profitText) {
            this.name = name;
            this.profitText = profitText;
        }
    }
}
