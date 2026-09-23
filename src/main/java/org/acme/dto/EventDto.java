package org.acme.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Событие хронологии.
 *
 * @param id             первичный ключ
 * @param at             момент события
 * @param type           тип
 * @param actorId        id актора
 * @param versionId      связанная версия или {@code null}
 * @param payload        произвольные данные в виде JSON-узла
 * @param eventHash      хеш события (hex)
 * @param prevEventHash  хеш предыдущего события (hex) или {@code null}
 */
public record EventDto(
        Long id,
        Instant at,
        String type,
        UUID actorId,
        UUID versionId,
        JsonNode payload,
        String eventHash,
        String prevEventHash) {
}