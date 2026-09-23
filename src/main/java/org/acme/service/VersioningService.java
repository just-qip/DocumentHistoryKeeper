package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.DocumentVersion;
import org.acme.enums.EventType;
import org.acme.util.HashUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Создание новых версий документа.
 */
@ApplicationScoped
public class VersioningService {

    @Inject
    EventService events;

    /**
     * Создаёт новую версию и событие.
     *
     * @param documentId   документ
     * @param content      байты
     * @param mimeType     MIME
     * @param originalName имя файла
     * @param author       автор
     * @param comment      комментарий
     * @return версия
     */
    @Transactional
    public DocumentVersion createVersion(UUID documentId,
                                         byte[] content,
                                         String mimeType,
                                         String originalName,
                                         Account author,
                                         String comment) {

        Document doc = Document.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
        if (doc == null || doc.deletedAt != null) {
            throw new NotFoundException("Document not found: " + documentId);
        }

        DocumentVersion latest = DocumentVersion
                .find("document.id = ?1 order by versionNumber desc", documentId)
                .firstResult();
        int next = (latest == null) ? 1 : latest.versionNumber + 1;

        byte[] hash = HashUtil.sha256(content);

        DocumentVersion version = new DocumentVersion();
        version.document = doc;
        version.versionNumber = next;
        version.content = content;
        version.mimeType = mimeType;
        version.originalName = originalName;
        version.sizeBytes = content.length;
        version.contentHash = hash;
        version.parentVersionId = doc.currentVersionId;
        version.author = author;
        version.comment = comment;
        version.persist();

        doc.currentVersionId = version.id;
        doc.currentVersionAuthorId = author.id;
        doc.updatedAt = version.createdAt;

        Map<String, Object> payload = new HashMap<>();
        payload.put("versionNumber", next);
        payload.put("sha256", HashUtil.hex(hash));
        payload.put("sizeBytes", version.sizeBytes);
        payload.put("mimeType", mimeType);
        if (comment != null && !comment.isBlank()) {
            payload.put("comment", comment);
        }

        events.append(documentId, doc.project.id, version.id,
                next == 1 ? EventType.CREATED : EventType.UPLOADED,
                author,
                payload);

        return version;
    }
}