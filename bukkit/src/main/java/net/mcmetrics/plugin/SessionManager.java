package net.mcmetrics.plugin;

import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.CustomEvent;
import net.mcmetrics.shared.models.Payment;
import net.mcmetrics.shared.models.Session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<UUID, Session> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, List<CustomEvent>> sessionCustomEvents = new ConcurrentHashMap<>();
    private final Map<UUID, List<Payment>> sessionPayments = new ConcurrentHashMap<>();
    private final MCMetricsAPI api;

    public SessionManager(MCMetricsAPI api) {
        this.api = api;
    }

    public void startSession(UUID playerId, Session session) {
        activeSessions.put(playerId, session);
        sessionCustomEvents.put(playerId, new ArrayList<>());
        sessionPayments.put(playerId, new ArrayList<>());
    }

    public Session getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public Session endSession(UUID playerId) {
        sessionCustomEvents.remove(playerId);
        sessionPayments.remove(playerId);
        return activeSessions.remove(playerId);
    }

    public List<Session> endAllSessions() {
        List<Session> endedSessions = new ArrayList<>(activeSessions.values());
        activeSessions.clear();
        sessionCustomEvents.clear();
        sessionPayments.clear();
        return endedSessions;
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public void addCustomEvent(UUID playerId, CustomEvent event) {
        sessionCustomEvents.computeIfAbsent(playerId, k -> new ArrayList<>()).add(event);
    }

    public void addPayment(UUID playerId, Payment payment) {
        sessionPayments.computeIfAbsent(playerId, k -> new ArrayList<>()).add(payment);
    }

    public List<CustomEvent> getSessionCustomEvents(UUID playerId) {
        return sessionCustomEvents.getOrDefault(playerId, new ArrayList<>());
    }

    public List<Payment> getSessionPayments(UUID playerId) {
        return sessionPayments.getOrDefault(playerId, new ArrayList<>());
    }

    public Map<String, Integer> getGroupedCustomEvents(UUID playerId) {
        Map<String, Integer> groupedEvents = new HashMap<>();
        for (CustomEvent event : getSessionCustomEvents(playerId)) {
            groupedEvents.merge(event.event_type, 1, Integer::sum);
        }
        return groupedEvents;
    }
}