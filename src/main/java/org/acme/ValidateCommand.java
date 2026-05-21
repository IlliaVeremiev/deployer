package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.MonoConfig;
import org.acme.config.MonoConfigLoader;
import org.acme.config.ServiceConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Command(name = "validate", description = "Validate deploy.yml and service deploy configuration", mixinStandardHelpOptions = true)
public class ValidateCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Override
    public void run() {
        System.out.println("🔍 Validating deploy configuration...");
        System.out.println();

        String cwd = System.getProperty("user.dir");
        List<String> problems = new ArrayList<>();

        // 1. deploy.yml
        MonoConfig mono = null;
        Path deployYml = Paths.get(cwd, "deploy.yml");
        if (!Files.exists(deployYml)) {
            problems.add("deploy.yml not found in project root");
            System.out.println("   ❌ deploy.yml not found");
        } else {
            System.out.println("   ✅ deploy.yml found");
            try {
                mono = MonoConfigLoader.load(cwd);
                System.out.println("   ✅ deploy.yml is parseable");
            } catch (IOException e) {
                problems.add("deploy.yml: " + e.getMessage());
                System.out.println("   ❌ deploy.yml: " + e.getMessage());
            }
        }

        if (mono == null) {
            printResult(problems);
            return;
        }

        // 2. stack
        if (mono.stack != null && !mono.stack.isEmpty()) {
            System.out.println("   ✅ stack: " + mono.stack);
        } else {
            problems.add("deploy.yml: 'stack' is required");
            System.out.println("   ❌ deploy.yml: 'stack' is required");
        }

        // 3. services
        if (mono.services.isEmpty()) {
            problems.add("deploy.yml: no services defined");
            System.out.println("   ❌ deploy.yml: no services defined");
            printResult(problems);
            return;
        }
        System.out.printf("   ✅ %d service(s) defined%n", mono.services.size());

        // 4. Per-service checks
        for (ServiceConfig svc : mono.services.values()) {
            System.out.println();
            System.out.printf("   📦 Service: %s (stack: %s)%n", svc.id, svc.stackName(mono.stack));

            check(svc.imageName, "image-name", svc.id, problems);
            check(svc.route, "route", svc.id, problems);
            check(svc.projectRoot, "project-root", svc.id, problems);

            if (svc.projectRoot != null) {
                Path projectRootPath = mono.deployYmlDir.resolve(svc.projectRoot);
                if (Files.isDirectory(projectRootPath)) {
                    System.out.println("      ✅ project-root exists: " + svc.projectRoot);
                } else {
                    problems.add("service '" + svc.id + "': project-root not found: " + svc.projectRoot);
                    System.out.println("      ❌ project-root not found: " + svc.projectRoot);
                }
            }

            String ctxRoot = svc.contextRoot != null ? svc.contextRoot : svc.projectRoot;
            if (ctxRoot != null) {
                Path ctxPath = mono.deployYmlDir.resolve(ctxRoot);
                if (Files.isDirectory(ctxPath)) {
                    System.out.println("      ✅ context-root exists: " + ctxRoot);
                } else {
                    problems.add("service '" + svc.id + "': context-root not found: " + ctxRoot);
                    System.out.println("      ❌ context-root not found: " + ctxRoot);
                }
            }

            // Dockerfile
            if (svc.projectRoot != null) {
                Path dockerfile = svc.resolveDockerfile(mono.deployYmlDir);
                if (Files.exists(dockerfile)) {
                    System.out.println("      ✅ Dockerfile: " + mono.deployYmlDir.relativize(dockerfile));
                } else {
                    problems.add("service '" + svc.id + "': Dockerfile not found at " + mono.deployYmlDir.relativize(dockerfile));
                    System.out.println("      ❌ Dockerfile not found: " + mono.deployYmlDir.relativize(dockerfile));
                }

                // Compose file
                Path composePath = svc.resolveComposeFile(mono.deployYmlDir);
                if (Files.exists(composePath)) {
                    System.out.println("      ✅ compose: " + mono.deployYmlDir.relativize(composePath));
                    try {
                        String compose = Files.readString(composePath);
                        if (compose.contains("IMAGE_NAME")) {
                            System.out.println("      ✅ compose references IMAGE_NAME");
                        } else {
                            problems.add("service '" + svc.id + "': compose does not reference ${IMAGE_NAME}");
                            System.out.println("      ❌ compose does not reference ${IMAGE_NAME}");
                        }
                        if (compose.contains("traefik.enable=true")) {
                            System.out.println("      ✅ compose has traefik.enable=true");
                        } else {
                            problems.add("service '" + svc.id + "': compose missing Traefik labels");
                            System.out.println("      ❌ compose missing traefik.enable=true");
                        }
                    } catch (IOException e) {
                        problems.add("service '" + svc.id + "': cannot read compose: " + e.getMessage());
                        System.out.println("      ❌ cannot read compose: " + e.getMessage());
                    }
                } else {
                    problems.add("service '" + svc.id + "': compose not found at " + mono.deployYmlDir.relativize(composePath));
                    System.out.println("      ❌ compose not found: " + mono.deployYmlDir.relativize(composePath));
                }

                // .env.production (optional)
                Path envPath = svc.resolveEnvFile(mono.deployYmlDir);
                if (Files.exists(envPath)) {
                    System.out.println("      ✅ .env.production found");
                } else {
                    System.out.println("      ℹ️  .env.production not found (optional)");
                }

                // Registry info
                if (svc.imageName != null && !svc.imageName.isEmpty()) {
                    System.out.println("      ℹ️  registry: " + ConfigLoader.registryHost(svc.imageName));
                }
            }
        }

        System.out.println();
        printResult(problems);
    }

    private void check(String value, String field, String serviceId, List<String> problems) {
        if (value != null && !value.isEmpty()) {
            System.out.printf("      ✅ %s: %s%n", field, value);
        } else {
            problems.add("service '" + serviceId + "': " + field + " is required");
            System.out.printf("      ❌ %s is required%n", field);
        }
    }

    private void printResult(List<String> problems) {
        if (problems.isEmpty()) {
            System.out.println("✅ All checks passed!");
        } else {
            System.out.println("❌ Found " + problems.size() + " problem(s):");
            problems.forEach(p -> System.out.println("   • " + p));
            System.exit(1);
        }
    }
}
