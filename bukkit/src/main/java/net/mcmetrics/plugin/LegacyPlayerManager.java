package net.mcmetrics.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class LegacyPlayerManager {
    private static final String FILENAME = "legacy_players.json";
    private final File dataFile;
    private final Set<UUID> knownPlayers;
    private final ReadWriteLock lock;
    private final Gson gson;
    private final Logger logger;

    public LegacyPlayerManager(File dataFolder, Logger logger) {
        this.logger = logger;
        this.dataFile = new File(dataFolder, FILENAME);
        this.knownPlayers = new HashSet<>();
        this.lock = new ReentrantReadWriteLock();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadData();
    }

    private void loadData() {
        lock.writeLock().lock();
        try {
            if (!dataFile.exists()) {
                if (!dataFile.createNewFile()) {
                    logger.severe("Failed to create legacy players file!");
                    return;
                }
                saveData(new HashSet<>());
            }

            try (Reader reader = new FileReader(dataFile)) {
                Type setType = new TypeToken<HashSet<UUID>>(){}.getType();
                Set<UUID> loadedData = gson.fromJson(reader, setType);
                if (loadedData != null) {
                    knownPlayers.clear();
                    knownPlayers.addAll(loadedData);
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to load legacy players data: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveData(Set<UUID> data) {
        lock.writeLock().lock();
        try (Writer writer = new FileWriter(dataFile)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            logger.severe("Failed to save legacy players data: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isKnownPlayer(UUID playerUuid) {
        lock.readLock().lock();
        try {
            return knownPlayers.contains(playerUuid);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addKnownPlayer(UUID playerUuid) {
        lock.writeLock().lock();
        try {
            knownPlayers.add(playerUuid);
            saveData(knownPlayers);
        } finally {
            lock.writeLock().unlock();
        }
    }
}