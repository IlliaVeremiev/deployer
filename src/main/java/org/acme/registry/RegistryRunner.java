package org.acme.registry;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class RegistryRunner {

    /** Login to a container registry. Password is piped via stdin. */
    public static void login(String registryHost, String username, String password, PrintStream out) throws IOException, InterruptedException {
        if (username == null || username.isEmpty()) throw new IOException("registry-username is required");
        if (password == null || password.isEmpty()) throw new IOException("registry-password is required");

        if (out != null) out.printf("🔑 Logging in to %s...%n", registryHost);

        ProcessBuilder pb = new ProcessBuilder("docker", "login", registryHost, "-u", username, "--password-stdin")
                .redirectErrorStream(true);

        Process proc = pb.start();
        try (OutputStream stdin = proc.getOutputStream()) {
            stdin.write(password.getBytes(StandardCharsets.UTF_8));
        }

        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IOException("docker login failed: " + output.trim());
        }

        if (out != null) out.println("✅ Logged in");
    }

    /** Push an image to the registry. Docker output goes to stdout/stderr. */
    public static void push(String imageName, PrintStream out) throws IOException, InterruptedException {
        if (out != null) out.printf("📦 Pushing %s...%n", imageName);

        ProcessBuilder pb = new ProcessBuilder("docker", "push", imageName)
                .inheritIO();

        int exit = pb.start().waitFor();
        if (exit != 0) throw new IOException("docker push failed with exit code " + exit);

        if (out != null) out.println("✅ Image pushed");
    }
}
