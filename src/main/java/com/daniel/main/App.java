package com.daniel.main;

import com.daniel.infrastructure.config.AppConfig;
import com.daniel.infrastructure.persistence.config.Database;
import com.daniel.core.service.DailyTrackingUseCase;
import com.daniel.presentation.view.AppShell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;

public class App extends Application {

    private Connection conn;
    private AppConfig appConfig;

    @Override
    public void init() {
        this.appConfig = new AppConfig();
        Database.open();
    }

    @Override
    public void start(Stage stage) {
        conn = Database.open();

        DailyTrackingUseCase dailyTrackingUseCase = appConfig.getDailyTrackingUseCase();
        AppShell shell = new AppShell(dailyTrackingUseCase);

        Scene scene = new Scene(shell.build(), 1280, 760);
        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

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
