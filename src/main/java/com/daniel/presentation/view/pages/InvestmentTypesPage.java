package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.domain.entity.Enums.CategoryEnum;
import com.daniel.core.domain.entity.Enums.LiquidityEnum;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.components.UiComponents;
import com.daniel.presentation.view.util.Dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Investment types management page.
 * Displays types as styled cards with category/liquidity info.
 * Provides actions to create, rename, and delete types.
 */
public final class InvestmentTypesPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox contentBox = new VBox(20);
    private final ScrollPane root;

    private final VBox typeCardsContainer = new VBox(12);
    private final Label typeCountLabel = new Label("0");

    private InvestmentType selectedType = null;

    public InvestmentTypesPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        contentBox.setSpacing(20);
        contentBox.setPadding(new Insets(0));

        contentBox.getChildren().addAll(
                buildHeader(),
                buildSummaryCard(),
                buildTypesSection(),
                buildActionsBar()
        );

        root = UiComponents.pageScroll(contentBox);
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

    /**
     * Builds the page header.
     */
    private Parent buildHeader() {
        return UiComponents.pageHeader("Tipos de Investimento",
                "Crie os tipos do seu jeito (ex.: CDB, Ações, Cripto, Tesouro...).");
    }

    /**
     * Builds the summary card with type count and category chips.
     */
    private Parent buildSummaryCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("Resumo");
        title.getStyleClass().add("card-title");

        HBox countRow = new HBox(6);
        Label countLbl = new Label("Total de tipos:");
        countLbl.getStyleClass().add("card-label");
        typeCountLabel.getStyleClass().add("big-value");
        countRow.getChildren().addAll(countLbl, typeCountLabel);

        VBox summaryContent = new VBox(12, title, countRow);
        card.getChildren().add(summaryContent);

        return card;
    }

    /**
     * Builds the investment types display section with cards in a VBox.
     */
    private Parent buildTypesSection() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = UiComponents.sectionTitle("Tipos cadastrados");

        VBox scrollContent = new VBox(10, title, typeCardsContainer);
        VBox.setVgrow(typeCardsContainer, Priority.ALWAYS);

        card.getChildren().add(scrollContent);
        VBox.setVgrow(card, Priority.ALWAYS);

        return card;
    }

    /**
     * Builds the action buttons bar at the bottom.
     */
    private Parent buildActionsBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("bottom-bar");

        Button btnAdd = new Button("+ Novo tipo");
        btnAdd.getStyleClass().add("primary-btn");
        btnAdd.setOnAction(e -> onAdd());

        Button btnRename = new Button("Renomear");
        btnRename.getStyleClass().add("ghost-btn");
        btnRename.setOnAction(e -> onRename());

        Button btnDelete = new Button("Excluir");
        btnDelete.getStyleClass().add("danger-btn");
        btnDelete.setOnAction(e -> onDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(btnAdd, btnRename, btnDelete, spacer);

        return bar;
    }

    /**
     * Builds a card representing a single investment type.
     */
    private VBox buildTypeCard(InvestmentType type) {
        VBox card = new VBox(10);
        card.getStyleClass().add("inv-type-card");
        card.setPadding(new Insets(12));
        card.setMinHeight(100);

        // Click to select
        card.setOnMouseClicked(e -> {
            selectedType = type;
            refreshCardStyles();
        });

        // Type name (bold)
        Label nameLabel = new Label(type.name());
        nameLabel.getStyleClass().add("h3");

        VBox nameBox = new VBox(nameLabel);

        // Category chip (if present)
        VBox chipBox = new VBox();
        chipBox.setSpacing(8);

        if (type.category() != null) {
            try {
                CategoryEnum cat = CategoryEnum.valueOf(type.category());
                String chipClass = mapCategoryToChipClass(cat);
                Label categoryChip = UiComponents.chip(cat.getDisplayName(), chipClass);
                chipBox.getChildren().add(categoryChip);
            } catch (IllegalArgumentException ignored) {}
        }

        // Liquidity chip (if present)
        if (type.liquidity() != null) {
            try {
                LiquidityEnum liq = LiquidityEnum.valueOf(type.liquidity());
                String chipClass = mapLiquidityToChipClass(liq);
                Label liquidityChip = UiComponents.chip(liq.getDisplayName(), chipClass);
                chipBox.getChildren().add(liquidityChip);
            } catch (IllegalArgumentException ignored) {}
        }

        // Data completeness badge
        if (!type.hasFullData()) {
            Label incompleteWarning = UiComponents.badge("Dados incompletos", "badge-warning");
            chipBox.getChildren().add(incompleteWarning);
        }

        card.getChildren().addAll(nameBox, chipBox);

        return card;
    }

    /**
     * Maps CategoryEnum to chip CSS class.
     */
    private String mapCategoryToChipClass(CategoryEnum category) {
        return switch (category) {
            case RENDA_FIXA -> "chip-blue";
            case RENDA_VARIAVEL -> "chip-green";
            case FUNDOS -> "chip-amber";
            case PREVIDENCIA -> "chip-purple";
            case CRIPTOMOEDAS -> "chip-red";
            case OUTROS -> "chip-gray";
        };
    }

    /**
     * Maps LiquidityEnum to chip CSS class.
     */
    private String mapLiquidityToChipClass(LiquidityEnum liquidity) {
        return switch (liquidity) {
            case MUITO_ALTA -> "chip-green";
            case ALTA -> "chip-green";
            case MEDIA -> "chip-amber";
            case BAIXA -> "chip-amber";
            case MUITO_BAIXA -> "chip-red";
        };
    }

    /**
     * Refreshes the card styles to highlight the selected type.
     */
    private void refreshCardStyles() {
        for (javafx.scene.Node node : typeCardsContainer.getChildren()) {
            if (node instanceof VBox card) {
                // Check if this is the selected card by looking at its content
                boolean isSelected = false;
                if (!card.getChildren().isEmpty()) {
                    javafx.scene.Node firstChild = card.getChildren().get(0);
                    if (firstChild instanceof VBox nameBox && !nameBox.getChildren().isEmpty()) {
                        javafx.scene.Node nameLabel = nameBox.getChildren().get(0);
                        if (nameLabel instanceof Label label) {
                            isSelected = label.getText().equals(selectedType.name());
                        }
                    }
                }

                if (isSelected) {
                    card.getStyleClass().add("selected");
                } else {
                    card.getStyleClass().remove("selected");
                }
            }
        }
    }

    /**
     * Refreshes the entire page with latest types.
     */
    private void refresh() {
        List<InvestmentType> types = daily.listTypes();

        typeCountLabel.setText(String.valueOf(types.size()));

        typeCardsContainer.getChildren().clear();

        if (types.isEmpty()) {
            Label empty = new Label("Nenhum tipo cadastrado. Crie um novo tipo para começar.");
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            typeCardsContainer.getChildren().add(empty);
        } else {
            for (InvestmentType type : types) {
                VBox card = buildTypeCard(type);
                typeCardsContainer.getChildren().add(card);
            }
        }

        selectedType = null;
    }

    /**
     * Opens dialog to create a new investment type.
     */
    private void onAdd() {
        String name = Dialogs.askText("Novo tipo", "Nome do tipo:");
        if (name == null || name.isBlank()) return;

        try {
            daily.createType(name);
            refresh();
        } catch (Exception e) {
            Dialogs.error(e.getMessage());
        }
    }

    /**
     * Opens dialog to rename the selected type.
     */
    private void onRename() {
        if (selectedType == null) {
            Dialogs.info("Selecione um tipo", "Clique em um tipo para selecioná-lo antes de renomear.");
            return;
        }

        String newName = Dialogs.askText("Renomear", "Novo nome:");
        if (newName == null || newName.isBlank()) return;

        try {
            daily.renameType(selectedType.id(), newName);
            refresh();
        } catch (Exception e) {
            Dialogs.error(e.getMessage());
        }
    }

    /**
     * Opens confirmation dialog to delete the selected type.
     */
    private void onDelete() {
        if (selectedType == null) {
            Dialogs.info("Selecione um tipo", "Clique em um tipo para selecioná-lo antes de excluir.");
            return;
        }

        boolean ok = Dialogs.confirm("Excluir",
                "Excluir tipo \"" + selectedType.name() + "\"?",
                "Isso apaga os registros relacionados (snapshots/fluxos) desse tipo.");
        if (!ok) return;

        try {
            daily.deleteType(selectedType.id());
            refresh();
        } catch (Exception e) {
            Dialogs.error(e.getMessage());
        }
    }
}
