package net.mcmetrics.plugin.listeners;

import net.mcmetrics.plugin.MCMetricsSpigotPlugin;
import net.mcmetrics.shared.models.CustomEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsoleEventListener implements Listener {
    private final MCMetricsSpigotPlugin plugin;
    private final Map<Pattern, ConsoleEventConfig> eventPatterns = new HashMap<>();
    private static final Pattern TIMESTAMP_PATTERN = Pattern
            .compile("\\[\\d{2}:\\d{2}:\\d{2}(?:\\s+[A-Z]+)?\\]:\\s*(.*)");
    private ConsoleAppender appender;

    public ConsoleEventListener(MCMetricsSpigotPlugin plugin) {
        this.plugin = plugin;
        loadEventPatterns();
        setupConsoleAppender();
    }

    private void setupConsoleAppender() {
        appender = new ConsoleAppender();
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
    }

    private class ConsoleAppender extends AbstractAppender {
        protected ConsoleAppender() {
            super("MCMetrics-Console-Appender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            String message = event.getMessage().getFormattedMessage();
            // Process on the main thread to avoid concurrent modification issues
            Bukkit.getScheduler().runTask(plugin, () -> processConsoleMessage(message));
        }
    }

    public void shutdown() {
        if (appender != null) {
            // Stop first, so it stops accepting new log events
            appender.stop();
            // Then remove from root logger
            ((Logger) LogManager.getRootLogger()).removeAppender(appender);
        }
    }

    public void loadEventPatterns() {
        eventPatterns.clear();
        List<Map<String, Object>> customEvents = plugin.getConfigManager().getCustomEvents("main");

        int successfulPatterns = 0;
        plugin.getLogger().info("Loading console event patterns...");

        for (Map<String, Object> event : customEvents) {
            String type = (String) event.get("type");
            if (!"console".equals(type)) {
                continue;
            }

            String name = (String) event.get("name");
            String consolePattern = (String) event.get("console-pattern");
            Integer playerField = (Integer) event.get("player-field");

            plugin.getLogger().info("Found console event config: " + name + " (pattern: " + consolePattern + ")");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metadata = (List<Map<String, Object>>) event.get("metadata");

            ConsoleEventConfig config = new ConsoleEventConfig(
                    name,
                    playerField,
                    parseMetadataConfig(metadata));

            try {
                Pattern pattern = Pattern.compile(consolePattern);
                eventPatterns.put(pattern, config);
                successfulPatterns++;
                plugin.getLogger().info("Successfully compiled pattern for: " + name);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid console pattern for event " + name + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + successfulPatterns + " console event patterns successfully");
    }

    private List<MetadataField> parseMetadataConfig(List<Map<String, Object>> metadata) {
        List<MetadataField> fields = new ArrayList<>();
        if (metadata == null)
            return fields;

        for (Map<String, Object> field : metadata) {
            for (Map.Entry<String, Object> entry : field.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldConfig = (Map<String, Object>) entry.getValue();
                String key = (String) fieldConfig.get("key");
                Integer fieldIndex = (Integer) fieldConfig.get("field");
                if (key != null && fieldIndex != null) {
                    fields.add(new MetadataField(key, fieldIndex));
                }
            }
        }
        return fields;
    }

    public void processConsoleMessage(String message) {

        // Try matching both the original message and the message with timestamp removed
        String messageWithoutTimestamp = message;
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(message);
        if (timestampMatcher.matches()) {
            messageWithoutTimestamp = timestampMatcher.group(1);
        }

        // Try both versions of the message
        if (!tryMatchMessage(message)) {
            tryMatchMessage(messageWithoutTimestamp);
        }
    }

    private boolean tryMatchMessage(String message) {
        for (Map.Entry<Pattern, ConsoleEventConfig> entry : eventPatterns.entrySet()) {
            Matcher matcher = entry.getKey().matcher(message);

            if (matcher.matches()) {
                ConsoleEventConfig config = entry.getValue();

                String playerIdentifier = null;
                if (config.playerField <= matcher.groupCount()) {
                    playerIdentifier = matcher.group(config.playerField);
                }

                if (playerIdentifier == null) {
                    plugin.getLogger().warning(
                            "Could not find player identifier in console message for event " + config.eventName);
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                for (MetadataField field : config.metadataFields) {
                    if (field.fieldIndex <= matcher.groupCount()) {
                        metadata.put(field.key, matcher.group(field.fieldIndex));
                    }
                }

                final String finalPlayerIdentifier = playerIdentifier;

                // fire the event asynchronously
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID playerUuid = resolvePlayerIdentifier(finalPlayerIdentifier);
                        if (playerUuid != null) {
                            CustomEvent customEvent = new CustomEvent();
                            customEvent.player_uuid = playerUuid;
                            customEvent.event_type = config.eventName;
                            customEvent.timestamp = new Date();
                            customEvent.metadata = metadata;

                            plugin.getApi().insertCustomEvent(customEvent)
                                    .thenRun(() -> {
                                        if (plugin.getConfigManager().getBoolean("main", "debug")) {
                                            plugin.getLogger().info(
                                                    "Console-triggered custom event recorded: " + config.eventName);
                                        }
                                    })
                                    .exceptionally(e -> {
                                        if (plugin.getConfigManager().getBoolean("main", "debug")) {
                                            plugin.getLogger().warning(
                                                    "Failed to record console-triggered custom event: "
                                                            + e.getMessage());
                                        }
                                        return null;
                                    });
                        }
                    }
                }.runTaskAsynchronously(plugin);
                return true;
            }
        }
        return false;
    }

    private UUID resolvePlayerIdentifier(String identifier) {
        // Try parsing as UUID first
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ignored) {
            // If not a UUID, try to find player by name
            Player player = Bukkit.getPlayer(identifier);
            return player != null ? player.getUniqueId() : null;
        }
    }

    public static class EventPatternInfo {
        private final String name;
        private final String pattern;
        private final int playerField;

        public EventPatternInfo(String name, String pattern, int playerField) {
            this.name = name;
            this.pattern = pattern;
            this.playerField = playerField;
        }

        public String getName() {
            return name;
        }

        public String getPattern() {
            return pattern;
        }

        public int getPlayerField() {
            return playerField;
        }
    }

    public List<EventPatternInfo> getEventPatternsInfo() {
        List<EventPatternInfo> patterns = new ArrayList<>();
        for (Map.Entry<Pattern, ConsoleEventConfig> entry : eventPatterns.entrySet()) {
            patterns.add(new EventPatternInfo(
                    entry.getValue().eventName,
                    entry.getKey().pattern(),
                    entry.getValue().playerField));
        }
        return patterns;
    }

    private static class ConsoleEventConfig {
        final String eventName;
        final int playerField;
        final List<MetadataField> metadataFields;

        ConsoleEventConfig(String eventName, int playerField, List<MetadataField> metadataFields) {
            this.eventName = eventName;
            this.playerField = playerField;
            this.metadataFields = metadataFields;
        }
    }

    private static class MetadataField {
        final String key;
        final int fieldIndex;

        MetadataField(String key, int fieldIndex) {
            this.key = key;
            this.fieldIndex = fieldIndex;
        }
    }
}