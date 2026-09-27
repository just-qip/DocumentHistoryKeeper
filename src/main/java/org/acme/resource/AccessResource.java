package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.AccessCandidateDto;
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
 * Управление доступами к проекту.
 *
 * <p>Просмотр доступен любому участнику проекта (VIEWER+).
 * Изменения — только владельцу проекта (OWNER) или системному админу.</p>
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
        access.require(projectId, current.get(), ProjectRole.VIEWER);
        return access.listForProject(projectId).stream()
                .map(DtoMapper::toDto).toList();
    }

    /**
     * Поиск кандидатов на добавление в проект.
     *
     * <p>Возвращает до 20 активных аккаунтов, у которых нет доступа к проекту,
     * чей email или displayName содержит {@code q} (case-insensitive).
     * Пустая строка допустима — вернёт первые 20 аккаунтов без доступа.</p>
     *
     * @param projectId проект
     * @param q         подстрока поиска
     * @param limit     максимум результатов (1..50)
     * @return список кандидатов
     */
    @GET
    @Path("/candidates")
    @Transactional
    public List<AccessCandidateDto> candidates(@PathParam("projectId") UUID projectId,
                                               @QueryParam("q") @DefaultValue("") String q,
                                               @QueryParam("limit") @DefaultValue("20") int limit) {
        access.requireOwnerOrAdmin(projectId, current.get());
        List<Account> found = access.searchCandidates(projectId, q, limit);
        return DtoMapper.toCandidates(found);
    }

    /**
     * @param projectId проект
     * @param req       тело запроса
     * @return 201 и созданная запись
     */
    @POST
    @Transactional
    public Response grant(@PathParam("projectId") UUID projectId, GrantRequest req) {
        Account actor = current.get();
        access.requireOwnerOrAdmin(projectId, actor);

        ProjectAccess pa = access.grant(projectId, req.accountId(), req.role(), actor);

        ProjectAccess fresh = access.listForProject(projectId).stream()
                .filter(x -> x.id.equals(pa.id))
                .findFirst()
                .orElse(pa);

        return Response.status(Response.Status.CREATED)
                .entity(DtoMapper.toDto(fresh))
                .build();
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
        access.requireOwnerOrAdmin(projectId, current.get());

        ProjectAccess existing = access.getAccess(projectId, accessId);

        if (existing.role == ProjectRole.OWNER
                && req.role() != ProjectRole.OWNER
                && access.countOwners(projectId) <= 1) {
            throw new BadRequestException("Нельзя разжаловать последнего владельца проекта");
        }

        access.changeRole(projectId, accessId, req.role());

        ProjectAccess fresh = access.listForProject(projectId).stream()
                .filter(x -> x.id.equals(accessId))
                .findFirst()
                .orElse(existing);

        return DtoMapper.toDto(fresh);
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
        access.requireOwnerOrAdmin(projectId, current.get());

        ProjectAccess existing = access.getAccess(projectId, accessId);

        if (existing.role == ProjectRole.OWNER && access.countOwners(projectId) <= 1) {
            throw new BadRequestException("Нельзя отозвать доступ у последнего владельца проекта");
        }

        access.revoke(projectId, accessId);
        return Response.noContent().build();
    }
}