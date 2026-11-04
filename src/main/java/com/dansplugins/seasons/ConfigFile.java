package com.dansplugins.seasons;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Checks that {@code config.yml} can be read before it is used.
 *
 * <p>{@code JavaPlugin#getConfig} logs a broken file and carries on with a blank configuration, which
 * would quietly replace every setting with its default. A shorter default season would then clamp
 * the saved date, and saving it on disable would make that loss permanent.
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
}
