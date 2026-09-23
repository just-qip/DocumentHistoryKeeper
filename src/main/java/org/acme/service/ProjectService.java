package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;
import org.acme.entity.Project;

import java.util.List;
import java.util.UUID;

/**
 * Операции над проектами.
 */
@ApplicationScoped
public class ProjectService {

    /**
     * Создаёт проект.
     *
     * @param tenantId    тенант
     * @param name        название
     * @param description описание
     * @param actor       создатель
     * @return сохранённый проект
     * @throws BadRequestException если название пустое
     */
    @Transactional
    public Project create(UUID tenantId, String name, String description, Account actor) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        Project project = new Project();
        project.tenantId = tenantId;
        project.name = name;
        project.description = description;
        project.createdBy = actor;
        project.persist();
        return project;
    }

    /**
     * Постраничный список активных проектов.
     *
     * @param page номер страницы
     * @param size размер страницы
     * @return список проектов
     */
    public List<Project> list(int page, int size) {
        return Project
                .<Project>find("archivedAt is null order by createdAt desc")
                .page(page, Math.min(size, 100))
                .list();
    }

    /**
     * Возвращает проект или бросает 404.
     *
     * @param id идентификатор
     * @return проект
     * @throws NotFoundException если не найден
     */
    public Project get(UUID id) {
        Project project = Project.findById(id);
        if (project == null) {
            throw new NotFoundException("Project not found: " + id);
        }
        return project;
    }
}