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

    /** Минимальная длина запроса для поиска кандидатов. */
    private static final int MIN_QUERY_LENGTH = 2;

    /**
     * @param account аккаунт
     * @return {@code true}, если это администратор
     */
    public boolean isAdmin(Account account) {
        return account != null && account.systemRole == SystemRole.ADMIN;
    }

    /**
     * @param projectId проект
     * @param actor     аккаунт
     * @return {@code true}, если актор — владелец проекта
     */
    public boolean isOwner(UUID projectId, Account actor) {
        if (actor == null) return false;
        return roleIn(projectId, actor.id) == ProjectRole.OWNER;
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
     * Требует роль OWNER проекта или системного админа.
     *
     * @param projectId проект
     * @param actor     аккаунт
     * @throws ForbiddenException если прав недостаточно
     */
    public void requireOwnerOrAdmin(UUID projectId, Account actor) {
        if (isAdmin(actor)) return;
        if (isOwner(projectId, actor)) return;
        throw new ForbiddenException("Требуются права владельца проекта");
    }

    /**
     * @param projectId проект
     * @return количество владельцев
     */
    public long countOwners(UUID projectId) {
        return ProjectAccess.count("project.id = ?1 and role = ?2",
                projectId, ProjectRole.OWNER);
    }

    /**
     * Возвращает список доступов с подтянутыми аккаунтами и теми,
     * кто выдал доступ — чтобы DtoMapper не дёргал lazy-поля N+1 раз.
     *
     * @param projectId проект
     * @return список записей
     */
    public List<ProjectAccess> listForProject(UUID projectId) {
        return ProjectAccess.find(
                "select pa from ProjectAccess pa " +
                        "join fetch pa.account " +
                        "left join fetch pa.grantedBy " +
                        "where pa.project.id = ?1 " +
                        "order by pa.grantedAt",
                projectId).list();
    }

    /**
     * Ищет кандидатов на добавление в проект: активные аккаунты,
     * у которых нет доступа к проекту, чей email или displayName
     * содержит {@code query} (без учёта регистра).
     *
     * <p>Если {@code query} короче {@value #MIN_QUERY_LENGTH} символов —
     * возвращает пустой список. Это защита и от неполного ввода, и от
     * «дамп всей базы» по пустому запросу.</p>
     *
     * @param projectId проект
     * @param query     подстрока для поиска (email или имя)
     * @param limit     максимум результатов (1..50)
     * @return список аккаунтов
     */
    public List<Account> searchCandidates(UUID projectId, String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String like = "%" + q + "%";

        return Account.find(
                        "select a from Account a " +
                                "where a.status = ?1 " +
                                "  and (lower(a.email) like ?2 or lower(a.displayName) like ?2) " +
                                "  and a.id not in (" +
                                "      select pa.account.id from ProjectAccess pa where pa.project.id = ?3" +
                                "  ) " +
                                "order by a.displayName asc, a.email asc",
                        Account.Status.ACTIVE, like, projectId)
                .page(0, Math.min(Math.max(limit, 1), 50))
                .list();
    }

    /**
     * @param projectId проект
     * @param accessId  запись
     * @return запись доступа
     * @throws NotFoundException если не найдена
     */
    public ProjectAccess getAccess(UUID projectId, UUID accessId) {
        ProjectAccess pa = ProjectAccess
                .find("id = ?1 and project.id = ?2", accessId, projectId)
                .firstResult();
        if (pa == null) throw new NotFoundException("Access not found");
        return pa;
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
        ProjectAccess pa = getAccess(projectId, accessId);
        pa.role = role;
        return pa;
    }

    /**
     * @param projectId проект
     * @param accessId  запись доступа
     */
    public void revoke(UUID projectId, UUID accessId) {
        getAccess(projectId, accessId).delete();
    }
}