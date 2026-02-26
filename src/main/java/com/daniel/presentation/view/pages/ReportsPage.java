package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.components.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public final class ReportsPage implements Page {

    private final DailyTrackingUseCase daily;
    private final ScrollPane root;
    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();

    private final Label totalMonth = new Label("—");
    private final Label profitMonth = new Label("—");

    private final TableView<Row> table = new TableView<>();

    public ReportsPage(DailyTrackingUseCase daily) {
        this.daily = daily;

        VBox content = new VBox(12);

        // Header with title, subtitle, and month picker
        VBox headerSection = buildHeader();
        content.getChildren().add(headerSection);

        // KPI cards row
        HBox kpiRow = buildKpiRow();
        content.getChildren().add(kpiRow);

        // Performance table
        buildTable();
        content.getChildren().add(table);

        // Wrap in scroll pane
        root = UiComponents.pageScroll(content);

        // Event listener
        monthPicker.valueProperty().addListener((o, a, b) -> reload());
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        if (monthPicker.getValue() == null) {
            monthPicker.setValue(YearMonth.now());
        }
        reload();
    }

    private VBox buildHeader() {
        VBox header = UiComponents.pageHeader(
                "Relatórios",
                "Análise por mês (lucro de mercado e total do último dia com dado)."
        );

        // Month picker in a soft card
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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox monthRow = new HBox(10, new Label("Mês:"), monthPicker);
        VBox pickerCard = new VBox(monthRow);
        pickerCard.getStyleClass().add("card-soft");
        pickerCard.setPadding(new Insets(12));

        // Combine header and picker
        VBox full = new VBox(12, header, pickerCard);
        return full;
    }

    private HBox buildKpiRow() {
        VBox totalCard = UiComponents.simpleKpiCard("Total (último dia do mês)", totalMonth);
        VBox profitCard = UiComponents.simpleKpiCard("Lucro/Prejuízo do mês (mercado)", profitMonth);

        HBox row = new HBox(12, totalCard, profitCard);
        row.setPrefHeight(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(profitCard, Priority.ALWAYS);

        return row;
    }

    private void buildTable() {
        TableColumn<Row, String> invCol = new TableColumn<>("Investimento");
        invCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().name));

        TableColumn<Row, String> profitCol = new TableColumn<>("Lucro/Prejuízo (mês)");
        profitCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().profitText));

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

        // Set profit/loss for month
        profitMonth.setText(summary.totalProfitCents() == 0
                ? "—"
                : (summary.totalProfitCents() >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(summary.totalProfitCents())));

        // Apply styling to profit label
        UiComponents.styleProfitLabel(profitMonth, summary.totalProfitCents());

        // Find total for last day with data
        LocalDate last = null;
        for (LocalDate d = end; !d.isBefore(start); d = d.minusDays(1)) {
            if (daily.hasAnyDataPublic(d)) {
                last = d;
                break;
            }
        }
        if (last == null) {
            totalMonth.setText("—");
        } else {
            totalMonth.setText(daily.brl(daily.summaryFor(last).totalTodayCents()));
        }

        // Build table rows for each investment
        List<Row> rows = new ArrayList<>();
        for (InvestmentType t : daily.listTypes()) {
            long p = summary.profitByInvestmentCents().getOrDefault(t.id(), 0L);
            String txt = p == 0 ? "—" : ((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p)));
            rows.add(new Row(t.name(), txt, p));
        }
        table.setItems(FXCollections.observableArrayList(rows));
    }

    private static final class Row {
        final String name;
        final String profitText;
        final long profitCents;

        Row(String name, String profitText, long profitCents) {
            this.name = name;
            this.profitText = profitText;
            this.profitCents = profitCents;
        }
    }
}
