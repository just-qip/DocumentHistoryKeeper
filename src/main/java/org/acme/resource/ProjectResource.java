package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.acme.dto.DocumentDto;
import org.acme.dto.ProjectDto;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.Project;
import org.acme.mapper.DtoMapper;
import org.acme.service.AccountService;
import org.acme.service.DocumentService;
import org.acme.service.ProjectService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * REST-ресурс проектов и документов в контексте проекта.
 */
@Path("/api/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    ProjectService projects;
    @Inject
    DocumentService documents;
    @Inject
    AccountService accounts;

    /**
     * Тело запроса на создание проекта.
     *
     * @param tenantId    тенант
     * @param name        название
     * @param description описание
     */
    public record CreateProjectRequest(UUID tenantId, String name, String description) {
    }

    /**
     * Создаёт проект.
     *
     * @param request тело запроса
     * @param actorId идентификатор актора из заголовка
     * @return 201 и созданный проект
     */
    @POST
    public Response create(CreateProjectRequest request,
                           @HeaderParam("X-Account-Id") UUID actorId) {
        Account actor = accounts.get(actorId);
        Project project = projects.create(request.tenantId(), request.name(),
                request.description(), actor);
        return Response.status(Response.Status.CREATED).entity(DtoMapper.toDto(project)).build();
    }

    /**
     * Постраничный список активных проектов.
     *
     * @param page номер страницы
     * @param size размер страницы
     * @return список DTO
     */
    @GET
    public List<ProjectDto> list(@QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("20") int size) {
        return DtoMapper.toProjectDtos(projects.list(page, size));
    }

    /**
     * Возвращает проект по id.
     *
     * @param id идентификатор
     * @return DTO проекта
     */
    @GET
    @Path("/{projectId}")
    public ProjectDto get(@PathParam("projectId") UUID id) {
        return DtoMapper.toDto(projects.get(id));
    }

    /* =====================================================
     *  Документы внутри проекта
     * ===================================================== */

    /**
     * Создаёт документ в проекте и его первую версию.
     *
     * @param projectId идентификатор проекта
     * @param title     заголовок
     * @param docKind   прикладной тип
     * @param file      загруженный файл
     * @param actorId   идентификатор актора
     * @param uriInfo   контекст для Location
     * @return 201 и метаданные созданного документа
     * @throws IOException если не удалось прочитать файл
     */
    @POST
    @Path("/{projectId}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createDocument(@PathParam("projectId") UUID projectId,
                                   @RestForm("title") String title,
                                   @RestForm("docKind") String docKind,
                                   @RestForm("file") FileUpload file,
                                   @HeaderParam("X-Account-Id") UUID actorId,
                                   @Context UriInfo uriInfo) throws IOException {

        Account actor = accounts.get(actorId);
        byte[] content = Files.readAllBytes(file.uploadedFile());

        Document document = documents.create(projectId, title, docKind,
                content, file.contentType(), file.fileName(), actor);

        return Response.created(
                        uriInfo.getBaseUriBuilder()
                                .path("api/documents/{id}")
                                .build(document.id))
                .entity(DtoMapper.toDto(document))
                .build();
    }

    /**
     * Список документов проекта.
     *
     * @param projectId идентификатор проекта
     * @param page      номер страницы
     * @param size      размер страницы
     * @return список DTO
     */
    @GET
    @Path("/{projectId}/documents")
    @Transactional
    public List<DocumentDto> listDocuments(@PathParam("projectId") UUID projectId,
                                           @QueryParam("page") @DefaultValue("0") int page,
                                           @QueryParam("size") @DefaultValue("50") int size) {
        List<Document> list = Document
                .<Document>find("project.id = ?1 and deletedAt is null order by updatedAt desc",
                        projectId)
                .page(page, Math.min(size, 200))
                .list();
        return DtoMapper.toDocumentDtos(list);
    }
}