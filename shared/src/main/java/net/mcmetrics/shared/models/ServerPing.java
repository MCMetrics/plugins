package net.mcmetrics.shared.models;

import java.util.Date;

public class ServerPing {
    public Date time;
    public int player_count;
    public int java_player_count;
    public int bedrock_player_count;
    public double tps;
    public double mspt;
    public double cpu_percent;
    public double ram_percent;
    public int entities_loaded;
    public int chunks_loaded;
}