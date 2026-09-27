package org.acme.dto;

import java.util.UUID;

/**
 * Кандидат на добавление в проект: аккаунт, найденный поиском по email или имени.
 *
 * @param accountId   идентификатор
 * @param email       email
 * @param displayName отображаемое имя
 * @param avatarUrl   публичный URL аватара или {@code null}
 */
public record AccessCandidateDto(
        UUID accountId,
        String email,
        String displayName,
        String avatarUrl) {
}