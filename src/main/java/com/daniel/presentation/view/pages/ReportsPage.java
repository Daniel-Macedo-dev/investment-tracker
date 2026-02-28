package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.Transaction;
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

    private final Label totalComprasLabel = new Label("—");
    private final Label totalVendasLabel = new Label("—");
    private final Label lucroRealizadoLabel = new Label("—");

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
                card("Total Compras", totalComprasLabel),
                card("Total Vendas", totalVendasLabel),
                card("Lucro Realizado", lucroRealizadoLabel)
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

        List<Transaction> transactions = daily.listTransactions(currentMonth);

        List<ExtractRow> rows = new ArrayList<>();
        long totalCompras = 0;
        long totalVendas = 0;

        for (Transaction tx : transactions) {
            boolean isBuy = Transaction.BUY.equals(tx.type());
            String type = isBuy ? "Compra" : "Venda";

            StringBuilder desc = new StringBuilder();
            desc.append(type).append(" de ").append(tx.name());
            if (tx.ticker() != null) {
                desc.append(" (").append(tx.ticker()).append(")");
            }
            if (tx.quantity() != null && tx.unitPriceCents() != null) {
                desc.append(" — ").append(tx.quantity()).append(" x ").append(daily.brl(tx.unitPriceCents()));
            }
            if (tx.note() != null) {
                desc.append(" | ").append(tx.note());
            }

            String value;
            if (isBuy) {
                value = "- " + daily.brl(tx.totalCents());
                totalCompras += tx.totalCents();
            } else {
                value = "+ " + daily.brl(tx.totalCents());
                totalVendas += tx.totalCents();
            }

            rows.add(new ExtractRow(tx.date(), type, desc.toString(), value));
        }

        table.setItems(FXCollections.observableArrayList(rows));

        totalComprasLabel.setText("- " + daily.brl(totalCompras));
        totalVendasLabel.setText("+ " + daily.brl(totalVendas));

        long lucro = totalVendas - totalCompras;
        lucroRealizadoLabel.setText((lucro >= 0 ? "+ " : "- ") + daily.brl(Math.abs(lucro)));

        totalComprasLabel.getStyleClass().removeAll("pos", "neg");
        totalComprasLabel.getStyleClass().add("neg");
        totalVendasLabel.getStyleClass().removeAll("pos", "neg");
        totalVendasLabel.getStyleClass().add("pos");
        lucroRealizadoLabel.getStyleClass().removeAll("pos", "neg");
        lucroRealizadoLabel.getStyleClass().add(lucro >= 0 ? "pos" : "neg");
    }

    private record ExtractRow(
            LocalDate date,
            String type,
            String description,
            String value
    ) {}
}