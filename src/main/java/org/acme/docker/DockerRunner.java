package org.acme.docker;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class DockerRunner {

    /** Build a Docker image. Progress messages go to `out`; docker output goes to stdout/stderr. */
    public static void build(String projectDir, String imageName, List<String> buildArgs, PrintStream out) throws IOException, InterruptedException {
        if (out != null) out.printf("🔨 Building %s...%n", imageName);

        List<String> cmd = new ArrayList<>(List.of("docker", "build", "-t", imageName));
        for (String arg : buildArgs) {
            cmd.add("--build-arg");
            cmd.add(arg);
        }
        cmd.add(".");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(new java.io.File(projectDir))
                .inheritIO();

        int exit = pb.start().waitFor();
        if (exit != 0) throw new IOException("docker build failed with exit code " + exit);

        if (out != null) out.println("✅ Build complete");
    }
}
