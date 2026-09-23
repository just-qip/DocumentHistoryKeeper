package org.acme.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Страница хронологии документа с курсором для следующего запроса.
 *
 * <p>{@code nextCursor == null} означает, что событий больше нет.
 * Иначе следующий запрос:
 * {@code GET /documents/{id}/timeline?before={nextCursor}}.</p>
 *
 * @param documentId документ
 * @param entries    события на странице
 * @param nextCursor момент последнего события или {@code null}
 */
public record TimelinePageDto(
        UUID documentId,
        List<EventDto> entries,
        Instant nextCursor) {
}