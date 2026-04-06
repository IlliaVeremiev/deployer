package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.ProjectConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "deploy", description = "Create or update Portainer stack", mixinStandardHelpOptions = true)
public class DeployCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Option(names = {"-v", "--verbose"},
            description = "Print HTTP request URLs, response status, and response bodies")
    boolean verbose;

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

            parent.runDeploy(cwd, cfg, verbose);

            if (parent.progress() != null) {
                parent.progress().printf("✅ Deployed! Live at: %s%n", liveUrl);
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("Error: " + (msg != null ? msg : e.getClass().getSimpleName() + " (no message)"));
            if (System.getenv("DEPLOYER_DEBUG") != null) e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
