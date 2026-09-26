package org.acme.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.acme.entity.Account;
import org.acme.service.SessionService;

import java.io.IOException;
import java.util.Set;

/**
 * Достаёт токен из {@code Authorization: Bearer ...},
 * резолвит аккаунт и кладёт в {@link CurrentAccount}.
 *
 * <p>Публичные пути (auth/*, hello) пропускаются без проверки токена.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionAuthFilter implements ContainerRequestFilter {

    /**
     * Сегменты пути, для которых аутентификация не требуется.
     *
     * <p>Проверка идёт по «хвосту» пути после возможного префикса
     * (например, {@code api/}), чтобы не зависеть от
     * {@code @ApplicationPath} / {@code quarkus.http.root-path}.</p>
     */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "auth/",
            "hello"
    );

    @Inject CurrentAccount current;
    @Inject SessionService sessions;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if (isPublic(ctx.getUriInfo().getPath())) {
            return;
        }

        String header = ctx.getHeaderString("Authorization");
        String token = header == null ? null
                : (header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim());

        Account a = sessions.resolve(token);
        if (a == null) {
            throw new NotAuthorizedException("missing or invalid session");
        }
        current.set(a);
    }

    /**
     * Определяет, является ли путь публичным.
     *
     * <p>Нормализуем: убираем ведущий «/» и, если путь начинается с
     * {@code api/}, отрезаем этот префикс. Это делает проверку
     * устойчивой к тому, отрезает ли Quarkus {@code /api} или нет.</p>
     *
     * @param path значение {@link jakarta.ws.rs.core.UriInfo#getPath()}
     * @return {@code true}, если аутентификация не требуется
     */
    private boolean isPublic(String path) {
        if (path == null || path.isEmpty()) return false;
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.startsWith("api/")) p = p.substring(4);
        for (String prefix : PUBLIC_PREFIXES) {
            if (p.startsWith(prefix)) return true;
        }
        return false;
    }
}