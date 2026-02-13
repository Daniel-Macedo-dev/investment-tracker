package com.daniel.ui.pages;

import com.daniel.domain.DailySummary;
import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

public final class DashboardPage implements Page {

    private final DailyService daily;
    private final VBox root = new VBox(12);

    private final NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));

    private final Label dateLabel = new Label();
    private final Label totalLabel = new Label();
    private final Label profitLabel = new Label();

    private final VBox listBox = new VBox(6);

    public DashboardPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Dashboard");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        HBox cards = new HBox(12,
                card("Data", dateLabel),
                card("Total hoje", totalLabel),
                card("Lucro/Prejuízo hoje", profitLabel)
        );

        Label h2 = new Label("Investimentos (hoje x ontem)");
        h2.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-opacity: 0.9;");

        root.getChildren().addAll(h1, cards, h2, listBox);
    }

    @Override
    public Parent view() { return root; }

    @Override
    public void onShow() {
        LocalDate today = LocalDate.now();
        DailySummary s = daily.summaryFor(today);

        dateLabel.setText(today.toString());
        totalLabel.setText(brl.format(s.totalTodayCents() / 100.0));

        long p = s.totalProfitTodayCents();
        profitLabel.setText((p >= 0 ? "+ " : "- ") + brl.format(Math.abs(p) / 100.0));
        profitLabel.setStyle(p >= 0
                ? "-fx-text-fill: #1fbf72; -fx-font-weight: bold;"
                : "-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");

        listBox.getChildren().clear();

        for (InvestmentType t : daily.listTypes()) {
            long val = s.investmentTodayCents().getOrDefault(t.id(), 0L);
            long prof = s.investmentProfitTodayCents().getOrDefault(t.id(), 0L);

            HBox row = new HBox(12);
            row.getStyleClass().add("card");

            Label name = new Label(t.name());
            name.setPrefWidth(220);
            name.setStyle("-fx-font-weight: bold;");

            Label v = new Label(brl.format(val / 100.0));
            v.setPrefWidth(180);

            Label pr = new Label((prof >= 0 ? "+ " : "- ") + brl.format(Math.abs(prof) / 100.0));
            pr.setStyle(prof >= 0
                    ? "-fx-text-fill: #1fbf72; -fx-font-weight: bold;"
                    : "-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");

            row.getChildren().addAll(name, v, pr);
            listBox.getChildren().add(row);
        }
    }

    private Pane card(String title, Label value) {
        VBox b = new VBox(6);
        b.getStyleClass().add("card");
        b.setPrefWidth(280);

        Label t = new Label(title);
        t.setStyle("-fx-opacity: 0.8;");

        value.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        b.getChildren().addAll(t, value);
        return b;
    }
}
