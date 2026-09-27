package org.acme.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.dto.AccountDto;
import org.acme.dto.DocumentDto;
import org.acme.dto.EventDto;
import org.acme.dto.ProjectAccessDto;
import org.acme.dto.ProjectDto;
import org.acme.dto.VersionMetaDto;
import org.acme.entity.Account;
import org.acme.entity.Document;
import org.acme.entity.DocumentEvent;
import org.acme.entity.DocumentVersion;
import org.acme.entity.Project;
import org.acme.entity.ProjectAccess;
import org.acme.util.HashUtil;

import java.util.List;

/**
 * Перевод доменных сущностей в DTO.
 */
public final class DtoMapper {

    private DtoMapper() {
        // utility class
    }

    /**
     * @param account сущность
     * @return DTO
     */
    public static AccountDto toDto(Account account) {
        return new AccountDto(
                account.id,
                account.email,
                account.displayName,
                account.status.name(),
                account.systemRole.name(),
                account.avatarUpdatedAt,
                account.createdAt);
    }

    /**
     * @param project сущность
     * @return DTO
     */
    public static ProjectDto toDto(Project project) {
        return new ProjectDto(
                project.id,
                project.name,
                project.description,
                project.createdAt,
                project.createdBy.id,
                project.archivedAt);
    }

    /**
     * @param list список сущностей
     * @return список DTO
     */
    public static List<ProjectDto> toProjectDtos(List<Project> list) {
        return list.stream().map(DtoMapper::toDto).toList();
    }

    /**
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
     * @param list список сущностей
     * @return список DTO
     */
    public static List<DocumentDto> toDocumentDtos(List<Document> list) {
        return list.stream().map(DtoMapper::toDto).toList();
    }

    /**
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
     * @param list список сущностей
     * @return список DTO
     */
    public static List<VersionMetaDto> toVersionMetas(List<DocumentVersion> list) {
        return list.stream().map(DtoMapper::toMeta).toList();
    }

    /**
     * @param event        сущность
     * @param objectMapper маппер для payload
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
     * @param list         список сущностей
     * @param objectMapper маппер для payload
     * @return список DTO
     */
    public static List<EventDto> toEventDtos(List<DocumentEvent> list, ObjectMapper objectMapper) {
        return list.stream().map(e -> toDto(e, objectMapper)).toList();
    }

    /**
     * @param access сущность
     * @return DTO
     */
    public static ProjectAccessDto toDto(ProjectAccess access) {
        return new ProjectAccessDto(
                access.id,
                access.project.id,
                access.account.id,
                access.role.name(),
                access.grantedAt,
                access.grantedBy == null ? null : access.grantedBy.id);
    }

    /**
     * @param list список сущностей
     * @return список DTO
     */
    public static List<ProjectAccessDto> toProjectAccessDtos(List<ProjectAccess> list) {
        return list.stream().map(DtoMapper::toDto).toList();
    }

    /**
     * @param raw          JSON-строка
     * @param objectMapper маппер
     * @return JSON-узел или {@code null}
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