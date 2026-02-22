package com.daniel.infrastructure.api;

import okhttp3.*;
import com.google.gson.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BrapiClient {

    private static final String BASE_URL = "https://brapi.dev/api";
    private static final String QUOTE_ENDPOINT = "/quote";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final Gson gson = new Gson();

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
            String error
    ) {
        public boolean hasError() {
            return error != null && !error.isBlank();
        }

        public boolean isValid() {
            return !hasError() && regularMarketPrice > 0;
        }
    }

    /**
     * Busca dados de uma ação específica
     *
     * @param ticker Código da ação (ex: PETR4, VALE3, ITUB4)
     * @return StockData com informações da ação
     * @throws IOException em caso de erro de rede
     */
    public static StockData fetchStockData(String ticker) throws IOException {
        if (ticker == null || ticker.isBlank()) {
            return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, "Ticker inválido");
        }

        String url = BASE_URL + QUOTE_ENDPOINT + "/" + ticker.toUpperCase().trim();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Investment-Tracker/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null,
                        "Erro HTTP: " + response.code());
            }

            String jsonResponse = response.body().string();
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            // Verificar se há erro na resposta
            if (root.has("error")) {
                String errorMsg = root.get("error").getAsString();
                return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, errorMsg);
            }

            // Parsear resultado
            JsonArray results = root.getAsJsonArray("results");
            if (results == null || results.size() == 0) {
                return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null,
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
                    null
            );

        } catch (JsonSyntaxException e) {
            return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null,
                    "Erro ao parsear JSON: " + e.getMessage());
        } catch (Exception e) {
            return new StockData(ticker, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null,
                    "Erro: " + e.getMessage());
        }
    }

    /**
     * Busca múltiplas ações de uma vez
     *
     * @param tickers Lista de tickers separados por vírgula (ex: "PETR4,VALE3,ITUB4")
     * @return Map com ticker → StockData
     */
    public static Map<String, StockData> fetchMultipleStocks(String tickers) throws IOException {
        Map<String, StockData> results = new HashMap<>();

        String url = BASE_URL + QUOTE_ENDPOINT + "/" + tickers.toUpperCase().trim();

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

    //Testa a conexão com a API

    public static boolean testConnection() {
        try {
            StockData test = fetchStockData("PETR4");
            return test.isValid();
        } catch (Exception e) {
            return false;
        }
    }
}