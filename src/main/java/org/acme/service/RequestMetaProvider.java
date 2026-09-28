package org.acme.service;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Извлекает IP и User-Agent текущего запроса.
 */
@RequestScoped
public class RequestMetaProvider {

    /**
     * @param ip        клиентский IP или null
     * @param userAgent сырой User-Agent или null
     */
    public record RequestMeta(String ip, String userAgent) {
    }

    @Inject CurrentVertxRequest currentVertxRequest;
    @Inject HttpHeaders headers;

    /**
     * @return мета текущего HTTP-запроса
     */
    public RequestMeta current() {
        return new RequestMeta(clientIp(), userAgent());
    }

    private String userAgent() {
        String ua = headers.getHeaderString("User-Agent");
        return ua == null || ua.isBlank() ? null : ua;
    }

    private String clientIp() {
        String xff = headers.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = headers.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        try {
            var ctx = currentVertxRequest.getCurrent();
            if (ctx == null) return null;
            var addr = ctx.request().remoteAddress();
            return addr == null ? null : addr.hostAddress();
        } catch (Exception e) {
            return null;
        }
    }
}