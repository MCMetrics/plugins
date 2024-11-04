package net.mcmetrics.shared.models;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Session {
    public UUID player_uuid;
    public String player_username;
    public Date session_start;
    public Date session_end;
    public String domain;
    public Long afk_time_ms; @Nullable // null for bungee/velocity implementations
    public String ip_address;
    public List<ABTestExposure> ab_test_exposure; //@Nullable // null for bungee/velocity implementations
}