package com.campushare.agent.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.mcp")
public class McpClientConfig {

    private List<ServerConfig> servers = new ArrayList<>();

    private boolean enabled = false;

    @Data
    public static class ServerConfig {
        private String name;
        private String url;
        private String transport = "streamable-http";
        private String command;
        private boolean enabled = true;
        private String authType;
        private String apiKey;
    }
}
