package org.acme.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class MonoConfig {
    public String stack;
    public Path deployYmlDir;
    public LinkedHashMap<String, ServiceConfig> services = new LinkedHashMap<>();

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (stack == null || stack.isEmpty()) errors.add("stack is required");
        if (services.isEmpty()) errors.add("at least one service must be defined under services:");
        for (ServiceConfig svc : services.values()) {
            errors.addAll(svc.validate());
        }
        return errors;
    }
}
