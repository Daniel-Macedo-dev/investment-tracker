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

    private final Label dateLabel = new Label("—");
    private final Label totalLabel = new Label("—");
    private final Label profitLabel = new Label("—");

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

        Label h2 = new Label("Investimentos (hoje)");
        h2.getStyleClass().add("section-title");

        root.getChildren().addAll(h1, cards, h2, listBox);
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        LocalDate d = LocalDate.now();
        dateLabel.setText(d.toString());

        if (!daily.hasAnyDataPublic(d)) {
            totalLabel.setText("—");
            profitLabel.setText("—");
            listBox.getChildren().setAll(new Label("Sem dados hoje. Vá em Registro Diário e salve o dia."));
            return;
        }

        DailySummary s = daily.summaryFor(d);
        totalLabel.setText(daily.brl(s.totalTodayCents()));

        long p = s.totalProfitTodayCents();
        profitLabel.setText(p == 0 ? "—" : ((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p))));
        profitLabel.getStyleClass().removeAll("pos","neg","muted");
        if (p == 0) profitLabel.getStyleClass().add("muted");
        else profitLabel.getStyleClass().add(p >= 0 ? "pos" : "neg");

        listBox.getChildren().clear();
        for (InvestmentType t : daily.listTypes()) {
            long v = s.investmentTodayCents().getOrDefault(t.id(), 0L);
            long pi = s.investmentProfitTodayCents().getOrDefault(t.id(), 0L);

            Label line = new Label(t.name() + " • " + daily.brl(v) + " • " +
                    (pi == 0 ? "—" : ((pi >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(pi)))));
            line.getStyleClass().add(pi > 0 ? "pos" : (pi < 0 ? "neg" : "muted"));
            listBox.getChildren().add(line);
        }
    }

    private VBox card(String title, Label value) {
        VBox box = new VBox(6);
        box.getStyleClass().add("card");
        Label t = new Label(title);
        t.getStyleClass().add("muted");
        value.getStyleClass().add("big-value");
        box.getChildren().addAll(t, value);
        return box;
    }
}
