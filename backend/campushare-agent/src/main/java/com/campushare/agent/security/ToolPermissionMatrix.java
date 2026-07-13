package com.campushare.agent.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.tool")
public class ToolPermissionMatrix {

    private Map<String, ToolPermission> permissions = new HashMap<>();

    @PostConstruct
    public void init() {
        if (permissions.isEmpty()) {
            permissions.put("search_posts", ToolPermission.builder()
                    .toolName("search_posts")
                    .allowedRoles(new String[]{"USER", "ADMIN"})
                    .rateLimitPerMinute(60)
                    .maxDailyUsage(1000)
                    .requiresAuth(true)
                    .build());

            permissions.put("search_knowledge", ToolPermission.builder()
                    .toolName("search_knowledge")
                    .allowedRoles(new String[]{"USER", "ADMIN"})
                    .rateLimitPerMinute(30)
                    .maxDailyUsage(500)
                    .requiresAuth(true)
                    .build());

            permissions.put("navigate_to_page", ToolPermission.builder()
                    .toolName("navigate_to_page")
                    .allowedRoles(new String[]{"USER", "ADMIN"})
                    .rateLimitPerMinute(120)
                    .maxDailyUsage(2000)
                    .requiresAuth(true)
                    .build());
        }
        log.info("Tool permission matrix initialized with {} tools", permissions.size());
    }

    public ToolPermission getPermission(String toolName) {
        return permissions.getOrDefault(toolName, ToolPermission.builder()
                .toolName(toolName)
                .allowedRoles(new String[]{"ADMIN"})
                .requiresAuth(true)
                .build());
    }

    public boolean isAllowed(String toolName, String role) {
        ToolPermission permission = getPermission(toolName);
        if (permission == null) return false;
        if (permission.getAllowedRoles() == null) return false;
        for (String allowedRole : permission.getAllowedRoles()) {
            if (allowedRole.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ToolPermission {
        private String toolName;
        private String[] allowedRoles;
        private int rateLimitPerMinute;
        private int maxDailyUsage;
        private boolean requiresAuth;
        private String[] allowedParameters;
        private Map<String, String[]> parameterWhitelist;
    }
}
