package net.mcmetrics.plugin.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {
    private final Map<String, YamlDocument> configs = new HashMap<>();

    public void loadConfig(String name, File dataFolder, String fileName, InputStream defaultConfig, Logger logger) throws IOException {
        File configFile = new File(dataFolder, fileName);
        YamlDocument config = YamlDocument.create(configFile, defaultConfig,
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build());

        if (config.update()) {
            logger.info("Configuration file " + fileName + " has been updated with new options.");
        }

        configs.put(name, config);
    }

    public void reloadConfig(String name) throws IOException {
        YamlDocument config = configs.get(name);
        if (config != null) {
            config.reload();
        } else {
            throw new IllegalArgumentException("Config '" + name + "' not found");
        }
    }

    public void saveConfig(String name) throws IOException {
        YamlDocument config = configs.get(name);
        if (config != null) {
            config.save();
        } else {
            throw new IllegalArgumentException("Config '" + name + "' not found");
        }
    }

    public String getString(String name, String path) {
        YamlDocument config = configs.get(name);
        return config != null ? config.getString(path) : null;
    }

    public int getInt(String name, String path) {
        YamlDocument config = configs.get(name);
        return config != null ? config.getInt(path) : 0;
    }

    public boolean getBoolean(String name, String path) {
        YamlDocument config = configs.get(name);
        return config != null && config.getBoolean(path);
    }

    public double getDouble(String name, String path) {
        YamlDocument config = configs.get(name);
        return config != null ? config.getDouble(path) : 0.0;
    }

    public List<?> getList(String name, String path) {
        YamlDocument config = configs.get(name);
        return config != null ? config.getList(path) : null;
    }

    public <T> T get(String name, String path, Class<T> type) {
        YamlDocument config = configs.get(name);
        return config != null ? (T) config.get(path, type) : null;
    }

    public void set(String name, String path, Object value) {
        YamlDocument config = configs.get(name);
        if (config != null) {
            config.set(path, value);
        } else {
            throw new IllegalArgumentException("Config '" + name + "' not found");
        }
    }
}