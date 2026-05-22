package org.acme;

import org.acme.config.MonoConfig;
import org.acme.config.MonoConfigLoader;
import org.acme.config.ServiceConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "deploy", description = "Create or update Portainer stack(s)", mixinStandardHelpOptions = true)
public class DeployCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Option(names = {"-v", "--verbose"},
            description = "Print HTTP request URLs, response status, and response bodies")
    boolean verbose;

    @Option(names = {"--service"}, description = "Service name to deploy (default: all services)")
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

            String domainRoot = parent.resolvedDomainRoot();
            List<ServiceConfig> services = resolveServices(mono);

            for (ServiceConfig svc : services) {
                String liveUrl = "https://" + svc.route + "." + domainRoot;
                if (parent.progress() != null) {
                    if (services.size() > 1) parent.progress().printf("%n▶ Deploying service: %s%n", svc.id);
                    parent.progress().printf("🚀 Deploying %s → %s%n", svc.stackName(mono.stack), liveUrl);
                    parent.logServiceFiles(mono, svc, false, true);
                }
                parent.runDeploy(mono, svc, verbose);
                if (parent.progress() != null) {
                    parent.progress().printf("✅ Deployed! Live at: %s%n", liveUrl);
                }
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
