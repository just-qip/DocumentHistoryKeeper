package org.acme.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.DocumentEvent;
import org.acme.enums.EventType;
import org.acme.util.HashUtil;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only запись событий хронологии с хеш-цепочкой.
 */
@ApplicationScoped
public class EventService {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Добавляет событие в хронологию документа.
     *
     * <p>Пессимистичная блокировка строки документа гарантирует, что два
     * параллельных события не получат одинаковый {@code prevEventHash}.
     * Payload сериализуется в стабильную JSON-строку (с сортировкой ключей)
     * до записи в БД — та же строка участвует в расчёте хеша.</p>
     *
     * @param documentId документ
     * @param projectId  проект (денормализация)
     * @param versionId  версия или {@code null}
     * @param type       тип события
     * @param actor      актор
     * @param payload    произвольные данные
     * @return сохранённое событие
     * @throws NotFoundException если документ не найден
     */
    @Transactional
    public DocumentEvent append(UUID documentId,
                                UUID projectId,
                                UUID versionId,
                                EventType type,
                                Account actor,
                                Map<String, Object> payload) {

        Document locked = Document.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
        if (locked == null) {
            throw new NotFoundException("Document not found: " + documentId);
        }

        DocumentEvent last = DocumentEvent
                .find("documentId = ?1 order by id desc", documentId)
                .firstResult();

        byte[] prevHash = last != null ? last.eventHash : null;
        Instant now = Instant.now();
        String payloadJson = serializePayload(payload);

        DocumentEvent event = new DocumentEvent();
        event.documentId = documentId;
        event.projectId = projectId;
        event.versionId = versionId;
        event.eventType = type;
        event.actor = actor;
        event.occurredAt = now;
        event.payload = payloadJson;
        event.prevEventHash = prevHash;
        event.eventHash = computeHash(prevHash, documentId, type, actor.id, now, payloadJson);
        event.persist();
        return event;
    }

    /**
     * Сериализует payload в стабильную JSON-строку: ключи сортируются
     * по алфавиту, что даёт детерминированный результат для одинаковых карт.
     * Пустой {@code null} превращается в {@code "{}"}.
     *
     * @param payload данные события
     * @return JSON-строка
     * @throws IllegalStateException если сериализация не удалась
     */
    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize payload", e);
        }
    }

    /**
     * Считает хеш события от предыдущего хеша и канонизированных полей.
     * Payload участвует в виде той же строки, что записана в БД — это
     * обеспечивает побайтовую воспроизводимость при верификации.
     *
     * @param prev        хеш предыдущего события
     * @param docId       идентификатор документа
     * @param type        тип события
     * @param actorId     идентификатор актора
     * @param at          момент события
     * @param payloadJson JSON-строка payload
     * @return SHA-256 хеш
     */
    private byte[] computeHash(byte[] prev, UUID docId, EventType type, UUID actorId,
                               Instant at, String payloadJson) {
        return HashUtil.sha256(
                prev,
                docId.toString().getBytes(StandardCharsets.UTF_8),
                type.name().getBytes(StandardCharsets.UTF_8),
                actorId.toString().getBytes(StandardCharsets.UTF_8),
                at.toString().getBytes(StandardCharsets.UTF_8),
                payloadJson.getBytes(StandardCharsets.UTF_8));
    }
}