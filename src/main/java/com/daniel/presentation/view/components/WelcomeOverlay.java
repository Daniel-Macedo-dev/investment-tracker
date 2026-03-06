package com.daniel.presentation.view.components;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.prefs.Preferences;

/**
 * Premium welcome overlay shown on first run.
 * Persists dismissal via java.util.prefs.Preferences.
 */
public final class WelcomeOverlay {

    private static final String PREF_NODE  = "com/daniel/investmentTracker";
    private static final String PREF_KEY   = "welcomeShown";

    private final StackPane overlay = new StackPane();
    private final Runnable onCreateInvestment;
    private final Runnable onConfigure;

    public WelcomeOverlay(Runnable onCreateInvestment, Runnable onConfigure) {
        this.onCreateInvestment = onCreateInvestment;
        this.onConfigure = onConfigure;
        buildOverlay();
    }

    /** Returns true if the overlay should be shown this session. */
    public static boolean shouldShow() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        return !prefs.getBoolean(PREF_KEY, false);
    }

    /** Marks the welcome as shown so it won't appear on next launch. */
    public static void markShown() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            prefs.putBoolean(PREF_KEY, true);
            prefs.flush();
        } catch (Exception ignored) {}
    }

    public StackPane getNode() {
        return overlay;
    }

    public void animateIn() {
        overlay.setOpacity(0);

        FadeTransition fadeDim = new FadeTransition(Duration.millis(220), overlay);
        fadeDim.setFromValue(0);
        fadeDim.setToValue(1);
        fadeDim.setInterpolator(Interpolator.EASE_OUT);

        VBox card = (VBox) overlay.getChildren().get(0);
        card.setScaleX(0.92);
        card.setScaleY(0.92);
        ScaleTransition scaleCard = new ScaleTransition(Duration.millis(260), card);
        scaleCard.setFromX(0.92);
        scaleCard.setFromY(0.92);
        scaleCard.setToX(1.0);
        scaleCard.setToY(1.0);
        scaleCard.setInterpolator(Interpolator.EASE_OUT);

        fadeDim.play();
        scaleCard.play();
    }

    private void buildOverlay() {
        overlay.getStyleClass().add("welcome-overlay");

        // ── Hero card ─────────────────────────────────────────────────────
        VBox card = new VBox(16);
        card.getStyleClass().add("welcome-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(520);

        Label logo = new Label("💰");
        logo.getStyleClass().add("welcome-logo");
        logo.setAlignment(Pos.CENTER);

        Label title = new Label("Bem-vindo ao Investment Tracker");
        title.getStyleClass().add("welcome-title");
        title.setAlignment(Pos.CENTER);
        title.setWrapText(true);

        Label subtitle = new Label(
                "Acompanhe seus investimentos, visualize rentabilidade e simule cenários — tudo em um só lugar.");
        subtitle.getStyleClass().add("welcome-subtitle");
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(400);

        // ── CTAs ──────────────────────────────────────────────────────────
        Button createBtn = new Button("+ Criar primeiro investimento");
        createBtn.getStyleClass().add("welcome-cta-primary");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> dismiss(true, onCreateInvestment));

        Button configBtn = new Button("⚙ Configurar token da Brapi");
        configBtn.getStyleClass().add("welcome-cta-secondary");
        configBtn.setMaxWidth(Double.MAX_VALUE);
        configBtn.setOnAction(e -> dismiss(true, onConfigure));

        Button dashBtn = new Button("Ir para o Dashboard");
        dashBtn.getStyleClass().add("welcome-cta-secondary");
        dashBtn.setMaxWidth(Double.MAX_VALUE);
        dashBtn.setOnAction(e -> dismiss(false, null));

        // ── Don't show again ──────────────────────────────────────────────
        CheckBox noShow = new CheckBox("Não mostrar novamente");
        noShow.getStyleClass().add("welcome-skip");

        card.getChildren().addAll(logo, title, subtitle, createBtn, configBtn, dashBtn, noShow);

        overlay.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        // Click on the dim background closes the overlay
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) dismiss(noShow.isSelected(), null);
        });
    }

    private void dismiss(boolean persist, Runnable callback) {
        if (persist) markShown();

        FadeTransition fade = new FadeTransition(Duration.millis(160), overlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);
        fade.setOnFinished(ev -> {
            if (overlay.getParent() instanceof StackPane parent) {
                parent.getChildren().remove(overlay);
            }
            if (callback != null) callback.run();
        });
        fade.play();
    }
}
