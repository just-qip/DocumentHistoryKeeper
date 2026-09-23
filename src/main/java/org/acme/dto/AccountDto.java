package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Публичное представление аккаунта.
 *
 * @param id          идентификатор
 * @param tenantId    тенант
 * @param email       email
 * @param displayName отображаемое имя
 * @param status      строковое представление статуса
 * @param createdAt   момент создания
 */
public record AccountDto(
        UUID id,
        UUID tenantId,
        String email,
        String displayName,
        String status,
        Instant createdAt) {
}