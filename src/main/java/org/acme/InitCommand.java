package org.acme;

import org.acme.config.MonoConfig;
import org.acme.config.MonoConfigLoader;
import org.acme.config.ServiceConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

@Command(name = "init", description = "Scaffold deploy/ folders for services in deploy.yml", mixinStandardHelpOptions = true)
public class InitCommand implements Runnable {

    private static final List<String> TEMPLATES = List.of("node", "laravel", "java", "nuxt");

    @ParentCommand
    DeployerCommand parent;

    @Override
    public void run() {
        try {
            String cwd = System.getProperty("user.dir");
            MonoConfig mono = MonoConfigLoader.load(cwd);

            if (mono.services.isEmpty()) {
                System.err.println("Error: no services defined in deploy.yml");
                System.exit(1);
                return;
            }

            PrintStream out = parent.progress() != null ? parent.progress() : System.out;
            out.printf("📦 Initializing deploy folders for stack: %s (%d service(s))%n%n",
                    mono.stack, mono.services.size());

            Scanner scanner = new Scanner(System.in);
            int initialized = 0;

            for (ServiceConfig svc : mono.services.values()) {
                Path deployDir = svc.resolveDockerfile(mono.deployYmlDir).getParent(); // {project-root}/deploy/

                if (Files.isDirectory(deployDir)) {
                    out.printf("⏭  %s: deploy/ already exists, skipping%n", svc.id);
                    continue;
                }

                out.printf("🔧 Service: %s (project-root: %s)%n", svc.id, svc.projectRoot);
                String template = promptTemplate(scanner, svc.id, out);
                if (template == null) {
                    out.printf("   ⏭  Skipped%n");
                    continue;
                }

                scaffoldService(svc, mono.deployYmlDir, template, out);
                initialized++;
            }

            out.println();
            if (initialized > 0) {
                out.printf("✅ Initialized %d service(s)!%n%n", initialized);
                out.println("Next steps:");
                out.println("   1. Review generated deploy/Dockerfile and docker-compose.deploy.yml in each service");
                out.println("   2. (Optional) Add deploy/.env.production to each service");
                out.println("   3. Run: deployer validate");
                out.println("   4. Run: deployer ship");
            } else {
                out.println("ℹ️  Nothing to initialize — all services already have a deploy/ folder.");
            }

        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("Error: " + (msg != null ? msg : e.getClass().getSimpleName() + " (no message)"));
            if (System.getenv("DEPLOYER_DEBUG") != null) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private String promptTemplate(Scanner scanner, String serviceId, PrintStream out) {
        while (true) {
            out.printf("   Select template for '%s' [node/laravel/java/nuxt/skip]: ", serviceId);
            out.flush();
            String input = scanner.nextLine().trim().toLowerCase();
            if ("skip".equals(input) || input.isEmpty()) return null;
            if (TEMPLATES.contains(input)) return input;
            out.println("   Unknown template. Choose: node, laravel, java, nuxt, skip");
        }
    }

    private void scaffoldService(ServiceConfig svc, Path base, String template, PrintStream out) throws IOException {
        Path deployDir = svc.resolveDockerfile(base).getParent();
        Files.createDirectories(deployDir);

        // Dockerfile
        writeTemplate("templates/" + template + "/deploy/Dockerfile",
                deployDir.resolve("Dockerfile"), null, null, out);

        // docker-compose.deploy.yml — replace {{APP_NAME}} with route (or service id as fallback)
        String appName = (svc.route != null && !svc.route.isEmpty()) ? svc.route : svc.id;
        writeTemplate("templates/" + template + "/deploy/docker-compose.deploy.yml",
                deployDir.resolve("docker-compose.deploy.yml"), "{{APP_NAME}}", appName, out);

        out.printf("   ✅ Created deploy/ in %s%n", svc.projectRoot);
    }

    private void writeTemplate(String resourcePath, Path target, String placeholder, String replacement,
                               PrintStream out) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Template resource not found: " + resourcePath);
        }
        String content = new String(stream.readAllBytes());
        if (placeholder != null) {
            content = content.replace(placeholder, replacement);
        }
        Files.writeString(target, content);
        try {
            if (target.getFileName().toString().startsWith(".env")) {
                Files.setPosixFilePermissions(target, Set.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE
                ));
            } else {
                Files.setPosixFilePermissions(target, Set.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ
                ));
            }
        } catch (UnsupportedOperationException ignored) {}
    }
}
