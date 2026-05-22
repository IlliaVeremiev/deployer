package org.acme;

import org.acme.config.MonoConfig;
import org.acme.config.MonoConfigLoader;
import org.acme.config.ServiceConfig;
import org.acme.docker.DockerRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

@Command(name = "build", description = "Build Docker image(s) from deploy.yml", mixinStandardHelpOptions = true)
public class BuildCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Option(names = {"--service"}, description = "Service name to build (default: all services)")
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

            List<ServiceConfig> services = resolveServices(mono);
            for (ServiceConfig svc : services) {
                if (parent.progress() != null) {
                    if (services.size() > 1) parent.progress().printf("%n▶ Building service: %s%n", svc.id);
                    parent.logServiceFiles(mono, svc, true, false);
                }
                DockerRunner.build(
                        svc.resolveContextRoot(mono.deployYmlDir),
                        svc.resolveDockerfile(mono.deployYmlDir),
                        svc.imageName,
                        svc.buildArgs,
                        debug,
                        parent.progress()
                );
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
