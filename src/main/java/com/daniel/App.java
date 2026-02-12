package com.daniel;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        VBox root = new VBox(12);
        root.setStyle("-fx-padding: 16;");
        root.getChildren().addAll(
                new Label("Investment Tracker"),
                new Label("Local-first (SQLite) - JavaFX")
        );

        stage.setTitle("Investment Tracker");
        stage.setScene(new Scene(root, 520, 200));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
