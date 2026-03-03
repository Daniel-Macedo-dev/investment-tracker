package com.daniel.presentation.view.pages;

import com.daniel.infrastructure.api.BcbClient;
import com.daniel.infrastructure.api.BrapiClient;
import com.daniel.infrastructure.persistence.repository.AppSettingsRepository;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public final class ConfiguracoesPage implements Page {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppSettingsRepository settings = new AppSettingsRepository();
    private final ScrollPane scrollPane = new ScrollPane();
    private final VBox root = new VBox(20);

    // Brapi section
    private final TextField tokenField = new TextField();
    private final CheckBox autoUpdateCheckbox = new CheckBox("Atualizar cotações automaticamente ao abrir o app");
    private final Label tokenStatusLabel = new Label();

    // BCB section
    private final Label cdiValueLabel = new Label("—");
    private final Label selicValueLabel = new Label("—");
    private final Label ipcaValueLabel = new Label("—");
    private final Label bcbLastUpdateLabel = new Label("Nunca atualizado");

    public ConfiguracoesPage() {
        root.setPadding(new Insets(16));

        Label h1 = new Label("Configurações");
        h1.getStyleClass().add("h1");

        Label subtitle = new Label("Gerencie tokens de API e preferências do app");
        subtitle.getStyleClass().add("muted");

        root.getChildren().addAll(h1, subtitle, buildBrapiSection(), buildBcbSection(), buildAboutSection());

        scrollPane.setContent(root);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-pane");
    }

    @Override
    public Parent view() {
        return scrollPane;
    }

    @Override
    public void onShow() {
        loadSavedSettings();
    }

    private void loadSavedSettings() {
        String savedToken = settings.get(BrapiClient.SETTINGS_KEY_TOKEN).orElse("");
        tokenField.setText(savedToken);

        String autoUpdate = settings.get("brapi_auto_update").orElse("false");
        autoUpdateCheckbox.setSelected(Boolean.parseBoolean(autoUpdate));

        updateTokenStatus(savedToken);
        loadBcbCachedValues();
    }

    private void updateTokenStatus(String token) {
        if (token == null || token.isBlank()) {
            tokenStatusLabel.setText("⚠️ Token não configurado. Cotações usarão preço de compra como fallback.");
            tokenStatusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12px;");
        } else {
            tokenStatusLabel.setText("✅ Token configurado.");
            tokenStatusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 12px;");
        }
    }

    private void loadBcbCachedValues() {
        String cdi = settings.get("rate_cdi").orElse(null);
        String selic = settings.get("rate_selic").orElse(null);
        String ipca = settings.get("rate_ipca").orElse(null);
        String lastUpdate = settings.get("rate_last_update").orElse(null);

        cdiValueLabel.setText(cdi != null ? String.format("%.2f%% a.a.", Double.parseDouble(cdi) * 100) : "—");
        selicValueLabel.setText(selic != null ? String.format("%.2f%% a.a.", Double.parseDouble(selic) * 100) : "—");
        ipcaValueLabel.setText(ipca != null ? String.format("%.2f%% a.a.", Double.parseDouble(ipca) * 100) : "—");
        bcbLastUpdateLabel.setText(lastUpdate != null ? "Última atualização: " + lastUpdate : "Nunca atualizado");
    }

    private VBox buildBrapiSection() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("API Brapi — Cotações de Ações");
        title.getStyleClass().add("card-title");

        Label tokenLabel = new Label("Token Brapi *");
        tokenLabel.setStyle("-fx-font-weight: bold;");

        tokenField.setPromptText("Cole seu token aqui...");
        tokenField.setStyle("-fx-font-family: 'Courier New';");

        Label tokenHint = new Label("Obtenha seu token gratuito em brapi.dev");
        tokenHint.getStyleClass().add("muted");
        tokenHint.setStyle("-fx-font-size: 11px;");

        tokenStatusLabel.setWrapText(true);
        updateTokenStatus(tokenField.getText());

        tokenField.textProperty().addListener((obs, old, newVal) -> updateTokenStatus(newVal));

        Button testBtn = new Button("Testar Token");
        testBtn.getStyleClass().add("secondary-btn");
        testBtn.setOnAction(e -> testToken());

        Button saveBtn = new Button("Salvar Configurações");
        saveBtn.getStyleClass().add("primary-btn");
        saveBtn.setOnAction(e -> saveSettings());

        HBox btnRow = new HBox(10, testBtn, saveBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                title,
                tokenLabel, tokenField, tokenHint,
                tokenStatusLabel,
                autoUpdateCheckbox,
                btnRow
        );
        return card;
    }

    private VBox buildBcbSection() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("Benchmarks — Banco Central do Brasil");
        title.getStyleClass().add("card-title");

        Label hint = new Label("Busca as taxas oficiais CDI, SELIC e IPCA direto da API do BCB.");
        hint.getStyleClass().add("muted");
        hint.setStyle("-fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(8);

        grid.add(new Label("CDI:"), 0, 0);
        cdiValueLabel.setStyle("-fx-font-weight: bold;");
        grid.add(cdiValueLabel, 1, 0);

        grid.add(new Label("SELIC:"), 0, 1);
        selicValueLabel.setStyle("-fx-font-weight: bold;");
        grid.add(selicValueLabel, 1, 1);

        grid.add(new Label("IPCA:"), 0, 2);
        ipcaValueLabel.setStyle("-fx-font-weight: bold;");
        grid.add(ipcaValueLabel, 1, 2);

        bcbLastUpdateLabel.getStyleClass().add("muted");
        bcbLastUpdateLabel.setStyle("-fx-font-size: 11px;");

        Button updateBtn = new Button("Atualizar CDI / SELIC / IPCA");
        updateBtn.getStyleClass().add("primary-btn");
        updateBtn.setOnAction(e -> fetchBcbRates(updateBtn));

        card.getChildren().addAll(title, hint, grid, bcbLastUpdateLabel, updateBtn);
        return card;
    }

    private VBox buildAboutSection() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("Sobre");
        title.getStyleClass().add("card-title");

        Label version = new Label("Investment Tracker v0.5.0");
        version.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label apis = new Label("APIs utilizadas:\n• Brapi (brapi.dev) — Cotações da B3\n• BCB (api.bcb.gov.br) — Taxas oficiais");
        apis.getStyleClass().add("muted");
        apis.setStyle("-fx-font-size: 12px;");

        Label privacy = new Label("Privacidade: todos os dados são armazenados localmente. Nenhum dado é enviado a servidores externos além das chamadas às APIs acima.");
        privacy.getStyleClass().add("muted");
        privacy.setStyle("-fx-font-size: 11px;");
        privacy.setWrapText(true);

        card.getChildren().addAll(title, version, apis, privacy);
        return card;
    }

    private void testToken() {
        String token = tokenField.getText().trim();
        if (token.isBlank()) {
            tokenStatusLabel.setText("⚠️ Digite um token antes de testar.");
            tokenStatusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12px;");
            return;
        }

        tokenStatusLabel.setText("🔄 Testando conexão...");
        tokenStatusLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

        CompletableFuture.supplyAsync(() -> BrapiClient.testConnectionWithToken(token))
                .thenAcceptAsync(ok -> Platform.runLater(() -> {
                    if (ok) {
                        tokenStatusLabel.setText("✅ Token válido! Conexão estabelecida com sucesso.");
                        tokenStatusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 12px;");
                    } else {
                        tokenStatusLabel.setText("❌ Token inválido ou sem conexão com a Brapi.");
                        tokenStatusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px;");
                    }
                }));
    }

    private void saveSettings() {
        String token = tokenField.getText().trim();
        if (token.isBlank()) {
            settings.delete(BrapiClient.SETTINGS_KEY_TOKEN);
        } else {
            settings.set(BrapiClient.SETTINGS_KEY_TOKEN, token);
        }

        settings.set("brapi_auto_update", String.valueOf(autoUpdateCheckbox.isSelected()));

        updateTokenStatus(token);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Configurações Salvas");
        alert.setHeaderText(null);
        alert.setContentText("Configurações salvas com sucesso!");
        alert.showAndWait();
    }

    private void fetchBcbRates(Button btn) {
        btn.setDisable(true);
        btn.setText("Atualizando...");

        CompletableFuture.supplyAsync(() -> {
            double cdi = BcbClient.fetchCdi().orElse(-1.0);
            double selic = BcbClient.fetchSelic().orElse(-1.0);
            double ipca = BcbClient.fetchIpca().orElse(-1.0);
            return new double[]{cdi, selic, ipca};
        }).thenAcceptAsync(rates -> Platform.runLater(() -> {
            btn.setDisable(false);
            btn.setText("Atualizar CDI / SELIC / IPCA");

            boolean anySuccess = false;

            if (rates[0] > 0) {
                settings.set("rate_cdi", String.valueOf(rates[0]));
                cdiValueLabel.setText(String.format("%.2f%% a.a.", rates[0] * 100));
                anySuccess = true;
            }
            if (rates[1] > 0) {
                settings.set("rate_selic", String.valueOf(rates[1]));
                selicValueLabel.setText(String.format("%.2f%% a.a.", rates[1] * 100));
                anySuccess = true;
            }
            if (rates[2] > 0) {
                settings.set("rate_ipca", String.valueOf(rates[2]));
                ipcaValueLabel.setText(String.format("%.2f%% a.a.", rates[2] * 100));
                anySuccess = true;
            }

            if (anySuccess) {
                String now = LocalDateTime.now().format(DT_FMT);
                settings.set("rate_last_update", now);
                bcbLastUpdateLabel.setText("Última atualização: " + now);
            } else {
                bcbLastUpdateLabel.setText("⚠️ Falha ao buscar taxas. Verifique sua conexão.");
            }
        }));
    }
}
