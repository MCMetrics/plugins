package net.mcmetrics.shared.models;

import java.util.Date;
import java.util.UUID;

public class ChatMessage {
    public UUID player_uuid;
    public String player_username;
    public String message;
    public Date timestamp = new Date();
}