package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.ProjectConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "deploy", description = "Create or update Portainer stack", mixinStandardHelpOptions = true)
public class DeployCommand implements Runnable {

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

            String domainRoot = parent.resolvedDomainRoot();
            String liveUrl = "https://" + cfg.appName + "." + domainRoot;

            if (parent.progress() != null) {
                parent.progress().printf("🚀 Deploying %s → %s%n", cfg.stackName, liveUrl);
            }

            parent.runDeploy(cwd, cfg);

            if (parent.progress() != null) {
                parent.progress().printf("✅ Deployed! Live at: %s%n", liveUrl);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
