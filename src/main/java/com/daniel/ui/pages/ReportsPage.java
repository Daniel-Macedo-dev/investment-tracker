package com.daniel.ui.pages;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class ReportsPage implements Page {
    private final VBox root = new VBox(10);

    public ReportsPage() {
        root.setPadding(new Insets(16));
        Label h1 = new Label("Relatórios");
        h1.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label info = new Label("""
                Próximos relatórios:
                • lucro acumulado por investimento em período
                • melhor/pior dia por investimento
                • evolução total + ranking
                """);
        info.setStyle("-fx-opacity: 0.85;");
        root.getChildren().addAll(h1, info);
    }

    @Override
    public Parent view() { return root; }
}
