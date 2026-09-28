package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.AccessLogDtos;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.enums.AccessAction;
import org.acme.security.CurrentAccount;
import org.acme.service.AccessService;
import org.acme.service.AuditService;

import java.util.UUID;

/**
 * Аудит доступа к документу.
 */
@Path("/api/documents/{documentId}/audit")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentAuditResource {

    @Inject AuditService audit;
    @Inject AccessService access;
    @Inject CurrentAccount current;

    /**
     * Страница журнала аудита.
     *
     * @param documentId     документ
     * @param action         фильтр по действию
     * @param accountId      фильтр по аккаунту
     * @param versionId      фильтр по версии
     * @param withoutVersion только записи без версии
     * @param deniedOnly     только отказанные попытки
     * @param page           номер страницы (0-based)
     * @param size           размер страницы (1..200)
     * @return страница записей
     */
    @GET
    @Transactional
    public AccessLogDtos.AccessLogPageDto list(@PathParam("documentId") UUID documentId,
                                               @QueryParam("action") String action,
                                               @QueryParam("accountId") UUID accountId,
                                               @QueryParam("versionId") UUID versionId,
                                               @QueryParam("withoutVersion")
                                               @DefaultValue("false") boolean withoutVersion,
                                               @QueryParam("deniedOnly")
                                               @DefaultValue("false") boolean deniedOnly,
                                               @QueryParam("page")
                                               @DefaultValue("0") int page,
                                               @QueryParam("size")
                                               @DefaultValue("50") int size) {
        requireOwnerOrAdmin(documentId);
        AccessAction act = parseAction(action);
        return audit.list(documentId, act, accountId, versionId, withoutVersion,
                deniedOnly, page, size);
    }

    @GET
    @Path("/stats")
    @Transactional
    public AccessLogDtos.AccessLogStatsDto stats(@PathParam("documentId") UUID documentId) {
        requireOwnerOrAdmin(documentId);
        return audit.stats(documentId);
    }

    private void requireOwnerOrAdmin(UUID documentId) {
        Document d = Document.findById(documentId);
        if (d == null || d.deletedAt != null) {
            throw new NotFoundException("Document not found");
        }
        Account actor = current.get();
        access.requireOwnerOrAdmin(d.project.id, actor);
    }

    private AccessAction parseAction(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AccessAction.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("invalid action: " + raw);
        }
    }
}