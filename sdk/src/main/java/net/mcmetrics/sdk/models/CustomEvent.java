package net.mcmetrics.sdk.models;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class CustomEvent {
    public UUID player_uuid;
    public String event_type;
    public Date timestamp;
    public Map<String, Object> metadata;
}