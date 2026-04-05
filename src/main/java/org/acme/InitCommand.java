package org.acme;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Command(name = "init", description = "Scaffold deploy/ folder from a project template", mixinStandardHelpOptions = true)
public class InitCommand implements Runnable {

    private static final Pattern APP_NAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    /**
     * Maps resource path -> relative target path.
     * Files named "dot-deploy" are written as ".deploy" (dotfiles can't start with . in some resource contexts).
     */
    private static final Map<String, List<TemplateFile>> TEMPLATES = Map.of(
        "node", List.of(
            new TemplateFile("templates/node/Dockerfile", "Dockerfile"),
            new TemplateFile("templates/node/deploy/dot-deploy", "deploy/.deploy"),
            new TemplateFile("templates/node/deploy/docker-compose.deploy.yml", "deploy/docker-compose.deploy.yml")
        ),
        "laravel", List.of(
            new TemplateFile("templates/laravel/Dockerfile", "Dockerfile"),
            new TemplateFile("templates/laravel/deploy/dot-deploy", "deploy/.deploy"),
            new TemplateFile("templates/laravel/deploy/docker-compose.deploy.yml", "deploy/docker-compose.deploy.yml")
        ),
        "java", List.of(
            new TemplateFile("templates/java/Dockerfile", "Dockerfile"),
            new TemplateFile("templates/java/deploy/dot-deploy", "deploy/.deploy"),
            new TemplateFile("templates/java/deploy/docker-compose.deploy.yml", "deploy/docker-compose.deploy.yml")
        )
    );

    record TemplateFile(String resourcePath, String targetRelativePath) {}

    @ParentCommand
    DeployerCommand parent;

    @Option(names = {"-t", "--template"}, description = "Project template: node, laravel, java, list", defaultValue = "node")
    String template;

    @Option(names = {"-n", "--name"}, description = "App name/subdomain slug (default: directory name)")
    String name;

    @Override
    public void run() {
        if ("list".equals(template)) {
            System.out.println("Available templates:");
            System.out.println("  node    — Node.js (port 3000)");
            System.out.println("  laravel — Laravel/PHP (port 80)");
            System.out.println("  java    — Java/Gradle (port 8080)");
            return;
        }

        if (!TEMPLATES.containsKey(template)) {
            System.err.println("Error: unknown template '" + template + "'. Use --template list to see available templates.");
            System.exit(1);
            return;
        }

        String cwd = System.getProperty("user.dir");

        // Determine app name
        String appName = (name != null && !name.isEmpty()) ? name : Paths.get(cwd).getFileName().toString().toLowerCase();

        if (!APP_NAME_PATTERN.matcher(appName).matches()) {
            System.err.println("Error: app name '" + appName + "' is invalid. Must be lowercase letters, digits, and hyphens only (no leading/trailing hyphens).");
            System.exit(1);
            return;
        }

        // Check deploy/ doesn't already exist
        if (Files.isDirectory(Paths.get(cwd, "deploy"))) {
            System.err.println("Error: deploy/ directory already exists. Remove it first or use a different project directory.");
            System.exit(1);
            return;
        }

        if (parent.progress() != null) {
            parent.progress().printf("📁 Initializing deploy config (template: %s, name: %s)%n%n", template, appName);
        }

        for (TemplateFile tf : TEMPLATES.get(template)) {
            Path targetPath = Paths.get(cwd, tf.targetRelativePath());

            try {
                Files.createDirectories(targetPath.getParent());

                InputStream stream = getClass().getClassLoader().getResourceAsStream(tf.resourcePath());
                if (stream == null) {
                    throw new IOException("Template resource not found: " + tf.resourcePath());
                }
                String content = new String(stream.readAllBytes());

                // Replace placeholders
                content = content.replace("{{APP_NAME}}", appName)
                                 .replace("{{STACK_NAME}}", appName)
                                 .replace("{{IMAGE_NAME}}", appName + ":latest");

                Files.writeString(targetPath, content);

                // .env* files get 0600, others get 0644
                try {
                    if (targetPath.getFileName().toString().startsWith(".env")) {
                        Files.setPosixFilePermissions(targetPath, Set.of(
                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE
                        ));
                    } else {
                        Files.setPosixFilePermissions(targetPath, Set.of(
                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.OTHERS_READ
                        ));
                    }
                } catch (UnsupportedOperationException ignored) {}

                if (parent.progress() != null) {
                    parent.progress().println("   📄 " + tf.targetRelativePath());
                }
            } catch (IOException e) {
                System.err.println("Error writing " + tf.targetRelativePath() + ": " + e.getMessage());
                System.exit(1);
                return;
            }
        }

        if (parent.progress() != null) {
            parent.progress().println();
            parent.progress().println("✅ Project initialized!");
            parent.progress().println();
            parent.progress().println("Next steps:");
            parent.progress().println("   1. Edit deploy/.deploy — set IMAGE_NAME to your registry path");
            parent.progress().println("   2. Edit deploy/docker-compose.deploy.yml — adjust Traefik labels");
            parent.progress().println("   3. (Optional) Add deploy/.env.production");
            parent.progress().println("   4. Run: deployer validate");
            parent.progress().println("   5. Run: deployer ship");
        }
    }
}
