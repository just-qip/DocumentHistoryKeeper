package org.acme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.EventDto;
import org.acme.dto.TimelinePageDto;
import org.acme.entity.DocumentEvent;
import org.acme.mapper.DtoMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Формирует страницы хронологии документа.
 */
@ApplicationScoped
public class TimelineService {

    /** Верхняя граница размера страницы. */
    private static final int MAX_LIMIT = 200;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Возвращает страницу событий, отсортированных по времени по убыванию.
     *
     * @param documentId документ
     * @param before     курсор: отдать события строго раньше этого момента
     * @param limit      желаемый размер страницы (будет ограничен)
     * @return страница хронологии
     */
    public TimelinePageDto timeline(UUID documentId, Instant before, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);

        Sort sort = Sort.by("occurredAt").descending().and("id").descending();

        PanacheQuery<DocumentEvent> query = (before == null)
                ? DocumentEvent.find("documentId = ?1", sort, documentId)
                : DocumentEvent.find("documentId = ?1 and occurredAt < ?2",
                sort, documentId, before);

        List<DocumentEvent> events = query.page(0, capped).list();

        Instant next = events.size() == capped
                ? events.get(events.size() - 1).occurredAt
                : null;

        List<EventDto> dtos = DtoMapper.toEventDtos(events, objectMapper);

        return new TimelinePageDto(documentId, dtos, next);
    }
}