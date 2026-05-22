package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.MonoConfig;
import org.acme.config.MonoConfigLoader;
import org.acme.config.ServiceConfig;
import org.acme.docker.DockerRunner;
import org.acme.registry.RegistryRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "ship", description = "Full pipeline: build → push → deploy (all services)", mixinStandardHelpOptions = true)
public class ShipCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Option(names = {"-v", "--verbose"},
            description = "Print HTTP request URLs, response status, and response bodies")
    boolean verbose;

    @Option(names = {"--service"}, description = "Service name to ship (default: all services)")
    String serviceName;

    @Option(names = {"--debug"}, description = "Run docker build with --debug flag")
    boolean debug;

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
            String domainRoot = parent.resolvedDomainRoot();

            if (username.isEmpty()) throw new IllegalArgumentException("--registry-username is required (or set DEPLOYER_REGISTRY_USERNAME)");
            if (password.isEmpty()) throw new IllegalArgumentException("registry-password is required (set DEPLOYER_REGISTRY_PASSWORD env var)");

            List<ServiceConfig> services = resolveServices(mono);
            int total = services.size();

            if (parent.progress() != null) {
                if (total > 1) {
                    parent.progress().printf("🚢 Shipping %s (%d services)%n%n", mono.stack, total);
                } else {
                    ServiceConfig svc = services.get(0);
                    String liveUrl = "https://" + svc.route + "." + domainRoot;
                    parent.progress().printf("🚢 Shipping %s%n", svc.stackName(mono.stack));
                    parent.progress().printf("   Image : %s%n", svc.imageName);
                    parent.progress().printf("   URL   : %s%n", liveUrl);
                    parent.progress().println();
                }
            }

            String lastRegistry = null;

            for (int i = 0; i < total; i++) {
                ServiceConfig svc = services.get(i);
                String liveUrl = "https://" + svc.route + "." + domainRoot;

                if (parent.progress() != null) {
                    if (total > 1) parent.progress().printf("[%d/%d] %s: %s%n", i + 1, total, svc.id, svc.stackName(mono.stack));
                    parent.logServiceFiles(mono, svc, true, true);
                }

                // 1. Build
                DockerRunner.build(
                        svc.resolveContextRoot(mono.deployYmlDir),
                        svc.resolveDockerfile(mono.deployYmlDir),
                        svc.imageName,
                        svc.buildArgs,
                        debug,
                        parent.progress()
                );

                // 2. Push (login once per unique registry)
                String registryHost = ConfigLoader.registryHost(svc.imageName);
                if (!registryHost.equals(lastRegistry)) {
                    RegistryRunner.login(registryHost, username, password, parent.progress());
                    lastRegistry = registryHost;
                }
                RegistryRunner.push(svc.imageName, parent.progress());

                // 3. Deploy
                parent.runDeploy(mono, svc, verbose);

                if (parent.progress() != null) {
                    parent.progress().printf("✅ %s live at: %s%n", svc.id, liveUrl);
                }
            }

            if (parent.progress() != null && total > 1) {
                parent.progress().printf("%n✅ All %d services shipped!%n", total);
            } else if (parent.progress() != null) {
                parent.progress().printf("%n✅ Shipped!%n");
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
