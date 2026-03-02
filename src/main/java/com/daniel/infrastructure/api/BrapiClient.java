package com.daniel.infrastructure.api;

import com.daniel.infrastructure.persistence.repository.AppSettingsRepository;
import okhttp3.*;
import com.google.gson.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class BrapiClient {

    private static final String BASE_URL = "https://brapi.dev/api";
    private static final String QUOTE_ENDPOINT = "/quote";
    private static final String AVAILABLE_ENDPOINT = "/available";

    public static final String SETTINGS_KEY_TOKEN = "brapi_token";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final Gson gson = new Gson();
    private static final AppSettingsRepository settingsRepo = new AppSettingsRepository();

    private static String getToken() {
        return settingsRepo.get(SETTINGS_KEY_TOKEN).orElse(null);
    }

    private static String appendToken(String url, String token) {
        if (token == null || token.isBlank()) return url;
        return url + (url.contains("?") ? "&" : "?") + "token=" + token.trim();
    }

    public record StockData(
            String ticker,
            String logoUrl,
            String longName,
            double regularMarketPrice,
            double regularMarketChange,
            double regularMarketChangePercent,
            double regularMarketOpen,
            double regularMarketDayHigh,
            double regularMarketDayLow,
            long regularMarketVolume,
            double fiftyTwoWeekHigh,
            double fiftyTwoWeekLow,
            double twoHundredDayAverage,
            String currency,
            double dividendYield,
            String error
    ) {
        public boolean hasError() {
            return error != null && !error.isBlank();
        }

        public boolean isValid() {
            return !hasError() && regularMarketPrice > 0;
        }
    }

    // Sugestão de ticker
    public record TickerSuggestion(
            String ticker,
            String name,
            String type  // "stock", "fund", "fii"
    ) {}

    // Histórico de dividendos
    public record DividendHistory(
            String ticker,
            double averageYield,  // Média de dividend yield
            double lastYearTotal,  // Total pago no último ano
            List<DividendPayment> payments
    ) {}

    public record DividendPayment(
            String date,
            double value
    ) {}

    /**
     * Busca dados de uma ação específica
     */
    public static StockData fetchStockData(String ticker) throws IOException {
        return fetchStockDataWithToken(ticker, getToken());
    }

    public static StockData fetchStockDataWithToken(String ticker, String token) throws IOException {
        if (ticker == null || ticker.isBlank()) {
            return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, "Ticker inválido");
        }

        String url = appendToken(
                BASE_URL + QUOTE_ENDPOINT + "/" + ticker.toUpperCase().trim(), token);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Investment-Tracker/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0,
                        "Erro HTTP: " + response.code());
            }

            String jsonResponse = response.body().string();
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            if (root.has("error")) {
                String errorMsg = root.get("error").getAsString();
                return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, errorMsg);
            }

            JsonArray results = root.getAsJsonArray("results");
            if (results == null || results.size() == 0) {
                return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0,
                        "Ação não encontrada");
            }

            JsonObject stock = results.get(0).getAsJsonObject();

            return new StockData(
                    getStringOrNull(stock, "symbol"),
                    getStringOrNull(stock, "logourl"),
                    getStringOrNull(stock, "longName"),
                    getDoubleOrZero(stock, "regularMarketPrice"),
                    getDoubleOrZero(stock, "regularMarketChange"),
                    getDoubleOrZero(stock, "regularMarketChangePercent"),
                    getDoubleOrZero(stock, "regularMarketOpen"),
                    getDoubleOrZero(stock, "regularMarketDayHigh"),
                    getDoubleOrZero(stock, "regularMarketDayLow"),
                    getLongOrZero(stock, "regularMarketVolume"),
                    getDoubleOrZero(stock, "fiftyTwoWeekHigh"),
                    getDoubleOrZero(stock, "fiftyTwoWeekLow"),
                    getDoubleOrZero(stock, "twoHundredDayAverage"),
                    getStringOrNull(stock, "currency"),
                    getDoubleOrZero(stock, "dividendYield"),
                    null
            );

        } catch (JsonSyntaxException e) {
            return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0,
                    "Erro ao parsear JSON: " + e.getMessage());
        } catch (Exception e) {
            return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0,
                    "Erro: " + e.getMessage());
        }
    }

    /**
     * Busca tickers por nome/código (autocomplete)
     */
    public static List<TickerSuggestion> searchTickers(String query) throws IOException {
        if (query == null || query.isBlank() || query.length() < 2) {
            return new ArrayList<>();
        }

        String url = BASE_URL + AVAILABLE_ENDPOINT;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Investment-Tracker/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new ArrayList<>();
            }

            String jsonResponse = response.body().string();
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            List<TickerSuggestion> suggestions = new ArrayList<>();

            if (root.has("stocks")) {
                JsonArray stocks = root.getAsJsonArray("stocks");
                for (JsonElement element : stocks) {
                    String ticker = element.getAsString();
                    if (ticker.toLowerCase().contains(query.toLowerCase())) {
                        suggestions.add(new TickerSuggestion(ticker, ticker, "stock"));
                    }
                }
            }

            // Limitar a 10 sugestões
            return suggestions.size() > 10 ? suggestions.subList(0, 10) : suggestions;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Calcula média de dividendos com base no dividend yield
     */
    public static DividendHistory estimateDividends(String ticker) throws IOException {
        StockData data = fetchStockData(ticker);

        if (!data.isValid() || data.dividendYield() <= 0) {
            return new DividendHistory(ticker, 0, 0, new ArrayList<>());
        }

        // Estimar dividendos anuais baseado no yield e preço atual
        double estimatedAnnualDividend = data.regularMarketPrice() * (data.dividendYield() / 100.0);

        return new DividendHistory(
                ticker,
                data.dividendYield(),
                estimatedAnnualDividend,
                new ArrayList<>()  // API pública não retorna histórico detalhado
        );
    }

    /**
     * Busca múltiplas ações de uma vez
     */
    public static Map<String, StockData> fetchMultipleStocks(String tickers) throws IOException {
        Map<String, StockData> results = new HashMap<>();

        String url = appendToken(
                BASE_URL + QUOTE_ENDPOINT + "/" + tickers.toUpperCase().trim(), getToken());

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Investment-Tracker/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return results;
            }

            String jsonResponse = response.body().string();
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            if (root.has("error")) {
                return results;
            }

            JsonArray resultsArray = root.getAsJsonArray("results");
            if (resultsArray == null) {
                return results;
            }

            for (JsonElement element : resultsArray) {
                JsonObject stock = element.getAsJsonObject();
                String symbol = getStringOrNull(stock, "symbol");

                if (symbol != null) {
                    StockData data = new StockData(
                            symbol,
                            getStringOrNull(stock, "logourl"),
                            getStringOrNull(stock, "longName"),
                            getDoubleOrZero(stock, "regularMarketPrice"),
                            getDoubleOrZero(stock, "regularMarketChange"),
                            getDoubleOrZero(stock, "regularMarketChangePercent"),
                            getDoubleOrZero(stock, "regularMarketOpen"),
                            getDoubleOrZero(stock, "regularMarketDayHigh"),
                            getDoubleOrZero(stock, "regularMarketDayLow"),
                            getLongOrZero(stock, "regularMarketVolume"),
                            getDoubleOrZero(stock, "fiftyTwoWeekHigh"),
                            getDoubleOrZero(stock, "fiftyTwoWeekLow"),
                            getDoubleOrZero(stock, "twoHundredDayAverage"),
                            getStringOrNull(stock, "currency"),
                            getDoubleOrZero(stock, "dividendYield"),
                            null
                    );

                    results.put(symbol, data);
                }
            }

            return results;

        } catch (Exception e) {
            return results;
        }
    }

    // Helper methods

    private static String getStringOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static double getDoubleOrZero(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return 0.0;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static long getLongOrZero(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static boolean testConnection() {
        try {
            StockData test = fetchStockData("PETR4");
            return test.isValid();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean testConnectionWithToken(String token) {
        try {
            StockData test = fetchStockDataWithToken("PETR4", token);
            return test.isValid();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasToken() {
        String token = getToken();
        return token != null && !token.isBlank();
    }
}