package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.domain.entity.Enums.LiquidityEnum;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.PageHeader;
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

import com.daniel.core.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class InvestmentTypesPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox root = new VBox(20);
    private final ScrollPane scrollPane = new ScrollPane();
    private final TableView<InvestmentType> table = new TableView<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public InvestmentTypesPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        root.getStyleClass().add("page-root");

        PageHeader header = new PageHeader("Meus Investimentos",
                "Gerencie e acompanhe todos os seus ativos");

        // ── Toolbar ─────────────────────────────────────────────────────────
        Button add = new Button("+ Novo Investimento");
        add.getStyleClass().add("button");

        Button edit = new Button("Editar");
        edit.getStyleClass().add("ghost-btn");

        Button sell = new Button("Registrar Venda");
        sell.getStyleClass().add("sell-btn");

        Button delete = new Button("Excluir");
        delete.getStyleClass().add("danger-btn");

        add.setOnAction(e -> onCreate());
        edit.setOnAction(e -> onEdit());
        sell.setOnAction(e -> onSell());
        delete.setOnAction(e -> onDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, add, spacer, edit, sell, delete);
        toolbar.getStyleClass().add("toolbar");

        // ── Table ────────────────────────────────────────────────────────────
        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox tableCard = new VBox(12, toolbar, table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(16));

        root.getChildren().addAll(header, tableCard);

        scrollPane.setContent(root);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("page-scroll");

        refresh();
    }

    @Override
    public Parent view() {
        return scrollPane;
    }

    @Override
    public void onShow() {
        refresh();
    }

    private void buildTable() {
        table.getStyleClass().add("table-analytic");
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
        liqCol.setPrefWidth(160);

        // Data
        TableColumn<InvestmentType, String> dateCol = new TableColumn<>("Data");
        dateCol.setCellValueFactory(c -> {
            LocalDate date = c.getValue().investmentDate();
            return new javafx.beans.property.SimpleStringProperty(
                    date == null ? "—" : DATE_FMT.format(date)
            );
        });
        dateCol.setPrefWidth(110);

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

        VBox emptyState = new VBox(8);
        emptyState.getStyleClass().add("empty-state");
        emptyState.setAlignment(Pos.CENTER);
        Label emptyIcon = new Label("📂");
        emptyIcon.getStyleClass().add("empty-icon");
        Label emptyTitle = new Label("Nenhum investimento cadastrado");
        emptyTitle.getStyleClass().add("empty-title");
        Label emptyHint = new Label("Clique em \"+ Novo Investimento\" para começar");
        emptyHint.getStyleClass().add("empty-hint");
        emptyState.getChildren().addAll(emptyIcon, emptyTitle, emptyHint);
        table.setPlaceholder(emptyState);

        table.getColumns().setAll(nameCol, catCol, liqCol, dateCol, profCol, valueCol);
    }

    private HBox createBadge(String text, String hexColor) {
        HBox badge = new HBox(6);
        badge.setAlignment(Pos.CENTER_LEFT);
        badge.getStyleClass().add("badge");
        badge.setStyle("-fx-background-color: " + hexColor + "20;");

        Circle circle = new Circle(4);
        circle.setFill(Color.web(hexColor));

        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + hexColor + ";");

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
                int newId = daily.createTypeFull(
                        data.name(),
                        data.category(),
                        data.liquidity(),
                        data.investmentDate(),
                        data.profitability(),
                        data.investedValue(),
                        data.typeOfInvestment(),
                        data.indexType(),
                        data.indexPercentage(),
                        data.ticker(),
                        data.purchasePrice(),
                        data.quantity()
                );

                if (data.investedValue() != null && data.investedValue().signum() > 0) {
                    long totalCents = data.investedValue().multiply(java.math.BigDecimal.valueOf(100)).longValue();
                    Long unitCents = data.purchasePrice() != null
                            ? data.purchasePrice().multiply(java.math.BigDecimal.valueOf(100)).longValue()
                            : null;
                    java.time.LocalDate txDate = data.investmentDate() != null
                            ? data.investmentDate() : java.time.LocalDate.now();
                    daily.recordBuy(newId, data.name(), data.ticker(),
                            data.quantity(), unitCents, totalCents, txDate);
                }

                daily.takeSnapshotIfNeeded(java.time.LocalDate.now());
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
                sel.investedValue(),
                sel.typeOfInvestment(),
                sel.indexType(),
                sel.indexPercentage(),
                sel.ticker(),
                sel.purchasePrice(),
                sel.quantity()
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
                        data.investedValue(),
                        data.typeOfInvestment(),
                        data.indexType(),
                        data.indexPercentage(),
                        data.ticker(),
                        data.purchasePrice(),
                        data.quantity()
                );
                daily.takeSnapshotIfNeeded(java.time.LocalDate.now());
                refresh();
                Dialogs.info("Sucesso", "Investimento atualizado!");
            } catch (Exception e) {
                Dialogs.error("Erro: " + e.getMessage());
            }
        });
    }

    private void onSell() {
        InvestmentType sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Dialogs.info("Atenção", "Selecione um investimento para vender.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Registrar Venda");
        dialog.setHeaderText("Venda de: " + sel.name());
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles/app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dark-dialog");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField qtyField = new TextField();
        qtyField.setPromptText("Quantidade");
        if (sel.quantity() != null) {
            qtyField.setText(sel.quantity().toString());
        }

        TextField priceField = new TextField();
        priceField.setPromptText("R$ 0,00");
        Money.applyFormatOnBlur(priceField);
        if (sel.purchasePrice() != null) {
            long cents = sel.purchasePrice().multiply(BigDecimal.valueOf(100)).longValue();
            priceField.setText(Money.centsToText(cents));
        }

        DatePicker datePicker = new DatePicker(LocalDate.now());

        TextField noteField = new TextField();
        noteField.setPromptText("Observação (opcional)");

        Label qtyLabel = new Label("Quantidade:");
        qtyLabel.getStyleClass().add("form-label");
        Label priceLabel = new Label("Preço Unitário:");
        priceLabel.getStyleClass().add("form-label");
        Label dateLabel = new Label("Data da Venda:");
        dateLabel.getStyleClass().add("form-label");
        Label noteLabel = new Label("Observação:");
        noteLabel.getStyleClass().add("form-label");

        VBox content = new VBox(8,
                qtyLabel, qtyField,
                priceLabel, priceField,
                dateLabel, datePicker,
                noteLabel, noteField
        );
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                long unitCents = Money.textToCentsOrZero(priceField.getText());
                String cleanQty = qtyField.getText().trim().replaceAll("[^0-9]", "");
                Integer qty = cleanQty.isEmpty() ? null : Integer.parseInt(cleanQty);
                long totalCents = qty != null ? unitCents * qty : unitCents;
                LocalDate sellDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
                String note = noteField.getText().isBlank() ? null : noteField.getText().trim();

                daily.recordSell(sel.id(), sel.name(), sel.ticker(),
                        qty, unitCents > 0 ? unitCents : null, totalCents, sellDate, note);
                Dialogs.info("Sucesso", "Venda registrada no extrato!");
            } catch (Exception e) {
                Dialogs.error("Erro ao registrar venda: " + e.getMessage());
            }
        }
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
                "Isso apagará TODOS os dados relacionados.\n\nEsta ação não pode ser desfeita."
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
