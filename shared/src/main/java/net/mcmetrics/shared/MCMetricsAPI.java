package net.mcmetrics.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcmetrics.shared.models.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class MCMetricsAPI {
    private static final String API_BASE_URL = "https://ingest.mcmetrics.net/v1";
    private final CloseableHttpClient httpClient;
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

        // The API may take multiple seconds to respond, per request
        // So we need a sizable connection pool limit
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(1000);
        connectionManager.setDefaultMaxPerRoute(500);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(45 * 1000)
                .setSocketTimeout(45 * 1000)
                .setConnectionRequestTimeout(45 * 1000)
                .build();

        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
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
            try {
                incrementRequestCount();
                String url = API_BASE_URL + endpoint;

                if (method.equals("POST")) {
                    HttpPost request = new HttpPost(url);
                    request.addHeader("Content-Type", "application/json");
                    request.addHeader("X-Server-ID", serverId);
                    request.addHeader("X-Server-Key", serverKey);

                    if (data != null) {
                        String json = gson.toJson(data);
                        request.setEntity(new StringEntity(json));
                    }

                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        handleResponse(response, responseClass, future);
                    }
                } else if (method.equals("GET")) {
                    HttpGet request = new HttpGet(url);
                    request.addHeader("Content-Type", "application/json");
                    request.addHeader("X-Server-ID", serverId);
                    request.addHeader("X-Server-Key", serverKey);

                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        handleResponse(response, responseClass, future);
                    }
                }
            } catch (Exception e) {
                logger.severe("[MCMetrics Debug] Network failure: " + e.getMessage());
                incrementErrorCount();
                future.completeExceptionally(
                        new MCMetricsException("NETWORK_ERROR", "Network error: " + e.getMessage(), logger));
            }
        });

        return future;
    }

    private <R> void handleResponse(CloseableHttpResponse response, Class<R> responseClass, CompletableFuture<R> future)
            throws IOException {
        HttpEntity entity = response.getEntity();
        String responseStr = entity != null ? EntityUtils.toString(entity) : "No response body";

        if (response.getStatusLine().getStatusCode() >= 200 && response.getStatusLine().getStatusCode() < 300) {
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
            httpClient.close();
        } catch (Exception e) {
            logger.warning("Error shutting down MCMetricsAPI: " + e.getMessage());
        }
    }
}