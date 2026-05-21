package org.acme.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServiceConfig {
    public String id;
    public String projectRoot;
    public String contextRoot;
    public String imageName;
    public String route;
    public List<String> buildArgs = new ArrayList<>();
    public String envFile;
    public String composeFile;

    public String stackName(String stack) {
        return stack + "-" + id;
    }

    public Path resolveDockerfile(Path base) {
        return base.resolve(projectRoot).resolve("deploy/Dockerfile");
    }

    public Path resolveEnvFile(Path base) {
        return envFile != null ? base.resolve(envFile)
                               : base.resolve(projectRoot).resolve("deploy/.env.production");
    }

    public Path resolveComposeFile(Path base) {
        return composeFile != null ? base.resolve(composeFile)
                                   : base.resolve(projectRoot).resolve("deploy/docker-compose.deploy.yml");
    }

    public Path resolveContextRoot(Path base) {
        return base.resolve(contextRoot != null ? contextRoot : projectRoot);
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (imageName == null || imageName.isEmpty()) errors.add("service '" + id + "': image-name is required");
        if (route == null || route.isEmpty()) errors.add("service '" + id + "': route is required");
        if (projectRoot == null || projectRoot.isEmpty()) errors.add("service '" + id + "': project-root is required");
        return errors;
    }
}
