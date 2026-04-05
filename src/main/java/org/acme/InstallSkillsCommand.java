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
import java.util.Set;

@Command(name = "install-skills", description = "Install Claude Code skills to ~/.claude/skills/", mixinStandardHelpOptions = true)
public class InstallSkillsCommand implements Runnable {

    private static final List<String> SKILL_FILES = List.of(
        "skills/deployer/SKILL.md",
        "skills/deployer-setup/SKILL.md"
    );

    @ParentCommand
    DeployerCommand parent;

    @Option(names = "--force", description = "Overwrite existing skill files")
    boolean force;

    @Override
    public void run() {
        String home = System.getProperty("user.home");
        if (home == null) {
            System.err.println("Error: cannot determine home directory");
            System.exit(1);
            return;
        }

        Path skillsDir = Paths.get(home, ".claude", "skills");
        if (parent.progress() != null) {
            parent.progress().println("📦 Installing skills to " + skillsDir + "/");
            parent.progress().println();
        }

        int skipped = 0;
        int installed = 0;

        for (String resourcePath : SKILL_FILES) {
            // resourcePath: "skills/deployer/SKILL.md" -> relative: "deployer/SKILL.md"
            String relativePath = resourcePath.substring("skills/".length());
            Path targetPath = skillsDir.resolve(relativePath);

            if (Files.exists(targetPath) && !force) {
                skipped++;
                continue;
            }

            try {
                Files.createDirectories(targetPath.getParent());
                InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (stream == null) {
                    throw new IOException("Skill resource not found: " + resourcePath);
                }
                Files.write(targetPath, stream.readAllBytes());
                try {
                    Files.setPosixFilePermissions(targetPath, Set.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ
                    ));
                } catch (UnsupportedOperationException ignored) {}

                installed++;
                if (parent.progress() != null) {
                    parent.progress().println("   📄 " + relativePath);
                }
            } catch (IOException e) {
                System.err.println("Error installing " + relativePath + ": " + e.getMessage());
                System.exit(1);
                return;
            }
        }

        if (parent.progress() != null) {
            parent.progress().println();
            parent.progress().println("✅ Skills installed!");
            if (skipped > 0) {
                parent.progress().println();
                parent.progress().printf("   ℹ️  %d file(s) skipped (already exist). Run with --force to overwrite.%n", skipped);
            }
            parent.progress().println();
            parent.progress().println("Installed skills:");
            parent.progress().println("   • deployer       — CLI commands and deployment workflow");
            parent.progress().println("   • deployer-setup — project scaffolding and config validation");
        }
    }
}
