package net.mcmetrics.plugin;

import net.mcmetrics.sdk.MCMetricsAPI;
import net.mcmetrics.sdk.models.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<UUID, Session> activeSessions = new ConcurrentHashMap<>();
    private final MCMetricsAPI api;

    public SessionManager(MCMetricsAPI api) {
        this.api = api;
    }

    public void startSession(UUID playerId, Session session) {
        activeSessions.put(playerId, session);
    }

    public Session getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public Session endSession(UUID playerId) {
        return activeSessions.remove(playerId);
    }

    public List<Session> endAllSessions() {
        List<Session> endedSessions = new ArrayList<>(activeSessions.values());
        activeSessions.clear();
        return endedSessions;
    }
}