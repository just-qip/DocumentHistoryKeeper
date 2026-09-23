package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
import org.acme.mapper.DtoMapper;
import org.acme.service.AccountService;
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

    @Inject
    DocumentService documents;
    @Inject
    VersioningService versioning;
    @Inject
    TimelineService timeline;
    @Inject
    MimeDetector mimeDetector;
    @Inject
    AccountService accounts;

    /**
     * Тело запроса на обновление метаданных.
     *
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
    public DocumentDto get(@PathParam("id") UUID id) {
        Document document = Document.findById(id);
        if (document == null || document.deletedAt != null) {
            throw new NotFoundException("Document not found");
        }
        return DtoMapper.toDto(document);
    }

    /**
     * Обновляет метаданные документа.
     *
     * @param id      идентификатор
     * @param request тело запроса
     * @param actorId идентификатор актора
     * @return обновлённый DTO
     */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public DocumentDto patch(@PathParam("id") UUID id,
                             UpdateMetadataRequest request,
                             @HeaderParam("X-Account-Id") UUID actorId) {
        Account actor = accounts.get(actorId);
        return DtoMapper.toDto(documents.updateMetadata(id, request.title(),
                request.docKind(), actor));
    }

    /**
     * Помечает документ удалённым.
     *
     * @param id      идентификатор
     * @param reason  причина
     * @param actorId идентификатор актора
     * @return 204
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id,
                           @QueryParam("reason") String reason,
                           @HeaderParam("X-Account-Id") UUID actorId) {
        documents.softDelete(id, accounts.get(actorId), reason);
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
    public List<VersionMetaDto> versions(@PathParam("id") UUID id) {
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
    public VersionMetaDto version(@PathParam("id") UUID id, @PathParam("n") int n) {
        return DtoMapper.toMeta(findVersion(id, n));
    }

    /**
     * Отдаёт контент версии как поток байтов.
     *
     * @param id идентификатор документа
     * @param n  номер версии
     * @return бинарный ответ
     */
    @GET
    @Path("/{id}/versions/{n}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response download(@PathParam("id") UUID id, @PathParam("n") int n) {
        DocumentVersion version = findVersion(id, n);
        byte[] content = version.content;

        String encodedName = URLEncoder.encode(version.originalName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        String disposition = "attachment; filename=\"" + version.originalName + "\"; " +
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
     * @param actorId идентификатор актора
     * @param uriInfo контекст для Location
     * @return 201 и метаданные новой версии
     * @throws IOException если не удалось прочитать файл
     */
    @POST
    @Path("/{id}/versions")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadVersion(@PathParam("id") UUID id,
                                  @RestForm("file") FileUpload file,
                                  @RestForm("comment") String comment,
                                  @HeaderParam("X-Account-Id") UUID actorId,
                                  @Context UriInfo uriInfo) throws IOException {

        Account actor = accounts.get(actorId);
        byte[] content = Files.readAllBytes(file.uploadedFile());

        Document document = Document.findById(id);
        if (document == null || document.deletedAt != null) {
            throw new NotFoundException("Document not found");
        }

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
    public TimelinePageDto timeline(@PathParam("id") UUID id,
                                    @QueryParam("before") String beforeIso,
                                    @QueryParam("limit") @DefaultValue("50") int limit) {
        Instant before = (beforeIso == null || beforeIso.isBlank())
                ? null
                : Instant.parse(beforeIso);
        return timeline.timeline(id, before, limit);
    }

    /* =====================================================
     *  helper
     * ===================================================== */

    /**
     * Ищет версию по документу и номеру.
     *
     * @param documentId документ
     * @param number     номер версии
     * @return найденная версия
     * @throws NotFoundException если версия не найдена
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