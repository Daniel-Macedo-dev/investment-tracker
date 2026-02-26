package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.DailySummary;
import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.components.UiComponents;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class DashboardPage implements Page {

    private final DailyTrackingUseCase daily;
    private final ScrollPane scrollPane;
    private final VBox content;

    private final Label totalLabel = new Label("—");
    private final Label cashLabel = new Label("—");
    private final Label investedLabel = new Label("—");
    private final Label profitLabel = new Label("—");

    private final VBox investmentsListBox = new VBox(10);
    private final VBox portfolioStatusCard = new VBox(12);

    public DashboardPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;
        this.content = new VBox();
        this.scrollPane = UiComponents.pageScroll(content);

        buildLayout();
    }

    private void buildLayout() {
        // Header with greeting and date
        String greeting = getTimeGreeting();
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        VBox header = UiComponents.pageHeader("Dashboard", greeting + " • " + dateStr);

        // KPI Cards in a 4-column grid
        GridPane kpiGrid = UiComponents.cardGrid(4);

        VBox kpiPatrimonio = UiComponents.simpleKpiCard("Patrimônio Total", totalLabel);
        VBox kpiCaixa = UiComponents.simpleKpiCard("Caixa Disponível", cashLabel);
        VBox kpiInvestido = UiComponents.simpleKpiCard("Total Investido", investedLabel);
        VBox kpiLucro = UiComponents.simpleKpiCard("Resultado do Dia", profitLabel);

        kpiGrid.add(kpiPatrimonio, 0, 0);
        kpiGrid.add(kpiCaixa, 1, 0);
        kpiGrid.add(kpiInvestido, 2, 0);
        kpiGrid.add(kpiLucro, 3, 0);

        // Portfolio Health Card with Badge
        VBox healthHeader = new VBox(8);
        Label healthTitle = UiComponents.sectionTitle("Saúde da Carteira");
        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        Label healthBadge = new Label("Em alta");
        healthBadge.getStyleClass().add("badge-success");
        badgeRow.getChildren().add(healthBadge);

        portfolioStatusCard.getStyleClass().add("card");
        portfolioStatusCard.getChildren().addAll(healthTitle, badgeRow);

        // Investments section
        VBox investmentsSection = new VBox(12);
        Label investmentsTitle = UiComponents.sectionTitle("Investimentos (hoje)");
        investmentsSection.getChildren().addAll(investmentsTitle, investmentsListBox);
        investmentsSection.getStyleClass().add("card");

        // Assemble content
        content.getChildren().addAll(
                header,
                kpiGrid,
                portfolioStatusCard,
                investmentsSection
        );
    }

    private String getTimeGreeting() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(12, 0))) {
            return "Bom dia";
        } else if (now.isBefore(LocalTime.of(18, 0))) {
            return "Boa tarde";
        } else {
            return "Boa noite";
        }
    }

    @Override
    public Parent view() {
        return scrollPane;
    }

    @Override
    public void onShow() {
        LocalDate d = LocalDate.now();
        investmentsListBox.getChildren().clear();

        if (!daily.hasAnyDataPublic(d)) {
            totalLabel.setText("—");
            cashLabel.setText("—");
            investedLabel.setText("—");
            profitLabel.setText("—");

            // Update portfolio badge
            updatePortfolioBadge(0);

            // Show empty state
            VBox emptyState = UiComponents.emptyState("🚫", "Sem dados hoje. Vá em Registro Diário e salve o dia.");
            investmentsListBox.getChildren().add(emptyState);
            return;
        }

        DailySummary summary = daily.summaryFor(d);

        // Update KPI cards
        long totalCents = summary.totalTodayCents();
        long cashCents = summary.cashTodayCents();
        long investedCents = totalCents - cashCents;
        long profitCents = summary.totalProfitTodayCents();

        totalLabel.setText(daily.brl(totalCents));
        cashLabel.setText(daily.brl(cashCents));
        investedLabel.setText(daily.brl(investedCents));

        // Format profit
        profitLabel.setText(formatProfit(profitCents));
        UiComponents.styleProfitLabel(profitLabel, profitCents);

        // Update portfolio badge
        updatePortfolioBadge(profitCents);

        // Build investments list
        for (InvestmentType type : daily.listTypes()) {
            long investmentValue = summary.investmentTodayCents().getOrDefault(type.id(), 0L);
            long investmentProfit = summary.investmentProfitTodayCents().getOrDefault(type.id(), 0L);

            // Left content: name + value
            VBox leftContent = new VBox(2);
            Label nameLabel = new Label(type.name());
            nameLabel.getStyleClass().add("card-title");

            Label valueLabel = new Label(daily.brl(investmentValue));
            valueLabel.getStyleClass().add("muted");

            leftContent.getChildren().addAll(nameLabel, valueLabel);

            // Right content: profit
            Label profitText = new Label(formatProfit(investmentProfit));
            profitText.setStyle("-fx-font-weight: bold;");
            UiComponents.styleProfitLabel(profitText, investmentProfit);

            // Create list item row
            HBox listRow = UiComponents.listItemRow(leftContent, profitText);
            investmentsListBox.getChildren().add(listRow);
        }
    }

    private void updatePortfolioBadge(long profitCents) {
        // Find and replace the badge
        HBox badgeRow = null;
        for (javafx.scene.Node node : portfolioStatusCard.getChildren()) {
            if (node instanceof HBox && node != portfolioStatusCard.getChildren().get(0)) {
                badgeRow = (HBox) node;
                break;
            }
        }

        if (badgeRow != null) {
            badgeRow.getChildren().clear();

            Label badge = new Label();
            if (profitCents > 0) {
                badge.setText("Em alta");
                badge.getStyleClass().add("badge-success");
            } else if (profitCents < 0) {
                badge.setText("Em baixa");
                badge.getStyleClass().add("badge-danger");
            } else {
                badge.setText("Sem variação");
                badge.getStyleClass().add("badge-warning");
            }

            badgeRow.getChildren().add(badge);
        }
    }

    private String formatProfit(long cents) {
        if (cents == 0) return "—";
        String sign = cents >= 0 ? "+ " : "- ";
        return sign + daily.brlAbs(Math.abs(cents));
    }
}