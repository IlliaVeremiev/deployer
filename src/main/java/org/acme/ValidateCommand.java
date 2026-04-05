package org.acme;

import org.acme.config.ConfigLoader;
import org.acme.config.ProjectConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Command(name = "validate", description = "Validate project deploy configuration", mixinStandardHelpOptions = true)
public class ValidateCommand implements Runnable {

    @ParentCommand
    DeployerCommand parent;

    @Override
    public void run() {
        System.out.println("🔍 Validating deploy configuration...");
        System.out.println();

        String cwd = System.getProperty("user.dir");
        List<String> problems = new ArrayList<>();

        // 1. deploy/ directory exists
        if (Files.isDirectory(Paths.get(cwd, "deploy"))) {
            System.out.println("   ✅ deploy/ directory exists");
        } else {
            problems.add("deploy/ directory not found");
            System.out.println("   ❌ deploy/ directory not found");
        }

        // 2. .deploy file
        ProjectConfig cfg = null;
        try {
            cfg = ConfigLoader.loadProjectConfig(cwd);
            System.out.println("   ✅ .deploy file is readable");
        } catch (IOException e) {
            problems.add(".deploy: " + e.getMessage());
            System.out.println("   ❌ .deploy: " + e.getMessage());
        }

        if (cfg != null) {
            // 3. Required fields
            check(cfg.stackName, "STACK_NAME", problems);
            check(cfg.imageName, "IMAGE_NAME", problems);
            check(cfg.appName, "APP_NAME", problems);

            // 4. Registry detection
            if (!cfg.imageName.isEmpty()) {
                System.out.println("   ℹ️  Registry: " + ConfigLoader.registryHost(cfg.imageName));
            }
        }

        // 5. docker-compose.deploy.yml exists
        java.nio.file.Path composePath = Paths.get(cwd, "deploy", "docker-compose.deploy.yml");
        if (Files.exists(composePath)) {
            System.out.println("   ✅ docker-compose.deploy.yml exists");
            try {
                String compose = Files.readString(composePath);
                // 6. References IMAGE_NAME
                if (compose.contains("IMAGE_NAME")) {
                    System.out.println("   ✅ compose references IMAGE_NAME");
                } else {
                    problems.add("compose: does not reference ${IMAGE_NAME}");
                    System.out.println("   ❌ compose: does not reference ${IMAGE_NAME}");
                }
                // 7. Traefik
                if (compose.contains("traefik.enable=true")) {
                    System.out.println("   ✅ compose has traefik.enable=true");
                } else {
                    problems.add("compose: missing Traefik labels (traefik.enable=true)");
                    System.out.println("   ❌ compose: missing traefik.enable=true");
                }
                // 8. proxy network
                if (compose.contains("proxy:") || compose.contains("- proxy")) {
                    System.out.println("   ✅ compose has proxy network");
                } else {
                    problems.add("compose: missing proxy network reference");
                    System.out.println("   ❌ compose: missing proxy network reference");
                }
            } catch (IOException e) {
                problems.add("compose: cannot read file: " + e.getMessage());
                System.out.println("   ❌ compose: cannot read: " + e.getMessage());
            }
        } else {
            problems.add("docker-compose.deploy.yml not found");
            System.out.println("   ❌ docker-compose.deploy.yml not found");
        }

        // 9. .env.production (optional)
        java.nio.file.Path envPath = Paths.get(cwd, "deploy", ".env.production");
        if (Files.exists(envPath)) {
            try {
                ConfigLoader.loadEnvFile(cwd);
                System.out.println("   ✅ .env.production is readable");
            } catch (IOException e) {
                problems.add(".env.production: " + e.getMessage());
                System.out.println("   ❌ .env.production: " + e.getMessage());
            }
        } else {
            System.out.println("   ℹ️  .env.production not found (optional)");
        }

        // 10. Dockerfile in project root
        if (Files.exists(Paths.get(cwd, "Dockerfile"))) {
            System.out.println("   ✅ Dockerfile exists");
        } else {
            problems.add("Dockerfile not found in project root");
            System.out.println("   ❌ Dockerfile not found in project root");
        }

        System.out.println();
        if (problems.isEmpty()) {
            System.out.println("✅ All checks passed!");
        } else {
            System.out.println("❌ Found " + problems.size() + " problem(s):");
            problems.forEach(p -> System.out.println("   • " + p));
            System.exit(1);
        }
    }

    private void check(String value, String fieldName, List<String> problems) {
        if (value != null && !value.isEmpty()) {
            System.out.println("   ✅ " + fieldName + "=" + value);
        } else {
            problems.add(".deploy: " + fieldName + " is required");
            System.out.println("   ❌ .deploy: " + fieldName + " is required");
        }
    }
}
