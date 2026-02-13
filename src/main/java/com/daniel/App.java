package com.daniel;

import com.daniel.db.Database;
import com.daniel.service.DailyService;
import com.daniel.ui.AppShell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;

public class App extends Application {

    private Connection conn;

    @Override
    public void start(Stage stage) {
        conn = Database.open();

        DailyService dailyService = new DailyService(conn);
        AppShell shell = new AppShell(dailyService);

        Scene scene = new Scene(shell.build(), 1250, 740);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("Investment Tracker");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch();
    }
}
