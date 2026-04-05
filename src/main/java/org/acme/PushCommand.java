package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.ProjectConfig;
import org.acme.registry.RegistryRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "push", description = "Login to container registry and push image", mixinStandardHelpOptions = true)
public class PushCommand implements Runnable {

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

            String username = parent.resolvedRegistryUsername();
            String password = parent.registryPassword();

            if (username.isEmpty()) {
                System.err.println("Error: --registry-username is required (or set DEPLOYER_REGISTRY_USERNAME)");
                System.exit(1);
                return;
            }
            if (password.isEmpty()) {
                System.err.println("Error: registry-password is required (set DEPLOYER_REGISTRY_PASSWORD env var or registry-password in config file)");
                System.exit(1);
                return;
            }

            String registryHost = ConfigLoader.registryHost(cfg.imageName);
            RegistryRunner.login(registryHost, username, password, parent.progress());
            RegistryRunner.push(cfg.imageName, parent.progress());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
