package org.acme.security;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.enums.AccessAction;
import org.acme.service.AuditService;
import org.acme.service.SessionService;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Достаёт токен из {@code Authorization: Bearer ...},
 * резолвит аккаунт и кладёт в {@link CurrentAccount}.
 *
 * <p>Если токен невалиден и запрос адресован документу — пишет
 * отказ в журнал аудита перед тем, как вернуть 401.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionAuthFilter implements ContainerRequestFilter {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "auth/",
            "hello"
    );

    /**
     * Пути, для которых пишем аудит.
     * Группа 1 — documentId, группа 2 — номер версии (для content-эндпоинта).
     */
    private static final Pattern DOC_PATTERN = Pattern.compile(
            "^documents/" +
                    "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})" +
                    "(?:/versions/(\\d+)/content)?$"
    );

    @Inject CurrentAccount current;
    @Inject SessionService sessions;
    @Inject AuditService audit;

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
            String reason = (token == null || token.isBlank())
                    ? "NO_SESSION"
                    : "INVALID_SESSION";
            logDeniedAttempt(ctx, reason);
            throw new NotAuthorizedException("missing or invalid session");
        }
        current.set(a);
    }

    /**
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

    /**
     * Разбирает путь и пишет отказ в аудит, если это обращение к документу.
     * Пишем только если документ существует — случайные UUID игнорируем.
     *
     * @param ctx    контекст запроса
     * @param reason NO_SESSION / INVALID_SESSION
     */
    private void logDeniedAttempt(ContainerRequestContext ctx, String reason) {
        DocumentAttempt attempt = parseDocumentAttempt(ctx);
        if (attempt == null) return;

        // Отбрасываем попытки с рандомным UUID: жалко таблицу на брутфорс.
        if (Document.count("id = ?1", attempt.documentId()) == 0) return;

        try {
            audit.recordDenied(attempt.documentId(), attempt.action(),
                    attempt.versionNumber(), null, reason);
        } catch (Exception e) {
            // Аудит не должен ломать ответ клиенту.
            Log.warnf(e, "failed to write denied-attempt audit log");
        }
    }

    /**
     * @param ctx контекст
     * @return распознанное обращение или {@code null}
     */
    private DocumentAttempt parseDocumentAttempt(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (path == null || path.isEmpty()) return null;

        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.startsWith("api/")) p = p.substring(4);

        Matcher m = DOC_PATTERN.matcher(p);
        if (!m.matches()) return null;

        try {
            UUID docId = UUID.fromString(m.group(1));
            if (m.group(2) != null) {
                int vNum = Integer.parseInt(m.group(2));
                String mode = ctx.getUriInfo().getQueryParameters().getFirst("mode");
                AccessAction action = "preview".equalsIgnoreCase(mode)
                        ? AccessAction.PREVIEW
                        : AccessAction.DOWNLOAD;
                return new DocumentAttempt(docId, action, vNum);
            }
            return new DocumentAttempt(docId, AccessAction.VIEW, null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param documentId    документ
     * @param action        предполагавшееся действие
     * @param versionNumber номер версии из URL (или null)
     */
    private record DocumentAttempt(UUID documentId, AccessAction action, Integer versionNumber) {
    }
}