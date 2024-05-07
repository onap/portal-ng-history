package org.onap.portalng.history.logging;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("logger")
public record LoggerProperties(
    String traceIdHeaderName,
    Boolean enabled, List<String> excludePaths) {
}