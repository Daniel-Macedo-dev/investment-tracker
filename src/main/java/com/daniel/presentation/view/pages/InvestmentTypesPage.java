package com.daniel.presentation.view.pages;

import com.daniel.core.domain.entity.InvestmentType;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.util.Dialogs;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class InvestmentTypesPage implements Page {

    private final DailyTrackingUseCase daily;
    private final VBox root = new VBox(12);
    private final ListView<InvestmentType> list = new ListView<>();

    public InvestmentTypesPage(DailyTrackingUseCase dailyTrackingUseCase) {
        this.daily = dailyTrackingUseCase;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Tipos de Investimento");
        h1.getStyleClass().add("h1");

        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(InvestmentType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        });

        Button add = new Button("+ Criar");
        Button rename = new Button("Renomear");
        Button delete = new Button("Excluir");
        delete.getStyleClass().add("danger-btn");

        add.setOnAction(e -> onAdd());
        rename.setOnAction(e -> onRename());
        delete.setOnAction(e -> onDelete());

        HBox actions = new HBox(8, add, rename, delete);
        root.getChildren().addAll(h1, new Label("Crie os tipos do seu jeito (ex: Renda fixa, Ações, Cripto, etc)."), actions, list);

        refresh();
    }

    @Override public Parent view() { return root; }

    @Override public void onShow() { refresh(); }

    private void refresh() {
        list.getItems().setAll(daily.listTypes());
    }

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

    private void onRename() {
        InvestmentType sel = list.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        String name = Dialogs.askText("Renomear", "Novo nome:");
        if (name == null || name.isBlank()) return;

        try {
            daily.renameType(sel.id(), name);
            refresh();
        } catch (Exception e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void onDelete() {
        InvestmentType sel = list.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        boolean ok = Dialogs.confirm("Excluir", "Excluir tipo \"" + sel.name() + "\"?",
                "Isso apaga os registros relacionados (snapshots/fluxos) desse tipo.");
        if (!ok) return;

        try {
            daily.deleteType(sel.id());
            refresh();
        } catch (Exception e) {
            Dialogs.error(e.getMessage());
        }
    }
}