package org.acme;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.enterprise.context.Dependent;
import org.acme.config.ConfigLoader;
import org.acme.config.MonoConfig;
import org.acme.config.ServiceConfig;
import org.acme.portainer.EnvVar;
import org.acme.portainer.PortainerClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TopCommand
@Dependent
@Command(
    name = "deployer",
    description = "Deploy Docker projects to Portainer",
    mixinStandardHelpOptions = true,
    subcommands = {
        BuildCommand.class,
        PushCommand.class,
        DeployCommand.class,
        ShipCommand.class,
        ValidateCommand.class,
        InitCommand.class,
        InstallSkillsCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class DeployerCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = "--config", description = "Path to config file (default: ~/.deployer.yaml)")
    String configFile;

    @Option(names = "--portainer-url",
            description = "Portainer base URL",
            defaultValue = "${DEPLOYER_PORTAINER_URL:-}")
    String portainerUrl;

    @Option(names = "--portainer-endpoint",
            description = "Portainer endpoint ID",
            defaultValue = "${DEPLOYER_PORTAINER_ENDPOINT:-}")
    String portainerEndpoint;

    @Option(names = "--domain-root",
            description = "Root domain for app routing (e.g. example.dev)",
            defaultValue = "${DEPLOYER_DOMAIN_ROOT:-}")
    String domainRoot;

    @Option(names = "--registry-username",
            description = "Container registry username",
            defaultValue = "${DEPLOYER_REGISTRY_USERNAME:-}")
    String registryUsername;

    @Option(names = {"-q", "--quiet"},
            description = "Suppress progress output")
    boolean quiet;

    /** Resolved global config (lazy-loaded once by subcommands) */
    private Map<String, String> globalConfig;

    public Map<String, String> globalConfig() {
        if (globalConfig == null) {
            globalConfig = ConfigLoader.loadGlobalConfig(configFile);
        }
        return globalConfig;
    }

    public String resolve(String flagValue, String configKey) {
        return ConfigLoader.resolve(flagValue, configKey, globalConfig());
    }

    public String portainerToken() {
        String env = System.getenv("DEPLOYER_PORTAINER_TOKEN");
        if (env != null && !env.isEmpty()) return env;
        return globalConfig().getOrDefault("portainer-token", "");
    }

    public String registryPassword() {
        String env = System.getenv("DEPLOYER_REGISTRY_PASSWORD");
        if (env != null && !env.isEmpty()) return env;
        return globalConfig().getOrDefault("registry-password", "");
    }

    public String resolvedPortainerUrl()      { return resolve(portainerUrl, "portainer-url"); }
    public String resolvedPortainerEndpoint() { return resolve(portainerEndpoint, "portainer-endpoint"); }
    public String resolvedDomainRoot()        { return resolve(domainRoot, "domain-root"); }
    public String resolvedRegistryUsername()  { return resolve(registryUsername, "registry-username"); }

    public java.io.PrintStream progress() {
        return quiet ? null : System.out;
    }

    public PortainerClient newPortainerClient() throws IllegalArgumentException {
        return newPortainerClient(false);
    }

    public PortainerClient newPortainerClient(boolean verbose) throws IllegalArgumentException {
        String url = resolvedPortainerUrl();
        String token = portainerToken();
        String endpointStr = resolvedPortainerEndpoint();

        if (url.isEmpty()) throw new IllegalArgumentException("--portainer-url is required (or set DEPLOYER_PORTAINER_URL)");
        if (token.isEmpty()) throw new IllegalArgumentException("portainer-token is required (set DEPLOYER_PORTAINER_TOKEN env var or portainer-token in config file)");
        if (endpointStr.isEmpty()) throw new IllegalArgumentException("--portainer-endpoint is required (or set DEPLOYER_PORTAINER_ENDPOINT)");

        int endpointId;
        try {
            endpointId = Integer.parseInt(endpointStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--portainer-endpoint must be a positive integer, got: " + endpointStr);
        }

        PrintWriter warnings = new PrintWriter(System.err, true);
        return new PortainerClient(url, token, endpointId, warnings, verbose);
    }

    /** Build Portainer env vars from .env.production + managed deployer vars. */
    public List<EnvVar> buildPortainerEnv(
            ServiceConfig svc,
            Map<String, String> envVars,
            String domainRootValue) {

        List<String> managed = List.of("IMAGE_NAME", "APP_NAME", "DOMAIN_ROOT");
        List<EnvVar> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            if (managed.contains(entry.getKey())) {
                System.err.printf("Warning: .env.production defines %s which is managed by deployer — it will be overridden%n", entry.getKey());
            } else {
                result.add(new EnvVar(entry.getKey(), entry.getValue()));
            }
        }

        result.add(new EnvVar("IMAGE_NAME", svc.imageName));
        result.add(new EnvVar("APP_NAME", svc.route));
        result.add(new EnvVar("DOMAIN_ROOT", domainRootValue));

        result.sort((a, b) -> a.name().compareTo(b.name()));
        return result;
    }

    /**
     * Print resolved file paths and build args for a service.
     * showBuild=true: Dockerfile, context, build args
     * showDeploy=true: compose file, env file
     */
    public void logServiceFiles(MonoConfig mono, ServiceConfig svc, boolean showBuild, boolean showDeploy) {
        java.io.PrintStream out = progress();
        if (out == null) return;
        java.nio.file.Path base = mono.deployYmlDir;
        if (showBuild) {
            out.printf("   📄 Dockerfile  : %s%n", base.relativize(svc.resolveDockerfile(base)));
            out.printf("   📁 Context     : %s%n", base.relativize(svc.resolveContextRoot(base)));
            out.printf("   🔧 Build args  : %s%n",
                    svc.buildArgs.isEmpty() ? "(none)" : String.join(", ", svc.buildArgs));
            java.nio.file.Path buildEnvPath = svc.resolveEnvFile(base);
            boolean buildEnvExists = Files.exists(buildEnvPath);
            out.printf("   🔐 Build env   : %s%s%n",
                    base.relativize(buildEnvPath), buildEnvExists ? "" : " (not found — optional)");
        }
        if (showDeploy) {
            out.printf("   📋 Compose     : %s%n", base.relativize(svc.resolveComposeFile(base)));
            java.nio.file.Path envPath = svc.resolveEnvFile(base);
            boolean envExists = Files.exists(envPath);
            out.printf("   🔐 Env file    : %s%s%n",
                    base.relativize(envPath), envExists ? "" : " (not found — optional)");
        }
    }

    /** Shared deploy logic for a single service. Used by DeployCommand and ShipCommand. */
    public void runDeploy(MonoConfig mono, ServiceConfig svc) throws Exception {
        runDeploy(mono, svc, false);
    }

    public void runDeploy(MonoConfig mono, ServiceConfig svc, boolean verbose) throws Exception {
        String domainRootValue = resolvedDomainRoot();
        if (domainRootValue.isEmpty()) throw new IllegalArgumentException("--domain-root is required (or set DEPLOYER_DOMAIN_ROOT)");

        String composeContent = Files.readString(svc.resolveComposeFile(mono.deployYmlDir));
        Map<String, String> envVars = ConfigLoader.loadEnvFile(svc.resolveEnvFile(mono.deployYmlDir));

        if (verbose && progress() != null) {
            if (envVars.isEmpty()) {
                progress().println("   🔐 Env vars    : (none)");
            } else {
                progress().println("   🔐 Env vars    :");
                envVars.forEach((k, v) -> progress().printf("      %s=%s%n", k, v));
            }
        }

        List<EnvVar> portainerEnv = buildPortainerEnv(svc, envVars, domainRootValue);

        PortainerClient client = newPortainerClient(verbose);
        client.deploy(svc.stackName(mono.stack), composeContent, portainerEnv);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
