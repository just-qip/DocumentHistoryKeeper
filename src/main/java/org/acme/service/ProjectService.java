package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;
import org.acme.entity.Project;

import java.util.List;
import java.util.UUID;

/** Сервис проектов. */
@ApplicationScoped
public class ProjectService {

    /**
     * Создаёт проект.
     *
     * @param name        название
     * @param description описание
     * @param actor       создатель
     * @return сохранённый проект
     */
    public Project create(String name, String description, Account actor) {
        Project p = new Project();
        p.name = name;
        p.description = description;
        p.createdBy = actor;
        p.persist();
        return p;
    }

    /**
     * @param page номер страницы
     * @param size размер страницы
     * @return список активных проектов
     */
    public List<Project> list(int page, int size) {
        return Project
                .<Project>find("archivedAt is null order by createdAt desc")
                .page(page, Math.min(Math.max(size, 1), 200))
                .list();
    }

    /**
     * @param id идентификатор
     * @return проект
     * @throws NotFoundException если не найден
     */
    public Project get(UUID id) {
        Project p = Project.findById(id);
        if (p == null) throw new NotFoundException("Project not found");
        return p;
    }
}