package net.mcmetrics.sdk.models;

import java.util.Date;
import java.util.UUID;

public class Payment {
    public String transaction_id;
    public UUID player_uuid;
    public String platform;
    public double amount;
    public String currency;
    public Date datetime;
}