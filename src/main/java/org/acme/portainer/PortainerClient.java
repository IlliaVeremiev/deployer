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

    public PortainerClient(String baseUrl, String token, int endpointId, java.io.PrintWriter warnings) throws IllegalArgumentException {
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
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Find a stack by name. Returns null if not found. */
    public Stack findStack(String name) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/stacks"))
                .header("X-API-Key", token)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
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

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("Portainer API error creating stack: HTTP " + resp.statusCode() + " — " + resp.body());
        }
    }

    /** Update an existing stack */
    public void updateStack(int id, String composeContent, List<EnvVar> env) throws IOException, InterruptedException {
        String body = buildUpdatePayload(composeContent, env);
        String url = baseUrl + "/api/stacks/" + id + "?endpointId=" + endpointId;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
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
        // Match objects containing "Id" and "Name" fields (order may vary)
        Pattern p = Pattern.compile("\\{[^}]*\"Id\"\\s*:\\s*(\\d+)[^}]*\"Name\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}|\\{[^}]*\"Name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"Id\"\\s*:\\s*(\\d+)[^}]*\\}");
        Matcher m = p.matcher(json);
        while (m.find()) {
            if (m.group(1) != null) {
                stacks.add(new Stack(Integer.parseInt(m.group(1)), m.group(2)));
            } else {
                stacks.add(new Stack(Integer.parseInt(m.group(4)), m.group(3)));
            }
        }
        return stacks;
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
