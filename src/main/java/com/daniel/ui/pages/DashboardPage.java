package com.daniel.ui.pages;

import com.daniel.domain.DailySummary;
import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.time.LocalDate;

public final class DashboardPage implements Page {

    private final DailyService daily;
    private final VBox root = new VBox(12);

    private final Label dateLabel = new Label();
    private final Label totalLabel = new Label();
    private final Label profitLabel = new Label();

    private final VBox listBox = new VBox(8);

    public DashboardPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Dashboard");
        h1.getStyleClass().add("h1");

        HBox cards = new HBox(12,
                card("Data", dateLabel),
                card("Total hoje", totalLabel),
                card("Lucro/Prejuízo hoje (mercado)", profitLabel)
        );

        Label h2 = new Label("Investimentos (hoje x ontem)");
        h2.getStyleClass().add("section-title");

        root.getChildren().addAll(h1, cards, h2, listBox);
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        LocalDate today = LocalDate.now();
        DailySummary s = daily.summaryFor(today);

        dateLabel.setText(today.toString());
        totalLabel.setText(daily.brl(s.totalTodayCents()));

        long p = s.totalProfitTodayCents();
        profitLabel.setText((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p)));
        profitLabel.getStyleClass().removeAll("pos","neg");
        profitLabel.getStyleClass().add(p >= 0 ? "pos" : "neg");

        listBox.getChildren().clear();

        for (InvestmentType t : daily.listTypes()) {
            long val = s.investmentTodayCents().getOrDefault(t.id(), 0L);
            long prof = s.investmentProfitTodayCents().getOrDefault(t.id(), 0L);

            HBox row = new HBox(12);
            row.getStyleClass().add("card");

            Label name = new Label(t.name());
            name.getStyleClass().add("inv-name");
            name.setPrefWidth(240);

            Label v = new Label(daily.brl(val));
            v.setPrefWidth(160);

            Label pr = new Label((prof >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(prof)));
            pr.getStyleClass().add(prof >= 0 ? "pos" : "neg");

            row.getChildren().addAll(name, v, pr);
            listBox.getChildren().add(row);
        }
    }

    private Pane card(String title, Label value) {
        VBox b = new VBox(6);
        b.getStyleClass().add("card");
        b.setPrefWidth(290);

        Label t = new Label(title);
        t.getStyleClass().add("muted");

        value.getStyleClass().add("card-value");

        b.getChildren().addAll(t, value);
        return b;
    }
}
