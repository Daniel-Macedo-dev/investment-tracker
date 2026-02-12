package com.daniel;

import com.daniel.db.Database;
import com.daniel.repository.AccountRepository;
import com.daniel.repository.TransactionRepository;
import com.daniel.service.CashService;
import com.daniel.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;

public class App extends Application {

    private Connection conn;

    @Override
    public void start(Stage stage) {
        conn = Database.open();

        AccountRepository accountRepo = new AccountRepository(conn);
        TransactionRepository txRepo = new TransactionRepository(conn);
        CashService cashService = new CashService(txRepo);

        long cashAccountId = accountRepo.ensureDefaultCashAccount();

        MainView mainView = new MainView(cashAccountId, txRepo, cashService);

        stage.setTitle("Investment Tracker");
        stage.setScene(new Scene((javafx.scene.Parent) mainView.build(), 980, 560));
        stage.show();
    }

    @Override
    public void stop() {
        try {
            if (conn != null) conn.close();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch();
    }
}
