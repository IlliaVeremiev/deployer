package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.ProjectConfig;
import org.acme.docker.DockerRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "build", description = "Build Docker image from project Dockerfile", mixinStandardHelpOptions = true)
public class BuildCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Override
    public void run() {
        try {
            String cwd = System.getProperty("user.dir");
            ProjectConfig cfg = ConfigLoader.loadProjectConfig(cwd);
            List<String> errors = cfg.validate();
            if (!errors.isEmpty()) {
                System.err.println("Configuration errors:");
                errors.forEach(e -> System.err.println("  • " + e));
                System.exit(1);
                return;
            }
            DockerRunner.build(cwd, cfg.imageName, cfg.buildArgs, parent.progress());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
