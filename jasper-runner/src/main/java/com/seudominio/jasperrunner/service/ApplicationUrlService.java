package com.seudominio.jasperrunner.service;

import com.seudominio.jasperrunner.config.JasperRunnerProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class ApplicationUrlService {

    private final JasperRunnerProperties properties;

    public ApplicationUrlService(JasperRunnerProperties properties) {
        this.properties = properties;
    }

    /** URL pública da aplicação (scheme + host + context path), sem barra final. */
    public String resolveBaseUrl() {
        String configured = properties.getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlash(configured);
        }

        String fromRequest = resolveFromCurrentRequest();
        if (fromRequest != null) {
            return fromRequest;
        }

        return "http://localhost:8090";
    }

    private String resolveFromCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }

        HttpServletRequest request = servletAttrs.getRequest();
        String baseUrl = ServletUriComponentsBuilder.fromContextPath(request)
            .replacePath(null)
            .replaceQuery(null)
            .build()
            .toUriString();

        return baseUrl.isBlank() ? null : stripTrailingSlash(baseUrl);
    }

    private static String stripTrailingSlash(String url) {
        return url.replaceAll("/$", "");
    }
}
