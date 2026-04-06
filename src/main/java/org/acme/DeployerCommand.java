package org.acme;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.config.ConfigLoader;
import org.acme.portainer.EnvVar;
import org.acme.portainer.PortainerClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TopCommand
@ApplicationScoped
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

    /** Resolve a value: CLI/env flag > config file */
    public String resolve(String flagValue, String configKey) {
        return ConfigLoader.resolve(flagValue, configKey, globalConfig());
    }

    /** Get portainer token from env var or config file (never a CLI flag) */
    public String portainerToken() {
        String env = System.getenv("DEPLOYER_PORTAINER_TOKEN");
        if (env != null && !env.isEmpty()) return env;
        return globalConfig().getOrDefault("portainer-token", "");
    }

    /** Get registry password from env var or config file (never a CLI flag) */
    public String registryPassword() {
        String env = System.getenv("DEPLOYER_REGISTRY_PASSWORD");
        if (env != null && !env.isEmpty()) return env;
        return globalConfig().getOrDefault("registry-password", "");
    }

    public String resolvedPortainerUrl() { return resolve(portainerUrl, "portainer-url"); }
    public String resolvedPortainerEndpoint() { return resolve(portainerEndpoint, "portainer-endpoint"); }
    public String resolvedDomainRoot() { return resolve(domainRoot, "domain-root"); }
    public String resolvedRegistryUsername() { return resolve(registryUsername, "registry-username"); }

    /** Get progress print stream: null if --quiet, otherwise System.out */
    public java.io.PrintStream progress() {
        return quiet ? null : System.out;
    }

    /** Build a Portainer client from current resolved config */
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

    /** Build the list of Portainer env vars from project config + .env.production */
    public List<EnvVar> buildPortainerEnv(
            org.acme.config.ProjectConfig cfg,
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

        result.add(new EnvVar("IMAGE_NAME", cfg.imageName));
        result.add(new EnvVar("APP_NAME", cfg.appName));
        result.add(new EnvVar("DOMAIN_ROOT", domainRootValue));

        result.sort((a, b) -> a.name().compareTo(b.name()));
        return result;
    }

    /** Shared deploy logic used by DeployCommand and ShipCommand */
    public void runDeploy(String cwd, org.acme.config.ProjectConfig cfg) throws Exception {
        runDeploy(cwd, cfg, false);
    }

    public void runDeploy(String cwd, org.acme.config.ProjectConfig cfg, boolean verbose) throws Exception {
        String domainRootValue = resolvedDomainRoot();
        if (domainRootValue.isEmpty()) throw new IllegalArgumentException("--domain-root is required (or set DEPLOYER_DOMAIN_ROOT)");

        String composeContent = ConfigLoader.readCompose(cwd);
        Map<String, String> envVars = ConfigLoader.loadEnvFile(cwd);
        List<EnvVar> portainerEnv = buildPortainerEnv(cfg, envVars, domainRootValue);

        PortainerClient client = newPortainerClient(verbose);
        client.deploy(cfg.stackName, composeContent, portainerEnv);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
