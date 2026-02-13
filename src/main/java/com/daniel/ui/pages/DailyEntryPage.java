package com.daniel.ui.pages;

import com.daniel.domain.DailyEntry;
import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import com.daniel.ui.Dialogs;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DailyEntryPage implements Page {

    private final DailyService daily;

    private final BorderPane root = new BorderPane();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());

    private final TextField cashField = new TextField();
    private final GridPane grid = new GridPane();

    private final Map<Long, TextField> fields = new HashMap<>();

    public DailyEntryPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));
        root.setTop(topBar());
        root.setCenter(center());
    }

    @Override
    public Parent view() { return root; }

    @Override
    public void onShow() {
        loadFor(datePicker.getValue());
    }

    private Parent topBar() {
        Label h1 = new Label("Registro Diário");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Button load = new Button("Carregar");
        load.setOnAction(e -> loadFor(datePicker.getValue()));

        Button save = new Button("Salvar dia");
        save.setOnAction(e -> saveDay());

        HBox right = new HBox(8, load, save);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(12, h1, spacer, new Label("Data:"), datePicker, right);
    }

    private Parent center() {
        VBox box = new VBox(12);

        VBox cashCard = new VBox(6);
        cashCard.getStyleClass().add("card");
        cashCard.getChildren().addAll(new Label("Dinheiro Livre (valor total do dia)"), cashField);
        cashField.setPromptText("ex: 1200,50");

        grid.setHgap(10);
        grid.setVgap(10);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent;");

        VBox invCard = new VBox(10);
        invCard.getStyleClass().add("card");
        invCard.getChildren().addAll(new Label("Investimentos (valor total de cada tipo no dia)"), scroll);

        box.getChildren().addAll(cashCard, invCard);
        return box;
    }

    private void buildGrid(List<InvestmentType> types) {
        grid.getChildren().clear();
        fields.clear();

        int r = 0;
        for (InvestmentType t : types) {
            Label name = new Label(t.name());
            name.setStyle("-fx-font-weight: bold;");

            TextField value = new TextField();
            value.setPromptText("ex: 5000,00");

            fields.put(t.id(), value);

            grid.addRow(r++, name, value);
        }
    }

    private void loadFor(LocalDate date) {
        List<InvestmentType> types = daily.listTypes();
        buildGrid(types);

        DailyEntry entry = daily.loadEntry(date);

        cashField.setText(entry.cashCents() == 0 ? "" : centsToBr(entry.cashCents()));

        for (InvestmentType t : types) {
            long v = entry.investmentValuesCents().getOrDefault(t.id(), 0L);
            fields.get(t.id()).setText(v == 0 ? "" : centsToBr(v));
        }
    }

    private void saveDay() {
        LocalDate date = datePicker.getValue();
        long cash = parseCentsOrZero(cashField.getText());

        Map<Long, Long> inv = new HashMap<>();
        for (var e : fields.entrySet()) {
            long val = parseCentsOrZero(e.getValue().getText());
            inv.put(e.getKey(), val);
        }

        try {
            daily.saveEntry(new DailyEntry(date, cash, inv));
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private long parseCentsOrZero(String s) {
        if (s == null || s.trim().isBlank()) return 0L;
        try {
            String x = s.replace("R$", "").trim();
            if (x.contains(",") && x.contains(".")) x = x.replace(".", "").replace(",", ".");
            else if (x.contains(",")) x = x.replace(",", ".");
            double v = Double.parseDouble(x);
            return Math.round(v * 100.0);
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor inválido: " + s);
        }
    }

    private String centsToBr(long cents) {
        long abs = Math.abs(cents);
        long reais = abs / 100;
        long cent = abs % 100;
        return reais + "," + (cent < 10 ? "0" + cent : cent);
    }
}
