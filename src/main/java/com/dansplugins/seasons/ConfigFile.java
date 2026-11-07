package com.dansplugins.seasons;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Checks that {@code config.yml} can be read before it is used.
 *
 * <p>{@code JavaPlugin#getConfig} logs a broken file and carries on with a blank configuration, which
 * would quietly replace every setting with its default. A shorter default season would then clamp
 * the saved date, and saving it on disable would make that loss permanent. A misshapen section does
 * the same to the settings inside it, because only the exact path of each setting is ever read.
 */
final class ConfigFile {

    private ConfigFile() {
    }

    /**
     * Throws if the file is not valid YAML. An empty file is valid: every key is simply missing.
     *
     * @throws IOException if the file cannot be read or parsed; the message names the file
     */
    static void requireReadable(File file) throws IOException {
        try {
            new YamlConfiguration().load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IOException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Throws if the section at the given path is set to a plain value, or holds a key other than the
     * given ones. A section that is not there is fine: its settings simply use their defaults. The
     * configuration's own defaults are not looked at, so only what the file says is checked.
     *
     * @throws IllegalArgumentException if the section is misshapen; the message names the path
     */
    static void requireOnlyKeys(ConfigurationSection config, String path, Collection<String> keys) {
        Object value = config.get(path, null);
        if (value == null) {
            return;
        }
        if (!(value instanceof ConfigurationSection)) {
            throw new IllegalArgumentException(path + " must be a section holding "
                    + String.join(", ", keys) + ", but was '" + value + "'");
        }
        for (String key : ((ConfigurationSection) value).getKeys(false)) {
            if (!keys.contains(key)) {
                throw new IllegalArgumentException(path + "." + key + " is not a setting. Use "
                        + String.join(", ", keys) + ".");
            }
        }
    }
}
