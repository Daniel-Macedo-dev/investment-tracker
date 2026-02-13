package com.daniel.ui.pages;

import com.daniel.domain.InvestmentType;
import com.daniel.service.DailyService;
import com.daniel.ui.Dialogs;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class InvestmentTypesPage implements Page {

    private final DailyService daily;
    private final VBox root = new VBox(12);
    private final ListView<InvestmentType> list = new ListView<>();

    public InvestmentTypesPage(DailyService dailyService) {
        this.daily = dailyService;

        root.setPadding(new Insets(16));

        Label h1 = new Label("Tipos de Investimento");
        h1.getStyleClass().add("h1");

        Button add = new Button("+ Criar");
        add.setOnAction(e -> onAdd());

        Button rename = new Button("Renomear");
        rename.setOnAction(e -> onRename());

        Button delete = new Button("Excluir");
        delete.setOnAction(e -> onDelete());

        HBox actions = new HBox(8, add, rename, delete);

        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(InvestmentType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        root.getChildren().addAll(h1, actions, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        list.getStyleClass().add("list");
    }

    @Override public Parent view() { return root; }
    @Override public void onShow() { refresh(); }

    private void refresh() {
        list.getItems().setAll(daily.listTypes());
        if (list.getItems().isEmpty()) {
            list.setPlaceholder(new Label("Crie seus tipos (ex: CDB, Ouro, Previdência...)."));
        }
    }

    private void onAdd() {
        String name = Dialogs.askText("Novo tipo", "Nome do investimento:");
        if (name == null || name.isBlank()) return;

        try {
            daily.createType(name.trim());
            refresh();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void onRename() {
        InvestmentType sel = list.getSelectionModel().getSelectedItem();
        if (sel == null) { Dialogs.error("Selecione um tipo."); return; }

        String name = Dialogs.askText("Renomear", "Novo nome:");
        if (name == null || name.isBlank()) return;

        try {
            daily.renameType(sel.id(), name.trim());
            refresh();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }

    private void onDelete() {
        InvestmentType sel = list.getSelectionModel().getSelectedItem();
        if (sel == null) { Dialogs.error("Selecione um tipo."); return; }

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Confirmar");
        a.setHeaderText("Excluir tipo: " + sel.name() + "?");
        a.setContentText("Isso apaga os registros diários desse tipo.");
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            daily.deleteType(sel.id());
            refresh();
        } catch (Exception ex) {
            Dialogs.error(ex.getMessage());
        }
    }
}
