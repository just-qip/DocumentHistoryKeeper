package org.acme.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.dto.AccountDto;
import org.acme.dto.DocumentDto;
import org.acme.dto.EventDto;
import org.acme.dto.ProjectDto;
import org.acme.dto.VersionMetaDto;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.DocumentEvent;
import org.acme.entity.DocumentVersion;
import org.acme.entity.Project;
import org.acme.util.HashUtil;

import java.util.List;

/**
 * Перевод доменных сущностей в DTO.
 *
 * <p>Правила:</p>
 * <ul>
 *   <li>Никаких {@code byte[]} наружу: хеши — в hex-строку.</li>
 *   <li>Ленивые связи — в плоские ссылки (UUID), не вложенные объекты.</li>
 *   <li>Поля, которых нет в DTO, не подгружаются из БД.</li>
 * </ul>
 */
public final class DtoMapper {

    private DtoMapper() {
        // utility class
    }

    /**
     * Маппит аккаунт в DTO.
     *
     * @param account сущность
     * @return DTO
     */
    public static AccountDto toDto(Account account) {
        return new AccountDto(
                account.id,
                account.tenantId,
                account.email,
                account.displayName,
                account.status.name(),
                account.createdAt);
    }

    /**
     * Маппит проект в DTO.
     *
     * @param project сущность
     * @return DTO
     */
    public static ProjectDto toDto(Project project) {
        return new ProjectDto(
                project.id,
                project.tenantId,
                project.name,
                project.description,
                project.createdAt,
                project.createdBy.id,
                project.archivedAt);
    }

    /**
     * Маппит список проектов.
     *
     * @param list список сущностей
     * @return список DTO
     */
    public static List<ProjectDto> toProjectDtos(List<Project> list) {
        return list.stream().map(DtoMapper::toDto).toList();
    }

    /**
     * Маппит документ в DTO.
     *
     * @param document сущность
     * @return DTO
     */
    public static DocumentDto toDto(Document document) {
        return new DocumentDto(
                document.id,
                document.project.id,
                document.title,
                document.docKind,
                document.currentVersionId,
                document.currentVersionAuthorId,
                document.versionSeq,
                document.createdAt,
                document.createdBy.id,
                document.updatedAt,
                document.deletedAt);
    }

    /**
     * Маппит список документов.
     *
     * @param list список сущностей
     * @return список DTO
     */
    public static List<DocumentDto> toDocumentDtos(List<Document> list) {
        return list.stream().map(DtoMapper::toDto).toList();
    }

    /**
     * Маппит версию в DTO с метаданными (без контента).
     *
     * @param version сущность
     * @return DTO
     */
    public static VersionMetaDto toMeta(DocumentVersion version) {
        return new VersionMetaDto(
                version.id,
                version.document.id,
                version.versionNumber,
                version.mimeType,
                version.originalName,
                version.sizeBytes,
                HashUtil.hex(version.contentHash),
                version.parentVersionId,
                version.author.id,
                version.comment,
                version.createdAt);
    }

    /**
     * Маппит список версий.
     *
     * @param list список сущностей
     * @return список DTO
     */
    public static List<VersionMetaDto> toVersionMetas(List<DocumentVersion> list) {
        return list.stream().map(DtoMapper::toMeta).toList();
    }

    /**
     * Маппит событие в DTO. Payload парсится из строки в JSON-узел.
     *
     * @param event        сущность
     * @param objectMapper маппер для парсинга payload
     * @return DTO
     */
    public static EventDto toDto(DocumentEvent event, ObjectMapper objectMapper) {
        return new EventDto(
                event.id,
                event.occurredAt,
                event.eventType.name(),
                event.actor.id,
                event.versionId,
                parsePayload(event.payload, objectMapper),
                HashUtil.hex(event.eventHash),
                event.prevEventHash == null ? null : HashUtil.hex(event.prevEventHash));
    }

    /**
     * Маппит список событий.
     *
     * @param list         список сущностей
     * @param objectMapper маппер для парсинга payload
     * @return список DTO
     */
    public static List<EventDto> toEventDtos(List<DocumentEvent> list, ObjectMapper objectMapper) {
        return list.stream().map(e -> toDto(e, objectMapper)).toList();
    }

    /**
     * Парсит JSON-строку payload в {@link JsonNode}.
     * Некорректная строка означает нарушение целостности данных —
     * это не «поле пустое», а именно повреждение, поэтому кидаем исключение.
     *
     * @param raw          строка из БД
     * @param objectMapper маппер
     * @return JSON-узел
     * @throws IllegalStateException если строка не парсится
     */
    private static JsonNode parsePayload(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupted event payload in database: " + raw, e);
        }
    }
}