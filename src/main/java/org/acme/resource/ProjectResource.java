package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
import org.acme.enums.ProjectRole;
import org.acme.mapper.DtoMapper;
import org.acme.security.CurrentAccount;
import org.acme.service.AccessService;
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

    @Inject ProjectService projects;
    @Inject DocumentService documents;
    @Inject AccessService access;
    @Inject CurrentAccount current;

    /**
     * @param name        название
     * @param description описание
     */
    public record CreateProjectRequest(String name, String description) {
    }

    /**
     * Создаёт проект и выдаёт создателю роль OWNER.
     *
     * @param request тело запроса
     * @return 201 и созданный проект
     */
    @POST
    @Transactional
    public Response create(CreateProjectRequest request) {
        Account actor = current.get();
        Project project = projects.create(request.name(), request.description(), actor);
        access.grant(project.id, actor.id, ProjectRole.OWNER, actor);
        return Response.status(Response.Status.CREATED)
                .entity(DtoMapper.toDto(project, "OWNER"))
                .build();
    }

    /**
     * Постраничный список проектов, доступных актору.
     * Администратор видит все проекты.
     *
     * @param page номер страницы
     * @param size размер страницы
     * @return список DTO
     */
    @GET
    @Transactional
    public List<ProjectDto> list(@QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("20") int size) {
        Account actor = current.get();
        if (access.isAdmin(actor)) {
            return DtoMapper.toProjectDtos(projects.list(page, size));
        }
        List<Project> accessible = Project
                .find("id in (select pa.project.id from ProjectAccess pa where pa.account.id = ?1) " +
                        "and archivedAt is null order by createdAt desc", actor.id)
                .page(page, size)
                .list();
        return DtoMapper.toProjectDtos(accessible);
    }

    /**
     * Возвращает проект по id вместе с ролью текущего пользователя.
     *
     * @param id идентификатор
     * @return DTO проекта
     */
    @GET
    @Path("/{projectId}")
    @Transactional
    public ProjectDto get(@PathParam("projectId") UUID id) {
        Account actor = current.get();
        access.require(id, actor, ProjectRole.VIEWER);

        String myRole;
        if (access.isAdmin(actor)) {
            myRole = "ADMIN";
        } else {
            ProjectRole r = access.roleIn(id, actor.id);
            myRole = r == null ? null : r.name();
        }

        return DtoMapper.toDto(projects.get(id), myRole);
    }

    /**
     * Создаёт документ в проекте и его первую версию.
     *
     * @param projectId идентификатор проекта
     * @param title     заголовок
     * @param docKind   прикладной тип
     * @param file      загруженный файл
     * @param uriInfo   контекст для Location
     * @return 201 и метаданные созданного документа
     * @throws IOException если не удалось прочитать файл
     */
    @POST
    @Path("/{projectId}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response createDocument(@PathParam("projectId") UUID projectId,
                                   @RestForm("title") String title,
                                   @RestForm("docKind") String docKind,
                                   @RestForm("file") FileUpload file,
                                   @Context UriInfo uriInfo) throws IOException {

        Account actor = current.get();
        access.require(projectId, actor, ProjectRole.EDITOR);

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
        access.require(projectId, current.get(), ProjectRole.VIEWER);

        List<Document> list = Document
                .<Document>find("project.id = ?1 and deletedAt is null order by updatedAt desc",
                        projectId)
                .page(page, Math.min(size, 200))
                .list();
        return DtoMapper.toDocumentDtos(list);
    }
}