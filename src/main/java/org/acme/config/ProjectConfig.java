package org.acme.config;

import java.util.ArrayList;
import java.util.List;

public class ProjectConfig {
    public String stackName;
    public String imageName;
    public String appName;
    public List<String> buildArgs = new ArrayList<>();

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (stackName == null || stackName.isEmpty()) errors.add("STACK_NAME is required");
        if (imageName == null || imageName.isEmpty()) errors.add("IMAGE_NAME is required");
        if (appName == null || appName.isEmpty()) errors.add("APP_NAME is required");
        return errors;
    }
}
