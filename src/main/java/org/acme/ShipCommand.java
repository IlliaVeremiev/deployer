package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.ProjectConfig;
import org.acme.docker.DockerRunner;
import org.acme.registry.RegistryRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "ship", description = "Full pipeline: build → push → deploy", mixinStandardHelpOptions = true)
public class ShipCommand implements Runnable {

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

            String username = parent.resolvedRegistryUsername();
            String password = parent.registryPassword();
            String domainRoot = parent.resolvedDomainRoot();
            String liveUrl = "https://" + cfg.appName + "." + domainRoot;

            if (parent.progress() != null) {
                parent.progress().printf("🚢 Shipping %s%n", cfg.stackName);
                parent.progress().printf("   Image : %s%n", cfg.imageName);
                parent.progress().printf("   URL   : %s%n", liveUrl);
                parent.progress().println();
            }

            // 1. Build
            DockerRunner.build(cwd, cfg.imageName, cfg.buildArgs, parent.progress());

            // 2. Push
            if (username.isEmpty()) throw new IllegalArgumentException("--registry-username is required (or set DEPLOYER_REGISTRY_USERNAME)");
            if (password.isEmpty()) throw new IllegalArgumentException("registry-password is required (set DEPLOYER_REGISTRY_PASSWORD env var)");
            String registryHost = ConfigLoader.registryHost(cfg.imageName);
            RegistryRunner.login(registryHost, username, password, parent.progress());
            RegistryRunner.push(cfg.imageName, parent.progress());

            // 3. Deploy
            parent.runDeploy(cwd, cfg, verbose);

            if (parent.progress() != null) {
                parent.progress().printf("%n✅ Shipped! Live at: %s%n", liveUrl);
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("Error: " + (msg != null ? msg : e.getClass().getSimpleName() + " (no message)"));
            if (System.getenv("DEPLOYER_DEBUG") != null) e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
