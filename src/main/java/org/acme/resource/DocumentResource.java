package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.acme.dto.DocumentDto;
import org.acme.dto.TimelinePageDto;
import org.acme.dto.VersionMetaDto;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.DocumentVersion;
import org.acme.enums.AccessAction;
import org.acme.enums.ProjectRole;
import org.acme.mapper.DtoMapper;
import org.acme.security.CurrentAccount;
import org.acme.service.AccessService;
import org.acme.service.AuditService;
import org.acme.service.DocumentService;
import org.acme.service.MimeDetector;
import org.acme.service.TimelineService;
import org.acme.service.VersioningService;
import org.acme.util.HashUtil;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST-ресурс документа: метаданные, версии, хронология.
 */
@Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentResource {

    @Inject DocumentService documents;
    @Inject VersioningService versioning;
    @Inject TimelineService timeline;
    @Inject MimeDetector mimeDetector;
    @Inject AccessService access;
    @Inject CurrentAccount current;
    @Inject AuditService audit;

    /**
     * @param title   новый заголовок или {@code null}
     * @param docKind новый тип или {@code null}
     */
    public record UpdateMetadataRequest(String title, String docKind) {
    }

    /* =====================================================
     *  Сам документ
     * ===================================================== */

    /**
     * Возвращает документ по id.
     *
     * @param id идентификатор
     * @return DTO документа
     */
    @GET
    @Path("/{id}")
    @Transactional
    public DocumentDto get(@PathParam("id") UUID id) {
        Document document = requireVisible(id, ProjectRole.VIEWER, AccessAction.VIEW);
        audit.record(document.id, null, current.get(), AccessAction.VIEW);
        return DtoMapper.toDto(document);
    }

    /**
     * Обновляет метаданные документа.
     *
     * @param id      идентификатор
     * @param request тело запроса
     * @return обновлённый DTO
     */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public DocumentDto patch(@PathParam("id") UUID id,
                             UpdateMetadataRequest request) {
        requireVisible(id, ProjectRole.EDITOR, null);
        Account actor = current.get();
        return DtoMapper.toDto(documents.updateMetadata(id, request.title(),
                request.docKind(), actor));
    }

    /**
     * Помечает документ удалённым.
     *
     * @param id     идентификатор
     * @param reason причина
     * @return 204
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id,
                           @QueryParam("reason") String reason) {
        requireVisible(id, ProjectRole.EDITOR, null);
        documents.softDelete(id, current.get(), reason);
        return Response.noContent().build();
    }

    /* =====================================================
     *  Версии документа
     * ===================================================== */

    /**
     * Список версий документа.
     *
     * @param id идентификатор
     * @return список DTO
     */
    @GET
    @Path("/{id}/versions")
    @Transactional
    public List<VersionMetaDto> versions(@PathParam("id") UUID id) {
        requireVisible(id, ProjectRole.VIEWER, AccessAction.VIEW);
        List<DocumentVersion> list = DocumentVersion
                .<DocumentVersion>find("document.id = ?1 order by versionNumber desc", id)
                .list();
        return DtoMapper.toVersionMetas(list);
    }

    /**
     * Возвращает метаданные конкретной версии.
     *
     * @param id идентификатор документа
     * @param n  номер версии
     * @return DTO версии
     */
    @GET
    @Path("/{id}/versions/{n}")
    @Transactional
    public VersionMetaDto version(@PathParam("id") UUID id, @PathParam("n") int n) {
        requireVisible(id, ProjectRole.VIEWER, AccessAction.VIEW);
        return DtoMapper.toMeta(findVersion(id, n));
    }

    /**
     * Отдаёт контент версии. Пишет в аудит PREVIEW или DOWNLOAD
     * в зависимости от {@code mode}.
     *
     * @param id   идентификатор документа
     * @param n    номер версии
     * @param mode preview или download
     * @return бинарный ответ
     */
    @GET
    @Path("/{id}/versions/{n}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response download(@PathParam("id") UUID id,
                             @PathParam("n") int n,
                             @QueryParam("mode") @DefaultValue("download") String mode) {
        AccessAction action = "preview".equalsIgnoreCase(mode)
                ? AccessAction.PREVIEW
                : AccessAction.DOWNLOAD;

        Document document = requireVisible(id, ProjectRole.VIEWER, action);
        DocumentVersion version = findVersion(id, n);

        audit.record(document.id, version.id, current.get(), action);

        byte[] content = version.content;

        String encodedName = URLEncoder.encode(version.originalName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        String dispositionType = action == AccessAction.PREVIEW ? "inline" : "attachment";
        String disposition = dispositionType + "; filename=\"" + version.originalName + "\"; " +
                "filename*=UTF-8''" + encodedName;

        return Response.ok(new ByteArrayInputStream(content))
                .header(HttpHeaders.CONTENT_TYPE, version.mimeType)
                .header(HttpHeaders.CONTENT_LENGTH, version.sizeBytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-SHA256", HashUtil.hex(version.contentHash))
                .build();
    }

    /**
     * Загружает новую версию документа.
     *
     * @param id      идентификатор документа
     * @param file    загруженный файл
     * @param comment комментарий
     * @param uriInfo контекст для Location
     * @return 201 и метаданные новой версии
     * @throws IOException если не удалось прочитать файл
     */
    @POST
    @Path("/{id}/versions")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadVersion(@PathParam("id") UUID id,
                                  @RestForm("file") FileUpload file,
                                  @RestForm("comment") String comment,
                                  @Context UriInfo uriInfo) throws IOException {

        requireVisible(id, ProjectRole.EDITOR, null);

        Account actor = current.get();
        byte[] content = Files.readAllBytes(file.uploadedFile());

        String declared = file.contentType();
        String mime = mimeDetector.isAllowed(declared)
                ? declared
                : mimeDetector.fromFilename(file.fileName())
                  .orElseThrow(() -> new BadRequestException("Unsupported content type"));

        DocumentVersion version = versioning.createVersion(id, content, mime,
                file.fileName(), actor, comment);

        return Response.created(
                        uriInfo.getBaseUriBuilder()
                                .path("api/documents/{id}/versions/{n}")
                                .build(id, version.versionNumber))
                .entity(DtoMapper.toMeta(version))
                .build();
    }

    /* =====================================================
     *  Хронология
     * ===================================================== */

    /**
     * Возвращает страницу хронологии документа.
     *
     * @param id        идентификатор документа
     * @param beforeIso ISO-8601 курсор
     * @param limit     размер страницы
     * @return страница хронологии
     */
    @GET
    @Path("/{id}/timeline")
    @Transactional
    public TimelinePageDto timeline(@PathParam("id") UUID id,
                                    @QueryParam("before") String beforeIso,
                                    @QueryParam("limit") @DefaultValue("50") int limit) {
        requireVisible(id, ProjectRole.VIEWER, AccessAction.VIEW);
        Instant before = (beforeIso == null || beforeIso.isBlank())
                ? null
                : Instant.parse(beforeIso);
        return timeline.timeline(id, before, limit);
    }

    /* =====================================================
     *  helper
     * ===================================================== */

    /**
     * Проверяет доступ к документу. При отказе и ненулевом
     * {@code auditAction} пишет запись в аудит с причиной.
     *
     * @param documentId  документ
     * @param required    требуемая роль
     * @param auditAction действие для аудита (nullable — для write-операций)
     * @return найденный документ
     */
    private Document requireVisible(UUID documentId,
                                    ProjectRole required,
                                    AccessAction auditAction) {
        Document document = Document.findById(documentId);
        if (document == null || document.deletedAt != null) {
            if (auditAction != null) {
                audit.recordDenied(documentId, auditAction, null,
                        current.get(), "NOT_FOUND");
            }
            throw new NotFoundException("Document not found");
        }
        Account actor = current.get();
        if (!access.hasAccess(document.project.id, actor, required)) {
            if (auditAction != null) {
                audit.recordDenied(documentId, auditAction, null,
                        actor, "NO_ACCESS");
            }
            throw new jakarta.ws.rs.ForbiddenException("Недостаточно прав на проект");
        }
        return document;
    }

    /**
     * @param documentId документ
     * @param number     номер версии
     * @return найденная версия
     */
    private DocumentVersion findVersion(UUID documentId, int number) {
        DocumentVersion version = DocumentVersion
                .find("document.id = ?1 and versionNumber = ?2", documentId, number)
                .firstResult();
        if (version == null) {
            throw new NotFoundException("Version not found");
        }
        return version;
    }
}