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
            String msg = e.getMessage();
            System.err.println("Error: " + (msg != null ? msg : e.getClass().getSimpleName() + " (no message)"));
            if (System.getenv("DEPLOYER_DEBUG") != null) e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
