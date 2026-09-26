package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;
import org.acme.entity.Project;
import org.acme.entity.ProjectAccess;
import org.acme.enums.ProjectRole;
import org.acme.enums.SystemRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Управление доступами аккаунтов к проектам. */
@ApplicationScoped
public class AccessService {

    /**
     * @param account аккаунт
     * @return {@code true}, если это администратор
     */
    public boolean isAdmin(Account account) {
        return account != null && account.systemRole == SystemRole.ADMIN;
    }

    /**
     * @param projectId проект
     * @param accountId аккаунт
     * @return роль или {@code null}, если доступа нет
     */
    public ProjectRole roleIn(UUID projectId, UUID accountId) {
        ProjectAccess pa = ProjectAccess
                .find("project.id = ?1 and account.id = ?2", projectId, accountId)
                .firstResult();
        return pa == null ? null : pa.role;
    }

    /**
     * @param projectId проект
     * @param account   аккаунт
     * @param required  минимальная роль
     * @return {@code true}, если доступ разрешён
     */
    public boolean hasAccess(UUID projectId, Account account, ProjectRole required) {
        if (account == null) return false;
        if (isAdmin(account)) return true;
        ProjectRole r = roleIn(projectId, account.id);
        return r != null && r.atLeast(required);
    }

    /**
     * @param projectId проект
     * @param account   аккаунт
     * @param required  минимальная роль
     * @throws ForbiddenException если прав недостаточно
     */
    public void require(UUID projectId, Account account, ProjectRole required) {
        if (!hasAccess(projectId, account, required)) {
            throw new ForbiddenException("Недостаточно прав на проект");
        }
    }

    /**
     * @param projectId проект
     * @return все доступы проекта
     */
    public List<ProjectAccess> listForProject(UUID projectId) {
        return ProjectAccess
                .find("project.id = ?1 order by grantedAt", projectId)
                .list();
    }

    /**
     * Выдаёт или обновляет доступ.
     *
     * @param projectId проект
     * @param accountId аккаунт
     * @param role      роль
     * @param actor     кто выполняет
     * @return сохранённая запись
     */
    public ProjectAccess grant(UUID projectId, UUID accountId, ProjectRole role, Account actor) {
        Project project = Project.findById(projectId);
        if (project == null || project.archivedAt != null) {
            throw new NotFoundException("Project not found");
        }
        Account target = Account.findById(accountId);
        if (target == null) {
            throw new NotFoundException("Account not found");
        }
        ProjectAccess pa = ProjectAccess
                .find("project.id = ?1 and account.id = ?2", projectId, accountId)
                .firstResult();
        if (pa == null) {
            pa = new ProjectAccess();
            pa.id = UUID.randomUUID();
            pa.project = project;
            pa.account = target;
            pa.role = role;
            pa.grantedAt = Instant.now();
            pa.grantedBy = actor;
            pa.persist();
        } else {
            pa.role = role;
        }
        return pa;
    }

    /**
     * @param projectId проект
     * @param accessId  запись доступа
     * @param role      новая роль
     * @return обновлённая запись
     */
    public ProjectAccess changeRole(UUID projectId, UUID accessId, ProjectRole role) {
        ProjectAccess pa = findAccess(projectId, accessId);
        pa.role = role;
        return pa;
    }

    /**
     * @param projectId проект
     * @param accessId  запись доступа
     */
    public void revoke(UUID projectId, UUID accessId) {
        findAccess(projectId, accessId).delete();
    }

    private ProjectAccess findAccess(UUID projectId, UUID accessId) {
        ProjectAccess pa = ProjectAccess
                .find("id = ?1 and project.id = ?2", accessId, projectId)
                .firstResult();
        if (pa == null) throw new NotFoundException("Access not found");
        return pa;
    }
}