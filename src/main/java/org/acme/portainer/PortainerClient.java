package org.acme.portainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortainerClient {

    private final String baseUrl;
    private final String token;
    private final int endpointId;
    private final HttpClient http;
    private final boolean verbose;

    public PortainerClient(String baseUrl, String token, int endpointId, java.io.PrintWriter warnings) throws IllegalArgumentException {
        this(baseUrl, token, endpointId, warnings, false);
    }

    public PortainerClient(String baseUrl, String token, int endpointId, java.io.PrintWriter warnings, boolean verbose) throws IllegalArgumentException {
        if (baseUrl == null || baseUrl.isEmpty()) throw new IllegalArgumentException("portainer-url is required");
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("portainer-url must be a valid URL (http:// or https://)");
        }
        if (endpointId <= 0) throw new IllegalArgumentException("portainer-endpoint must be a positive integer");
        if (baseUrl.startsWith("http://") && warnings != null) {
            warnings.println("Warning: Portainer URL uses plain HTTP — credentials will be sent unencrypted");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.endpointId = endpointId;
        this.verbose = verbose;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    private void logRequest(String method, String url) {
        if (verbose) System.err.println("[verbose] --> " + method + " " + url);
    }

    private void logResponse(HttpResponse<String> resp) {
        if (verbose) {
            System.err.println("[verbose] <-- HTTP " + resp.statusCode());
            String body = resp.body();
            if (body != null && !body.isEmpty()) System.err.println("[verbose] " + body);
        }
    }

    /** Find a stack by name. Returns null if not found. */
    public Stack findStack(String name) throws IOException, InterruptedException {
        String url = baseUrl + "/api/stacks";
        logRequest("GET", url);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", token)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new java.net.ConnectException("Cannot connect to Portainer at " + url + " — " + e.getMessage());
        }
        logResponse(resp);
        if (resp.statusCode() >= 400) {
            throw new IOException("Portainer API error listing stacks: HTTP " + resp.statusCode());
        }
        return parseStacks(resp.body()).stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** Create a new stack */
    public void createStack(String name, String composeContent, List<EnvVar> env) throws IOException, InterruptedException {
        String body = buildCreatePayload(name, composeContent, env);
        String url = baseUrl + "/api/stacks/create/standalone/string?endpointId=" + endpointId;
        logRequest("POST", url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new java.net.ConnectException("Cannot connect to Portainer at " + url + " — " + e.getMessage());
        }
        logResponse(resp);
        if (resp.statusCode() >= 400) {
            throw new IOException("Portainer API error creating stack: HTTP " + resp.statusCode() + " — " + resp.body());
        }
    }

    /** Update an existing stack */
    public void updateStack(int id, String composeContent, List<EnvVar> env) throws IOException, InterruptedException {
        String body = buildUpdatePayload(composeContent, env);
        String url = baseUrl + "/api/stacks/" + id + "?endpointId=" + endpointId;
        logRequest("PUT", url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new java.net.ConnectException("Cannot connect to Portainer at " + url + " — " + e.getMessage());
        }
        logResponse(resp);
        if (resp.statusCode() >= 400) {
            throw new IOException("Portainer API error updating stack: HTTP " + resp.statusCode() + " — " + resp.body());
        }
    }

    /** Deploy: create or update based on whether the stack exists */
    public void deploy(String name, String composeContent, List<EnvVar> env) throws IOException, InterruptedException {
        Stack existing = findStack(name);
        if (existing != null) {
            updateStack(existing.id(), composeContent, env);
        } else {
            createStack(name, composeContent, env);
        }
    }

    // --- JSON helpers ---

    private List<Stack> parseStacks(String json) {
        List<Stack> stacks = new ArrayList<>();
        // Extract top-level objects from the JSON array, then parse Id and Name from each
        List<String> objects = extractTopLevelObjects(json);
        Pattern idPat = Pattern.compile("\"Id\"\\s*:\\s*(\\d+)");
        Pattern namePat = Pattern.compile("\"Name\"\\s*:\\s*\"([^\"]+)\"");
        for (String obj : objects) {
            Matcher idM = idPat.matcher(obj);
            Matcher nameM = namePat.matcher(obj);
            if (idM.find() && nameM.find()) {
                stacks.add(new Stack(Integer.parseInt(idM.group(1)), nameM.group(1)));
            }
        }
        return stacks;
    }

    /** Extract the text of each top-level JSON object from an array, handling nested objects/arrays. */
    private List<String> extractTopLevelObjects(String json) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    private String buildCreatePayload(String name, String composeContent, List<EnvVar> env) {
        return "{\"Name\":" + jsonString(name) +
                ",\"StackFileContent\":" + jsonString(composeContent) +
                ",\"Env\":" + buildEnvArray(env) + "}";
    }

    private String buildUpdatePayload(String composeContent, List<EnvVar> env) {
        return "{\"StackFileContent\":" + jsonString(composeContent) +
                ",\"PullImage\":true" +
                ",\"Env\":" + buildEnvArray(env) + "}";
    }

    private String buildEnvArray(List<EnvVar> env) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < env.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":").append(jsonString(env.get(i).name()))
              .append(",\"value\":").append(jsonString(env.get(i).value())).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }
}
