package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.DailySummary;
import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.DailyTrackingUseCase;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.time.LocalDate;

public final class DashboardPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox root = new VBox(12);

    private final Label dateLabel = new Label("—");
    private final Label totalLabel = new Label("—");
    private final Label profitLabel = new Label("—");

    private final VBox listBox = new VBox(10);

    public DashboardPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        root.setPadding(new Insets(16));
        root.getStyleClass().add("page");

        Label h1 = new Label("Dashboard");
        h1.getStyleClass().add("h1");

        Label sub = new Label("Visão rápida do dia atual (total e lucro de mercado).");
        sub.getStyleClass().add("muted");

        GridPane cards = new GridPane();
        cards.setHgap(12);
        cards.setVgap(12);

        VBox c1 = metricCard("Hoje", dateLabel);
        VBox c2 = metricCard("Total hoje", totalLabel);
        VBox c3 = metricCard("Lucro/Prejuízo (mercado)", profitLabel);

        cards.add(c1, 0, 0);
        cards.add(c2, 1, 0);
        cards.add(c3, 2, 0);

        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS);
        col.setPercentWidth(33.33);
        cards.getColumnConstraints().setAll(col, col, col);

        VBox investmentsCard = new VBox(10);
        investmentsCard.getStyleClass().add("card");
        Label h2 = new Label("Investimentos (hoje)");
        h2.getStyleClass().add("section-title");
        Label hint = new Label("Resumo por investimento (valor e lucro do dia).");
        hint.getStyleClass().add("muted");

        investmentsCard.getChildren().addAll(h2, hint, listBox);

        root.getChildren().addAll(h1, sub, cards, investmentsCard);
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() {
        LocalDate d = LocalDate.now();
        dateLabel.setText(d.toString());

        listBox.getChildren().clear();

        if (!daily.hasAnyDataPublic(d)) {
            totalLabel.setText("—");
            profitLabel.setText("—");

            Label empty = new Label("Sem dados hoje. Vá em Registro Diário e salve o dia.");
            empty.getStyleClass().add("muted");
            listBox.getChildren().setAll(empty);
            return;
        }

        DailySummary s = daily.summaryFor(d);

        totalLabel.setText(daily.brl(s.totalTodayCents()));

        long p = s.totalProfitTodayCents();
        profitLabel.setText(p == 0 ? "—" : ((p >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(p))));
        profitLabel.getStyleClass().removeAll("pos","neg","muted");
        if (p == 0) profitLabel.getStyleClass().add("muted");
        else profitLabel.getStyleClass().add(p >= 0 ? "pos" : "neg");

        for (InvestmentType t : daily.listTypes()) {
            long v = s.investmentTodayCents().getOrDefault(t.id(), 0L);
            long pi = s.investmentProfitTodayCents().getOrDefault(t.id(), 0L);

            HBox row = new HBox(10);
            row.getStyleClass().add("list-item");

            VBox left = new VBox(2);
            Label name = new Label(t.name());
            name.getStyleClass().add("card-title");

            String info = daily.brl(v);
            Label value = new Label(info);
            value.getStyleClass().add("muted");

            left.getChildren().addAll(name, value);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label profit = new Label(pi == 0 ? "—" : ((pi >= 0 ? "+ " : "- ") + daily.brlAbs(Math.abs(pi))));
            profit.getStyleClass().add(pi > 0 ? "pos" : (pi < 0 ? "neg" : "muted"));
            profit.setStyle("-fx-font-weight: 900;");

            row.getChildren().addAll(left, spacer, profit);
            listBox.getChildren().add(row);
        }
    }

    private VBox metricCard(String title, Label value) {
        VBox box = new VBox(6);
        box.getStyleClass().add("card");

        Label t = new Label(title);
        t.getStyleClass().add("muted");

        value.getStyleClass().add("big-value");

        box.getChildren().addAll(t, value);
        return box;
    }
}