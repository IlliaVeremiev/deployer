package org.acme.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonoConfigLoader {

    public static MonoConfig load(String cwd) throws IOException {
        Path deployYml = Paths.get(cwd, "deploy.yml");
        if (!Files.exists(deployYml)) {
            throw new IOException("deploy.yml not found in " + cwd
                    + "\nCreate a deploy.yml file with a 'stack:' and 'services:' section.");
        }
        return parse(Files.readString(deployYml), deployYml.getParent().toAbsolutePath().normalize());
    }

    /**
     * Parse deploy.yml content. Format (2-space indents):
     *
     *   stack: myapp
     *   services:
     *     api:
     *       project-root: ./backend
     *       context-root: ./backend
     *       image-name: ghcr.io/user/api:latest
     *       route: myapp-api
     *       build-args:
     *         - FOO=bar
     *       env-file: ./custom/.env          # optional
     *       compose-file: ./custom/dc.yml    # optional
     */
    public static MonoConfig parse(String content, Path deployYmlDir) {
        MonoConfig config = new MonoConfig();
        config.deployYmlDir = deployYmlDir;

        ServiceConfig currentService = null;
        String currentListKey = null;
        boolean inServices = false;

        for (String rawLine : content.split("\n")) {
            // Strip inline comments and trailing whitespace
            String stripped = rawLine;
            int hashIdx = indexOfUnquotedHash(rawLine);
            if (hashIdx >= 0) stripped = rawLine.substring(0, hashIdx);
            stripped = stripped.stripTrailing();

            if (stripped.isBlank()) continue;

            int indent = leadingSpaces(stripped);
            String line = stripped.trim();

            if (indent == 0) {
                currentService = null;
                currentListKey = null;

                if (line.equals("services:")) {
                    inServices = true;
                } else {
                    inServices = false;
                    String[] kv = splitKV(line);
                    if (kv != null && "stack".equals(kv[0])) {
                        config.stack = kv[1];
                    }
                }
            } else if (indent == 2 && inServices) {
                // Service ID line: "  api:"
                if (line.endsWith(":") && !line.contains(": ")) {
                    currentService = new ServiceConfig();
                    currentService.id = line.substring(0, line.length() - 1).trim();
                    config.services.put(currentService.id, currentService);
                    currentListKey = null;
                }
            } else if (indent == 4 && currentService != null) {
                if (line.endsWith(":") && !line.contains(": ")) {
                    // List header like "build-args:"
                    currentListKey = line.substring(0, line.length() - 1).trim();
                } else {
                    currentListKey = null;
                    String[] kv = splitKV(line);
                    if (kv != null) applyServiceProperty(currentService, kv[0], kv[1]);
                }
            } else if (indent == 6 && currentService != null && currentListKey != null) {
                if (line.startsWith("- ")) {
                    String item = line.substring(2).trim();
                    if ("build-args".equals(currentListKey)) {
                        currentService.buildArgs.add(item);
                    }
                }
            }
        }
        return config;
    }

    private static void applyServiceProperty(ServiceConfig svc, String key, String value) {
        switch (key) {
            case "project-root"  -> svc.projectRoot  = value;
            case "context-root"  -> svc.contextRoot  = value;
            case "image-name"    -> svc.imageName    = value;
            case "route"         -> svc.route        = value;
            case "env-file"      -> svc.envFile      = value;
            case "compose-file"  -> svc.composeFile  = value;
        }
    }

    /** Split "key: value" into {"key","value"}, or "key:" into {"key",""}. Returns null if no colon. */
    private static String[] splitKV(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) return null;
        String key = line.substring(0, colon).trim();
        String value = line.substring(colon + 1).trim();
        // Strip quotes
        if (value.length() >= 2) {
            char f = value.charAt(0), l = value.charAt(value.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return new String[]{key, value};
    }

    private static int leadingSpaces(String s) {
        int count = 0;
        while (count < s.length() && s.charAt(count) == ' ') count++;
        return count;
    }

    /** Find the index of a '#' that is not inside a quoted string. Returns -1 if none. */
    private static int indexOfUnquotedHash(String line) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '#' && !inSingle && !inDouble) return i;
        }
        return -1;
    }
}
