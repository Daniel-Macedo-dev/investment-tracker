package com.daniel.presentation.view.components;

import com.daniel.presentation.view.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;

import java.util.Map;

public final class TitleBar extends HBox {

    private static final Map<String, String> PAGE_NAMES = Map.of(
            "Dashboard",                "Dashboard",
            "Cadastrar Investimento",   "Carteira",
            "Diversificação",           "Diversificação",
            "Simulação",                "Simulação",
            "Extrato de Investimentos", "Extrato",
            "Configurações",            "Configurações"
    );

    private final Label pageTitle = new Label();

    public TitleBar(Stage stage, VBox windowBody) {
        getStyleClass().add("titlebar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4);

        Label brand = new Label("Investment Tracker");
        brand.getStyleClass().add("titlebar-brand");

        Label sep = new Label("·");
        sep.getStyleClass().add("titlebar-sep");

        pageTitle.getStyleClass().add("titlebar-page");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minBtn = makeWcBtn(Feather.MINUS, "wc-min");
        minBtn.setOnAction(e -> stage.setIconified(true));

        Button maxBtn = makeWcBtn(Feather.MAXIMIZE_2, "wc-max");
        maxBtn.setOnAction(e -> {
            boolean nowMax = !stage.isMaximized();
            stage.setMaximized(nowMax);
        });

        Button closeBtn = makeWcBtn(Feather.X, "wc-close");
        closeBtn.setOnAction(e -> stage.close());

        getChildren().addAll(brand, sep, pageTitle, spacer, minBtn, maxBtn, closeBtn);

        // Toggle window-maximized class for CSS corner-radius reset
        stage.maximizedProperty().addListener((obs, old, max) -> {
            if (max) windowBody.getStyleClass().add("window-maximized");
            else     windowBody.getStyleClass().remove("window-maximized");
        });

        // Drag-to-move
        double[] origin = {0, 0};
        setOnMousePressed(e -> {
            origin[0] = e.getScreenX() - stage.getX();
            origin[1] = e.getScreenY() - stage.getY();
        });
        setOnMouseDragged(e -> {
            if (stage.isMaximized()) return;
            stage.setX(e.getScreenX() - origin[0]);
            stage.setY(e.getScreenY() - origin[1]);
        });
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) stage.setMaximized(!stage.isMaximized());
        });
    }

    private static Button makeWcBtn(Feather icon, String extraClass) {
        Button btn = new Button();
        btn.setGraphic(Icons.of(icon, 13));
        btn.getStyleClass().addAll("wc-btn", extraClass);
        btn.setFocusTraversable(false);
        return btn;
    }

    public void setPageTitle(String key) {
        pageTitle.setText(PAGE_NAMES.getOrDefault(key, key));
    }
}
