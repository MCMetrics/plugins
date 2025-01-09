package net.mcmetrics.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

import net.mcmetrics.shared.models.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class MCMetricsAPI {
    private static final String API_BASE_URL = "https://ingest.services.mcmetrics.net/v1";
    private final Gson gson;
    private final String serverId;
    private final String serverKey;
    private final Logger logger;
    private final ExecutorService executorService;

    private final AtomicInteger requestCount;
    private final AtomicInteger errorCount;
    private final Queue<Instant> requestTimes;
    private final Queue<Instant> errorTimes;

    public MCMetricsAPI(String serverId, String serverKey, Logger logger) {
        this.serverId = serverId;
        this.serverKey = serverKey;
        this.logger = logger;

        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                // Convert to UTC if the server timezone is not UTC
                .registerTypeAdapter(Date.class, new JsonSerializer<Date>() {
                    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    {
                        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    }

                    @Override
                    public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
                        return new JsonPrimitive(dateFormat.format(src));
                    }
                })
                .create();

        this.executorService = Executors.newCachedThreadPool();
        this.requestCount = new AtomicInteger(0);
        this.errorCount = new AtomicInteger(0);
        this.requestTimes = new ConcurrentLinkedQueue<>();
        this.errorTimes = new ConcurrentLinkedQueue<>();
    }

    private static class EmptyResponse {
        // Empty response wrapper for void endpoints
    }

    public CompletableFuture<Void> insertServerPing(ServerPing serverPing) {
        return makeRequest("POST", "/insert/server_ping", serverPing, EmptyResponse.class).thenApply(v -> null);
    }

    public CompletableFuture<Void> insertSession(Session session) {
        return makeRequest("POST", "/insert/session", session, EmptyResponse.class).thenApply(v -> null);
    }

    public CompletableFuture<Void> insertPayment(Payment payment) {
        return makeRequest("POST", "/insert/payment", payment, EmptyResponse.class).thenApply(v -> null);
    }

    public CompletableFuture<Void> insertCustomEvent(CustomEvent customEvent) {
        return makeRequest("POST", "/insert/custom_event", customEvent, EmptyResponse.class).thenApply(v -> null);
    }

    public CompletableFuture<Void> insertChatMessage(ChatMessage chatMessage) {
        return makeRequest("POST", "/insert/chat_message", chatMessage, EmptyResponse.class).thenApply(v -> null);
    }

    public CompletableFuture<List<ABTest>> getABTests() {
        return makeRequest("GET", "/ab_tests", null, ABTestResponse.class)
                .thenApply(response -> ((ABTestResponse) response).data.ab_tests);
    }

    private <T, R> CompletableFuture<R> makeRequest(String method, String endpoint, T data, Class<R> responseClass) {
        CompletableFuture<R> future = new CompletableFuture<>();

        executorService.submit(() -> {
            HttpURLConnection connection = null;
            try {
                incrementRequestCount();
                URL url = new URL(API_BASE_URL + endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(45000);
                connection.setReadTimeout(45000);

                // Set headers
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Server-ID", serverId);
                connection.setRequestProperty("X-Server-Key", serverKey);

                if (method.equals("POST") && data != null) {
                    connection.setDoOutput(true);
                    String json = gson.toJson(data);
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = json.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }

                int responseCode = connection.getResponseCode();
                String responseBody;

                if (responseCode >= 200 && responseCode < 300) {
                    responseBody = readInputStream(connection.getInputStream());
                } else {
                    responseBody = readInputStream(connection.getErrorStream());
                }

                handleResponse(responseCode, responseBody, responseClass, future);
            } catch (Exception e) {
                logger.severe("[MCMetrics Debug] Network failure: " + e.getMessage());
                incrementErrorCount();
                future.completeExceptionally(
                        new MCMetricsException("NETWORK_ERROR", "Network error: " + e.getMessage(), logger));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });

        return future;
    }

    private String readInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return response.toString();
    }

    private <R> void handleResponse(int statusCode, String responseStr, Class<R> responseClass,
            CompletableFuture<R> future) {
        if (statusCode >= 200 && statusCode < 300) {
            if (responseClass == Void.class) {
                future.complete(null);
            } else {
                try {
                    R result = gson.fromJson(responseStr, responseClass);
                    future.complete(result);
                } catch (Exception e) {
                    logger.severe("[MCMetrics Debug] Failed to parse response: " + e.getMessage());
                    future.completeExceptionally(new MCMetricsException("PARSE_ERROR",
                            "Failed to parse response: " + e.getMessage(), logger));
                }
            }
        } else {
            incrementErrorCount();
            MCMetricsException exception = parseErrorResponse(responseStr);
            future.completeExceptionally(exception);
        }
    }

    private MCMetricsException parseErrorResponse(String errorBody) {
        try {
            JsonObject jsonObject = JsonParser.parseString(errorBody).getAsJsonObject();
            String error = jsonObject.get("error").getAsString();

            if (error.contains("AUTH_INVALID") || error.contains("AUTH_MISSING")) {
                return new MCMetricsException("AUTH_ERROR", error, logger);
            } else if (error.contains("RATE_LIMIT")) {
                return new MCMetricsException("RATE_LIMIT", error, logger);
            } else if (error.contains("MISC_ERROR")) {
                return new MCMetricsException("MISC_ERROR", error, logger);
            } else {
                return new MCMetricsException("UNKNOWN_ERROR", error, logger);
            }
        } catch (Exception e) {
            return new MCMetricsException("PARSE_ERROR", "Failed to parse error response: " + errorBody, logger);
        }
    }

    private void incrementRequestCount() {
        requestCount.incrementAndGet();
        requestTimes.add(Instant.now());
        cleanupOldEntries(requestTimes);
    }

    private void incrementErrorCount() {
        errorCount.incrementAndGet();
        errorTimes.add(Instant.now());
        cleanupOldEntries(errorTimes);
    }

    private void cleanupOldEntries(Queue<Instant> queue) {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        while (!queue.isEmpty() && queue.peek().isBefore(oneHourAgo)) {
            queue.poll();
        }
    }

    public int getRequestCount() {
        cleanupOldEntries(requestTimes);
        return requestTimes.size();
    }

    public int getErrorCount() {
        cleanupOldEntries(errorTimes);
        return errorTimes.size();
    }

    public static class MCMetricsException extends Exception {
        public MCMetricsException(String errorType, String message, Logger logger) {
            super(message);

            switch (errorType) {
                case "NETWORK_ERROR":
                    logger.severe(
                            "A network error occurred while making a request to the MCMetrics API. Network error: "
                                    + message);
                    break;
                case "AUTH_ERROR":
                    logger.warning(
                            "A network error occurred while making a request to the MCMetrics API. Did you run the /mcmetrics setup command? Error: "
                                    + message);
                    break;
                case "RATE_LIMIT":
                    logger.warning("API rate limit exceeded while making a request to the MCMetrics API: " + message);
                    break;
                case "MISC_ERROR":
                    logger.severe(
                            "A miscellaneous error occurred while making a request to the MCMetrics API: " + message);
                    break;
                case "UNKNOWN_ERROR":
                    logger.severe("Unknown error occurred while making a request to the MCMetrics API: " + message);
                    break;
                case "PARSE_ERROR":
                    logger.severe("Failed to parse error response from the MCMetrics API: " + message);
                    break;
            }
        }
    }

    public void shutdown() {
        try {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("Error shutting down MCMetricsAPI: " + e.getMessage());
        }
    }
}