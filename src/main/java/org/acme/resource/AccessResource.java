package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.ProjectAccessDto;
import org.acme.entity.Account;
import org.acme.entity.ProjectAccess;
import org.acme.enums.ProjectRole;
import org.acme.mapper.DtoMapper;
import org.acme.security.CurrentAccount;
import org.acme.service.AccessService;

import java.util.List;
import java.util.UUID;

/**
 * Управление доступами к проекту. Только для администраторов тенанта.
 *
 * <p>Актор берётся из {@link CurrentAccount}, который заполняется
 * фильтром {@code SessionAuthFilter} по сессионному токену.</p>
 */
@Path("/api/projects/{projectId}/access")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccessResource {

    @Inject AccessService access;
    @Inject CurrentAccount current;

    /**
     * @param accountId аккаунт
     * @param role      роль
     */
    public record GrantRequest(UUID accountId, ProjectRole role) {
    }

    /**
     * @param role новая роль
     */
    public record ChangeRoleRequest(ProjectRole role) {
    }

    /**
     * @param projectId проект
     * @return список доступов
     */
    @GET
    @Transactional
    public List<ProjectAccessDto> list(@PathParam("projectId") UUID projectId) {
        requireAdmin();
        return access.listForProject(projectId).stream()
                .map(DtoMapper::toDto).toList();
    }

    /**
     * @param projectId проект
     * @param req       тело запроса
     * @return 201 и созданная запись
     */
    @POST
    @Transactional
    public Response grant(@PathParam("projectId") UUID projectId,
                          GrantRequest req) {
        Account actor = current.get();
        requireAdmin();
        ProjectAccess pa = access.grant(projectId, req.accountId(), req.role(), actor);
        return Response.status(Response.Status.CREATED).entity(DtoMapper.toDto(pa)).build();
    }

    /**
     * @param projectId проект
     * @param accessId  запись
     * @param req       тело запроса
     * @return обновлённая запись
     */
    @PATCH
    @Path("/{accessId}")
    @Transactional
    public ProjectAccessDto changeRole(@PathParam("projectId") UUID projectId,
                                       @PathParam("accessId") UUID accessId,
                                       ChangeRoleRequest req) {
        requireAdmin();
        return DtoMapper.toDto(access.changeRole(projectId, accessId, req.role()));
    }

    /**
     * @param projectId проект
     * @param accessId  запись
     * @return 204
     */
    @DELETE
    @Path("/{accessId}")
    @Transactional
    public Response revoke(@PathParam("projectId") UUID projectId,
                           @PathParam("accessId") UUID accessId) {
        requireAdmin();
        access.revoke(projectId, accessId);
        return Response.noContent().build();
    }

    /**
     * @throws ForbiddenException если текущий актор не админ
     */
    private void requireAdmin() {
        if (!access.isAdmin(current.get())) {
            throw new ForbiddenException("Требуются права администратора");
        }
    }
}