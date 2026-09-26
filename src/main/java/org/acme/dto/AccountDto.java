package org.acme.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Публичное представление аккаунта.
 *
 * @param id          идентификатор
 * @param email       email
 * @param displayName отображаемое имя
 * @param status      строковое представление статуса
 * @param systemRole  системная роль (USER / ADMIN)
 * @param createdAt   момент создания
 */
public record AccountDto(
        UUID id,
        String email,
        String displayName,
        String status,
        String systemRole,
        Instant createdAt) {
}