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
    public List<ABTestExposure> ab_test_exposure; @Nullable // null for bungee/velocity implementations

    /*
    when a new customer installs mcmetrics, it is likely that the plugin is being installed
    mid-lifecycle of their server, i.e. players have already played before they installed the plugin
    if this is the case, we do not want to count these players towards joins, retention, etc. on the dashboard
    since we're obviously missing data from before the plugin was installed
    this flag is set to true if the player has played before and is not in the legacy player list
    and the ingest API will handle this flag accordingly so that dashboard queries can filter out these players
     */
    public boolean potential_legacy = false;
}