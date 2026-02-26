package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.DailySummary;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.components.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

    private final ScrollPane root;
    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AgendaPage(DailyTrackingUseCase daily, java.util.function.Consumer<LocalDate> openDailyAt) {
        this.daily = daily;
        this.openDailyAt = openDailyAt;

        VBox content = new VBox(12);

        // Header with title, subtitle, and month picker
        VBox headerSection = buildHeader();
        content.getChildren().add(headerSection);

        // Tip card
        VBox tipCard = buildTipCard();
        content.getChildren().add(tipCard);

        // Table
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
                "Agenda",
                "Visão por dia do mês (duplo clique para abrir o registro)."
        );

        // Month picker in a soft card
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

        HBox monthRow = new HBox(10, new Label("Mês:"), monthPicker);
        VBox pickerCard = new VBox(monthRow);
        pickerCard.getStyleClass().add("card-soft");
        pickerCard.setPadding(new Insets(12));

        // Combine header and picker
        VBox full = new VBox(12, header, pickerCard);
        return full;
    }

    private VBox buildTipCard() {
        Label tipTitle = new Label("Dica");
        tipTitle.getStyleClass().add("card-title");

        Label tipText = new Label("Dê duplo clique em um dia para abrir o Registro Diário nessa data.");
        tipText.getStyleClass().add("muted");
        tipText.setWrapText(true);

        VBox card = new VBox(8, tipTitle, tipText);
        card.getStyleClass().add("card-soft");
        card.setPadding(new Insets(12));

        return card;
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

        // Configure row styling and interactions
        table.setRowFactory(tv -> {
            TableRow<Row> r = new TableRow<>() {
                @Override protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("row-today", "row-yesterday", "row-empty");
                    if (empty || item == null) return;

                    if (!item.hasData) {
                        getStyleClass().add("row-empty");
                    }
                    if (item.date.equals(LocalDate.now())) {
                        getStyleClass().add("row-today");
                    }
                    if (item.date.equals(LocalDate.now().minusDays(1))) {
                        getStyleClass().add("row-yesterday");
                    }
                }
            };

            // Handle double-click to open daily entry
            r.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !r.isEmpty()) {
                    openDailyAt.accept(r.getItem().date);
                }
            });
            return r;
        });

        // Handle Enter key to open daily entry
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Row sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    openDailyAt.accept(sel.date);
                }
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
