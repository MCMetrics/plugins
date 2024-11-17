package net.mcmetrics.shared.models;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ABTest {
    public UUID id;
    public String name;
    public TriggerType trigger;
    public List<ABTestVariant> variants;
    public Date created_at;

    public enum TriggerType {
        Join("Join"),
        FirstJoin("First Join"),
        Purchase("Purchase"),
        Command("Command"),
        Placeholder("Placeholder"); //TODO: Not implemented yet

        private final String value;

        TriggerType(String value) {
            this.value = value;
        }

        public static TriggerType fromString(String text) {
            for (TriggerType type : TriggerType.values()) {
                if (type.value.equalsIgnoreCase(text)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown trigger type: " + text);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static class ABTestVariant {
        public String name;
        public ActionType action;
        public int weight;
        public String payload;

        public enum ActionType {
            Control("Control"),
            ConsoleCommand("ConsoleCommand"),
            PlayerCommand("PlayerCommand"),
            PlayerMessage("PlayerMessage");

            private final String value;

            ActionType(String value) {
                this.value = value;
            }

            public static ActionType fromString(String text) {
                for (ActionType type : ActionType.values()) {
                    if (type.value.equalsIgnoreCase(text)) {
                        return type;
                    }
                }
                throw new IllegalArgumentException("Unknown action type: " + text);
            }

            @Override
            public String toString() {
                return value;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" (").append(weight).append("%)");
            if (action != ActionType.Control) {
                sb.append(" - ").append(action);
                if (!payload.isEmpty()) {
                    sb.append(": ").append(payload);
                }
            }
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("A/B Test: ").append(name).append("\n");
        sb.append("ID: ").append(id).append("\n");
        sb.append("Trigger: ").append(trigger).append("\n");
        sb.append("Created: ").append(created_at).append("\n");
        sb.append("Variants:\n");
        for (ABTestVariant variant : variants) {
            sb.append("  - ").append(variant.toString()).append("\n");
        }
        return sb.toString();
    }
}