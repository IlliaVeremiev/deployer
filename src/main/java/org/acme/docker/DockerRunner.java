package org.acme.docker;

import java.io.IOException;
import java.io.PrintStream;
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
            PrintStream out) throws IOException, InterruptedException {

        if (out != null) out.printf("🔨 Building %s...%n", imageName);

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "build",
                "--file", dockerfilePath.toAbsolutePath().toString(),
                "--tag", imageName
        ));
        for (String arg : buildArgs) {
            cmd.add("--build-arg");
            cmd.add(arg);
        }
        cmd.add(contextDir.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .inheritIO();

        int exit = pb.start().waitFor();
        if (exit != 0) throw new IOException("docker build failed with exit code " + exit);

        if (out != null) out.println("✅ Build complete");
    }
}
