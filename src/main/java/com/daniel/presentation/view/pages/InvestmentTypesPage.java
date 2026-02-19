package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.domain.entity.Enums.LiquidityEnum;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.components.InvestmentTypeDialog;
import com.daniel.presentation.view.components.InvestmentTypeDialog.InvestmentTypeData;
import com.daniel.presentation.view.util.Dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class InvestmentTypesPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox root = new VBox(12);
    private final TableView<InvestmentType> table = new TableView<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public InvestmentTypesPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Tipos de Investimento");
        h1.getStyleClass().add("h1");

        buildTable();

        Button add = new Button("+ Criar Investimento");
        add.getStyleClass().add("primary-btn");

        Button edit = new Button("✏️ Editar");

        Button delete = new Button("🗑️ Excluir");
        delete.getStyleClass().add("danger-btn");

        add.setOnAction(e -> onCreate());
        edit.setOnAction(e -> onEdit());
        delete.setOnAction(e -> onDelete());

        HBox actions = new HBox(8, add, edit, delete);

        Label hint = new Label("💡 Crie investimentos com todos os detalhes para acompanhar rentabilidade!");
        hint.getStyleClass().add("muted");

        root.getChildren().addAll(h1, hint, actions, table);

        refresh();
    }

    @Override
    public Parent view() {
        return root;
    }

    @Override
    public void onShow() {
        refresh();
    }

    private void buildTable() {
        table.getStyleClass().add("table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Nome
        TableColumn<InvestmentType, String> nameCol = new TableColumn<>("Nome");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(200);

        // Categoria com badge
        TableColumn<InvestmentType, String> catCol = new TableColumn<>("Categoria");
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                InvestmentType inv = getTableView().getItems().get(getIndex());
                if (inv.category() == null) {
                    setText("—");
                    setGraphic(null);
                } else {
                    try {
                        CategoryEnum cat = CategoryEnum.valueOf(inv.category());
                        setGraphic(createBadge(cat.getDisplayName(), cat.getColor()));
                        setText(null);
                    } catch (Exception e) {
                        setText(inv.category());
                        setGraphic(null);
                    }
                }
            }
        });
        catCol.setPrefWidth(180);

        // Liquidez com badge
        TableColumn<InvestmentType, String> liqCol = new TableColumn<>("Liquidez");
        liqCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                InvestmentType inv = getTableView().getItems().get(getIndex());
                if (inv.liquidity() == null) {
                    setText("—");
                    setGraphic(null);
                } else {
                    try {
                        LiquidityEnum liq = LiquidityEnum.valueOf(inv.liquidity());
                        setGraphic(createBadge(liq.getDisplayName(), liq.getColor()));
                        setText(null);
                    } catch (Exception e) {
                        setText(inv.liquidity());
                        setGraphic(null);
                    }
                }
            }
        });
        liqCol.setPrefWidth(180);

        // Data
        TableColumn<InvestmentType, String> dateCol = new TableColumn<>("Data Investimento");
        dateCol.setCellValueFactory(c -> {
            LocalDate date = c.getValue().investmentDate();
            return new javafx.beans.property.SimpleStringProperty(
                    date == null ? "—" : DATE_FMT.format(date)
            );
        });
        dateCol.setPrefWidth(130);

        // Rentabilidade
        TableColumn<InvestmentType, String> profCol = new TableColumn<>("Rentab. Anual");
        profCol.setCellValueFactory(c -> {
            BigDecimal prof = c.getValue().profitability();
            return new javafx.beans.property.SimpleStringProperty(
                    prof == null ? "—" : String.format("%.2f%%", prof)
            );
        });
        profCol.setPrefWidth(120);

        // Valor Investido
        TableColumn<InvestmentType, String> valueCol = new TableColumn<>("Valor Investido");
        valueCol.setCellValueFactory(c -> {
            BigDecimal val = c.getValue().investedValue();
            if (val == null) {
                return new javafx.beans.property.SimpleStringProperty("—");
            }
            long cents = val.multiply(BigDecimal.valueOf(100)).longValue();
            return new javafx.beans.property.SimpleStringProperty(daily.brl(cents));
        });
        valueCol.setPrefWidth(140);

        table.getColumns().setAll(nameCol, catCol, liqCol, dateCol, profCol, valueCol);
    }

    private HBox createBadge(String text, String hexColor) {
        HBox badge = new HBox(6);
        badge.setAlignment(Pos.CENTER_LEFT);
        badge.setStyle(
                "-fx-background-color: " + hexColor + "20; " +
                        "-fx-background-radius: 6; " +
                        "-fx-padding: 4 8;"
        );

        Circle circle = new Circle(4);
        circle.setFill(Color.web(hexColor));

        Label label = new Label(text);
        label.setStyle(
                "-fx-text-fill: " + hexColor + "; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-weight: bold;"
        );

        badge.getChildren().addAll(circle, label);
        return badge;
    }

    private void refresh() {
        table.getItems().setAll(daily.listTypes());
    }

    private void onCreate() {
        InvestmentTypeDialog dialog = new InvestmentTypeDialog("Novo Investimento", null);
        Optional<InvestmentTypeData> result = dialog.showAndWait();

        result.ifPresent(data -> {
            try {
                daily.createTypeFull(
                        data.name(),
                        data.category(),
                        data.liquidity(),
                        data.investmentDate(),
                        data.profitability(),
                        data.investedValue()
                );
                refresh();
                Dialogs.info("Sucesso", "Investimento criado!");
            } catch (Exception e) {
                Dialogs.error("Erro: " + e.getMessage());
            }
        });
    }

    private void onEdit() {
        InvestmentType sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Dialogs.info("Atenção", "Selecione um investimento.");
            return;
        }

        InvestmentTypeData existing = new InvestmentTypeData(
                sel.name(),
                sel.category(),
                sel.liquidity(),
                sel.investmentDate(),
                sel.profitability(),
                sel.investedValue()
        );

        InvestmentTypeDialog dialog = new InvestmentTypeDialog("Editar Investimento", existing);
        Optional<InvestmentTypeData> result = dialog.showAndWait();

        result.ifPresent(data -> {
            try {
                daily.updateTypeFull(
                        sel.id(),
                        data.name(),
                        data.category(),
                        data.liquidity(),
                        data.investmentDate(),
                        data.profitability(),
                        data.investedValue()
                );
                refresh();
                Dialogs.info("Sucesso", "Investimento atualizado!");
            } catch (Exception e) {
                Dialogs.error("Erro: " + e.getMessage());
            }
        });
    }

    private void onDelete() {
        InvestmentType sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Dialogs.info("Atenção", "Selecione um investimento.");
            return;
        }

        boolean ok = Dialogs.confirm(
                "Excluir",
                "Excluir \"" + sel.name() + "\"?",
                "⚠️ Isso apagará TODOS os dados relacionados.\n\nNão pode ser desfeito."
        );

        if (!ok) return;

        try {
            daily.deleteType(sel.id());
            refresh();
            Dialogs.info("Sucesso", "Investimento excluído!");
        } catch (Exception e) {
            Dialogs.error("Erro: " + e.getMessage());
        }
    }
}