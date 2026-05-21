package org.acme.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    /** Load .env.production from an explicit path. Returns empty map if file doesn't exist. */
    public static Map<String, String> loadEnvFile(Path envFile) throws IOException {
        if (!Files.exists(envFile)) return new LinkedHashMap<>();
        return parseDotenv(Files.readString(envFile));
    }

    /**
     * Extract the registry host from an image name.
     * ghcr.io/user/img:tag  -> ghcr.io
     * user/img:tag          -> docker.io
     * img                   -> docker.io
     */
    public static String registryHost(String imageName) {
        String[] parts = imageName.split("/");
        if (parts.length > 1) {
            String first = parts[0];
            if (first.contains(".") || first.contains(":")) {
                return first;
            }
        }
        return "docker.io";
    }

    /**
     * Load a global deployer config YAML file (~/.deployer.yaml or ./.deployer.yaml).
     * Returns an empty map if no config file is found.
     */
    public static Map<String, String> loadGlobalConfig(String configFilePath) {
        List<Path> candidates = new ArrayList<>();
        if (configFilePath != null && !configFilePath.isEmpty()) {
            candidates.add(Paths.get(configFilePath));
        } else {
            String home = System.getProperty("user.home");
            if (home != null) candidates.add(Paths.get(home, ".deployer.yaml"));
            candidates.add(Paths.get(".deployer.yaml"));
        }
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    return parseSimpleYaml(Files.readString(candidate));
                } catch (IOException e) {
                    System.err.println("Warning: could not read config file " + candidate + ": " + e.getMessage());
                }
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * Resolve a config value: CLI/env flag value takes priority, then config file.
     * The flagValue already includes env var fallback via Picocli's defaultValue.
     */
    public static String resolve(String flagValue, String configKey, Map<String, String> globalConfig) {
        if (flagValue != null && !flagValue.isEmpty()) return flagValue;
        return globalConfig.getOrDefault(configKey, "");
    }

    // --- Parsers ---

    /** Parse dotenv format: KEY=VALUE, ignoring comments and blank lines */
    public static Map<String, String> parseDotenv(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0) {
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                value = stripQuotes(value);
                result.put(key, value);
            }
        }
        return result;
    }

    /** Parse simple flat YAML: key: value, ignoring comments and blank lines */
    public static Map<String, String> parseSimpleYaml(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(": ");
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 2).trim();
                value = stripQuotes(value);
                result.put(key, value);
            }
        }
        return result;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0), last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}
