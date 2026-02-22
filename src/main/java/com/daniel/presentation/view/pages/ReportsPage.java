package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.Flow;
import com.daniel.core.domain.entity.Enums.FlowKind;
import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.DailyTrackingUseCase;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ReportsPage implements Page {

    private final DailyTrackingUseCase daily;

    private final BorderPane root = new BorderPane();
    private final Button btnPrevMonth = new Button("◀");
    private final Button btnNextMonth = new Button("▶");
    private final Button btnCurrentMonth = new Button("Mês Atual");
    private final Label monthLabel = new Label();

    private final Label saldoInicialLabel = new Label("—");
    private final Label saldoFinalLabel = new Label("—");
    private final Label totalAportesLabel = new Label("—");
    private final Label totalSaquesLabel = new Label("—");

    private final TableView<ExtractRow> table = new TableView<>();

    private YearMonth currentMonth = YearMonth.now();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ReportsPage(DailyTrackingUseCase daily) {
        this.daily = daily;

        root.setPadding(new Insets(16));
        root.setTop(top());
        root.setCenter(center());

        buildTable();
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        currentMonth = YearMonth.now();
        reload();
    }

    private Parent top() {
        Label h1 = new Label("Extrato de Investimentos");
        h1.getStyleClass().add("h1");

        btnPrevMonth.getStyleClass().add("icon-btn");
        btnNextMonth.getStyleClass().add("icon-btn");
        btnCurrentMonth.getStyleClass().add("ghost-btn");

        btnPrevMonth.setOnAction(e -> {
            currentMonth = currentMonth.minusMonths(1);
            reload();
        });

        btnNextMonth.setOnAction(e -> {
            currentMonth = currentMonth.plusMonths(1);
            reload();
        });

        btnCurrentMonth.setOnAction(e -> {
            currentMonth = YearMonth.now();
            reload();
        });

        monthLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox nav = new HBox(8, btnPrevMonth, btnNextMonth, new Separator(), btnCurrentMonth);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, h1, spacer, monthLabel, nav);
        bar.getStyleClass().add("header-row");
        return bar;
    }

    private Parent center() {
        VBox box = new VBox(12);

        // Cards de resumo
        HBox cards = new HBox(12);
        cards.getChildren().addAll(
                card("Saldo Inicial", saldoInicialLabel),
                card("Total Aportes", totalAportesLabel),
                card("Total Saques", totalSaquesLabel),
                card("Saldo Final", saldoFinalLabel)
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
        value.setStyle("-fx-font-size: 16px;");
        b.getChildren().addAll(t, value);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private void buildTable() {
        table.getStyleClass().add("table");

        TableColumn<ExtractRow, String> dateCol = new TableColumn<>("Data");
        dateCol.setCellValueFactory(v -> new SimpleStringProperty(DATE_FMT.format(v.getValue().date)));
        dateCol.setPrefWidth(120);

        TableColumn<ExtractRow, String> typeCol = new TableColumn<>("Tipo");
        typeCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().type));
        typeCol.setPrefWidth(150);

        TableColumn<ExtractRow, String> descCol = new TableColumn<>("Descrição");
        descCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().description));

        TableColumn<ExtractRow, String> valueCol = new TableColumn<>("Valor");
        valueCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().value));
        valueCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("pos", "neg");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        getStyleClass().add("pos");
                    } else if (item.startsWith("-")) {
                        getStyleClass().add("neg");
                    }
                }
            }
        });
        valueCol.setPrefWidth(150);

        table.getColumns().setAll(dateCol, typeCol, descCol, valueCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void reload() {
        monthLabel.setText(currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("pt", "BR"))));

        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();

        // Buscar saldo inicial (último dia do mês anterior)
        LocalDate prevMonthEnd = start.minusDays(1);
        long saldoInicial = 0;
        if (daily.hasAnyDataPublic(prevMonthEnd)) {
            var prevSummary = daily.summaryFor(prevMonthEnd);
            saldoInicial = prevSummary.totalTodayCents();
        }

        // Buscar saldo final (último dia do mês atual)
        long saldoFinal = 0;
        if (daily.hasAnyDataPublic(end)) {
            var endSummary = daily.summaryFor(end);
            saldoFinal = endSummary.totalTodayCents();
        }

        // Coletar todos os fluxos do mês
        List<ExtractRow> rows = new ArrayList<>();
        long totalAportes = 0;
        long totalSaques = 0;

        Map<Long, String> investmentNames = new HashMap<>();
        for (InvestmentType inv : daily.listTypes()) {
            investmentNames.put((long) inv.id(), inv.name());
        }

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<Flow> flows = daily.flowsFor(date);

            for (Flow flow : flows) {
                String type;
                String desc;
                String value;

                // Determinar tipo e descrição
                if (flow.fromKind() == FlowKind.CASH && flow.toKind() == FlowKind.INVESTMENT) {
                    type = "Aporte";
                    String invName = investmentNames.getOrDefault(flow.toInvestmentTypeId(), "Investimento");
                    desc = "Aporte em " + invName;
                    value = "+ " + daily.brl(flow.amountCents());
                    totalAportes += flow.amountCents();

                } else if (flow.fromKind() == FlowKind.INVESTMENT && flow.toKind() == FlowKind.CASH) {
                    type = "Saque";
                    String invName = investmentNames.getOrDefault(flow.fromInvestmentTypeId(), "Investimento");
                    desc = "Saque de " + invName;
                    value = "- " + daily.brl(flow.amountCents());
                    totalSaques += flow.amountCents();

                } else if (flow.fromKind() == FlowKind.INVESTMENT && flow.toKind() == FlowKind.INVESTMENT) {
                    type = "Transferência";
                    String fromName = investmentNames.getOrDefault(flow.fromInvestmentTypeId(), "Investimento");
                    String toName = investmentNames.getOrDefault(flow.toInvestmentTypeId(), "Investimento");
                    desc = "De " + fromName + " para " + toName;
                    value = daily.brl(flow.amountCents());
                } else {
                    type = "Outro";
                    desc = flow.note() != null ? flow.note() : "—";
                    value = daily.brl(flow.amountCents());
                }

                rows.add(new ExtractRow(date, type, desc, value));
            }
        }

        // Ordenar por data (mais recente primeiro)
        rows.sort((a, b) -> b.date.compareTo(a.date));

        // Atualizar tabela
        table.setItems(FXCollections.observableArrayList(rows));

        // Atualizar cards
        saldoInicialLabel.setText(daily.brl(saldoInicial));
        saldoFinalLabel.setText(daily.brl(saldoFinal));
        totalAportesLabel.setText("+ " + daily.brl(totalAportes));
        totalSaquesLabel.setText("- " + daily.brl(totalSaques));

        totalAportesLabel.getStyleClass().add("pos");
        totalSaquesLabel.getStyleClass().add("neg");
    }

    private record ExtractRow(
            LocalDate date,
            String type,
            String description,
            String value
    ) {}
}