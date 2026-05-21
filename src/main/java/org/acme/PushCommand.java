package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.MonoConfig;
import org.acme.config.MonoConfigLoader;
import org.acme.config.ServiceConfig;
import org.acme.registry.RegistryRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "push", description = "Login to container registry and push image(s)", mixinStandardHelpOptions = true)
public class PushCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Option(names = {"--service"}, description = "Service name to push (default: all services)")
    String serviceName;

    @Override
    public void run() {
        try {
            String cwd = System.getProperty("user.dir");
            MonoConfig mono = MonoConfigLoader.load(cwd);

            List<String> errors = mono.validate();
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

            List<ServiceConfig> services = resolveServices(mono);
            String lastRegistry = null;

            for (ServiceConfig svc : services) {
                if (parent.progress() != null && services.size() > 1) {
                    parent.progress().printf("%n▶ Pushing service: %s%n", svc.id);
                }
                String registryHost = ConfigLoader.registryHost(svc.imageName);
                // Login once per unique registry
                if (!registryHost.equals(lastRegistry)) {
                    RegistryRunner.login(registryHost, username, password, parent.progress());
                    lastRegistry = registryHost;
                }
                RegistryRunner.push(svc.imageName, parent.progress());
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("Error: " + (msg != null ? msg : e.getClass().getSimpleName() + " (no message)"));
            if (System.getenv("DEPLOYER_DEBUG") != null) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private List<ServiceConfig> resolveServices(MonoConfig mono) {
        if (serviceName != null) {
            ServiceConfig svc = mono.services.get(serviceName);
            if (svc == null) throw new IllegalArgumentException("Service '" + serviceName + "' not found in deploy.yml");
            return List.of(svc);
        }
        return List.copyOf(mono.services.values());
    }
}
