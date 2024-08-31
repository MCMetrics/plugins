package net.mcmetrics.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mcmetrics.shared.models.*;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MCMetricsAPI {
    private static final String API_BASE_URL = "https://ingest.mcmetrics.net/v1";
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String serverId;
    private final String serverKey;
    private final Logger logger;

    public MCMetricsAPI(String serverId, String serverKey, Logger logger) {
        this.serverId = serverId;
        this.serverKey = serverKey;
        this.logger = logger;
        this.httpClient = new OkHttpClient.Builder().build();
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .create();
    }

    public CompletableFuture<Void> insertServerPing(ServerPing serverPing) {
        return makeRequest("/insert/server_ping", serverPing);
    }

    public CompletableFuture<Void> insertSession(Session session) {
        return makeRequest("/insert/session", session);
    }

    public CompletableFuture<Void> insertPayment(Payment payment) {
        return makeRequest("/insert/payment", payment);
    }

    public CompletableFuture<Void> insertCustomEvent(CustomEvent customEvent) {
        return makeRequest("/insert/custom_event", customEvent);
    }

    private <T> CompletableFuture<Void> makeRequest(String endpoint, T data) {
        String json = gson.toJson(data);
        Request request = new Request.Builder()
                .url(API_BASE_URL + endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Server-ID", serverId)
                .addHeader("X-Server-Key", serverKey)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        CompletableFuture<Void> future = new CompletableFuture<>();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.severe("Error making request to " + endpoint + ": " + e.getMessage());
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBody = responseBody != null ? responseBody.string() : "No error body";
                        future.completeExceptionally(new IOException("Unexpected code " + response + ", body: " + errorBody));
                    } else {
                        future.complete(null);
                    }
                }
            }
        });

        return future;
    }
}