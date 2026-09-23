package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.Project;
import org.acme.enums.EventType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Операции над документами: создание, метаданные, soft delete.
 */
@ApplicationScoped
public class DocumentService {

    @Inject
    MimeDetector mimeDetector;
    @Inject
    VersioningService versioning;
    @Inject
    EventService events;

    /** Максимальный размер документа в байтах. */
    @ConfigProperty(name = "app.docs.max-bytes", defaultValue = "15728640")
    long maxBytes;

    /**
     * Создаёт документ и его первую версию.
     *
     * @param projectId    проект
     * @param title        заголовок
     * @param docKind      прикладной тип
     * @param content      байты
     * @param declaredMime MIME из запроса
     * @param originalName имя файла
     * @param actor        создатель
     * @return сохранённый документ
     */
    @Transactional
    public Document create(UUID projectId,
                           String title,
                           String docKind,
                           byte[] content,
                           String declaredMime,
                           String originalName,
                           Account actor) {

        if (title == null || title.isBlank()) {
            throw new BadRequestException("title is required");
        }
        if (docKind == null || docKind.isBlank()) {
            throw new BadRequestException("docKind is required");
        }
        if (content == null || content.length == 0) {
            throw new BadRequestException("file is empty");
        }
        if (content.length > maxBytes) {
            throw new BadRequestException("file exceeds " + maxBytes + " bytes");
        }

        Project project = Project.findById(projectId);
        if (project == null || project.archivedAt != null) {
            throw new NotFoundException("Project not found: " + projectId);
        }

        String mime = resolveMime(declaredMime, originalName);
        String safeName = sanitizeName(originalName);

        Document document = new Document();
        document.project = project;
        document.title = title;
        document.docKind = docKind;
        document.createdBy = actor;
        document.persist();

        versioning.createVersion(document.id, content, mime, safeName, actor, "Initial version");

        return document;
    }

    /**
     * Обновляет метаданные документа и пишет событие, если что-то изменилось.
     *
     * @param documentId документ
     * @param title      новый заголовок или {@code null}
     * @param docKind    новый тип или {@code null}
     * @param actor      актор
     * @return обновлённый документ
     */
    @Transactional
    public Document updateMetadata(UUID documentId, String title, String docKind, Account actor) {
        Document document = Document.findById(documentId);
        if (document == null || document.deletedAt != null) {
            throw new NotFoundException("Document not found");
        }

        boolean titleChanged = title != null && !title.equals(document.title);
        boolean kindChanged = docKind != null && !docKind.equals(document.docKind);
        if (!titleChanged && !kindChanged) {
            return document;
        }

        Map<String, Object> payload = new HashMap<>();
        if (titleChanged) {
            document.title = title;
            payload.put("title", title);
        }
        if (kindChanged) {
            document.docKind = docKind;
            payload.put("docKind", docKind);
        }
        document.updatedAt = Instant.now();

        events.append(documentId, document.project.id, document.currentVersionId,
                EventType.METADATA_CHANGED, actor, payload);

        return document;
    }

    /**
     * Помечает документ удалённым и пишет событие.
     *
     * @param documentId документ
     * @param actor      актор
     * @param reason     причина удаления (может быть {@code null})
     */
    @Transactional
    public void softDelete(UUID documentId, Account actor, String reason) {
        Document document = Document.findById(documentId);
        if (document == null || document.deletedAt != null) {
            throw new NotFoundException("Document not found");
        }
        document.deletedAt = Instant.now();
        events.append(documentId, document.project.id, document.currentVersionId,
                EventType.DELETED, actor, Map.of("reason", reason == null ? "" : reason));
    }

    /**
     * Определяет MIME: доверяет declared, если он в белом списке,
     * иначе пробует по расширению файла.
     *
     * @param declared     MIME из запроса
     * @param originalName имя файла
     * @return MIME-тип
     */
    private String resolveMime(String declared, String originalName) {
        if (mimeDetector.isAllowed(declared)) {
            return declared;
        }
        return mimeDetector.fromFilename(originalName)
                .orElseThrow(() -> new BadRequestException(
                        "Unsupported content type: " + declared));
    }

    /**
     * Убирает из имени путь и управляющие символы.
     *
     * @param name исходное имя
     * @return безопасное имя
     */
    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String normalized = name.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.replaceAll("[\\p{Cntrl}]", "").trim();
        return normalized.isEmpty() ? "unnamed" : normalized;
    }
}