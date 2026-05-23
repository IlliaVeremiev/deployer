package org.acme.docker;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DockerRunner {

    /** Build a Docker image. Uses explicit Dockerfile path; context is contextDir. */
    public static void build(
            Path contextDir,
            Path dockerfilePath,
            String imageName,
            List<String> buildArgs,
            Path envFile,
            boolean debug,
            PrintStream out) throws IOException, InterruptedException {

        if (out != null) out.printf("🔨 Building %s...%n", imageName);

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "build",
                "--file", dockerfilePath.toAbsolutePath().toString(),
                "--tag", imageName
        ));
        if (debug) cmd.add("--debug");
        for (String arg : buildArgs) {
            cmd.add("--build-arg");
            cmd.add(arg);
        }
        boolean hasEnvFile = envFile != null && Files.exists(envFile);
        if (hasEnvFile) {
            cmd.add("--secret");
            cmd.add("id=envfile,src=" + envFile.toAbsolutePath());
        }
        cmd.add(contextDir.toAbsolutePath().toString());

        if (out != null) {
            out.printf("   $ %s%n", String.join(" ", cmd));
            out.printf("   context: %s%n", contextDir.toAbsolutePath());
            if (hasEnvFile) out.printf("   env    : %s (passed as build secret)%n", envFile.toAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .inheritIO();
        pb.environment().put("DOCKER_BUILDKIT", "1");

        int exit = pb.start().waitFor();
        if (exit != 0) throw new IOException("docker build failed with exit code " + exit);

        if (out != null) out.println("✅ Build complete");
    }
}
