package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.dto.AccessLogDtos;
import org.acme.entity.Account;
import org.acme.entity.DocumentAccessLog;
import org.acme.entity.DocumentVersion;
import org.acme.enums.AccessAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Запись и чтение аудита доступа к документам.
 */
@ApplicationScoped
public class AuditService {

    @Inject RequestMetaProvider metaProvider;

    /**
     * Пишет запись аудита. Вызывается из ресурсов после проверки доступа —
     * то есть логируем только успешные действия.
     *
     * @param documentId документ
     * @param versionId  версия (для VIEW = null)
     * @param actor      кто
     * @param action     действие
     */
    @Transactional
    public void record(UUID documentId, UUID versionId, Account actor, AccessAction action) {
        var meta = metaProvider.current();
        var parsed = UserAgentParser.parse(meta.userAgent());

        DocumentAccessLog log = new DocumentAccessLog();
        log.documentId = documentId;
        log.versionId = versionId;
        log.accountId = actor.id;
        log.action = action;
        log.ipAddress = meta.ip();
        log.userAgent = meta.userAgent();
        log.deviceType = parsed.device();
        log.osName = parsed.os();
        log.browserName = parsed.browser();
        log.persist();
    }

    /**
     * Постраничный список записей аудита.
     *
     * @param documentId     документ
     * @param action         фильтр по действию или null
     * @param accountId      фильтр по аккаунту или null
     * @param versionId      фильтр по конкретной версии или null
     * @param withoutVersion если {@code true} — только записи без версии (VIEW)
     * @param before         курсор (occurred_at &lt;) или null
     * @param limit          размер страницы
     * @return страница с курсором
     */
    @Transactional
    public AccessLogDtos.AccessLogPageDto list(UUID documentId,
                                               AccessAction action,
                                               UUID accountId,
                                               UUID versionId,
                                               boolean withoutVersion,
                                               Instant before,
                                               int limit) {
        int size = Math.min(Math.max(limit, 1), 200);

        StringBuilder jpql = new StringBuilder(
                "select l from DocumentAccessLog l " +
                        "join fetch l.account " +
                        "left join fetch l.version " +
                        "where l.documentId = :docId");
        Map<String, Object> params = new HashMap<>();
        params.put("docId", documentId);

        if (action != null) {
            jpql.append(" and l.action = :action");
            params.put("action", action);
        }
        if (accountId != null) {
            jpql.append(" and l.accountId = :accountId");
            params.put("accountId", accountId);
        }
        if (withoutVersion) {
            jpql.append(" and l.versionId is null");
        } else if (versionId != null) {
            jpql.append(" and l.versionId = :versionId");
            params.put("versionId", versionId);
        }
        if (before != null) {
            jpql.append(" and l.occurredAt < :before");
            params.put("before", before);
        }
        jpql.append(" order by l.occurredAt desc, l.id desc");

        List<DocumentAccessLog> rows = DocumentAccessLog
                .find(jpql.toString(), params)
                .page(0, size + 1)
                .list();

        Instant nextCursor = null;
        if (rows.size() > size) {
            DocumentAccessLog last = rows.get(size - 1);
            nextCursor = last.occurredAt;
            rows = rows.subList(0, size);
        }

        List<AccessLogDtos.AccessLogEntryDto> dtos = rows.stream()
                .map(this::toEntry)
                .toList();

        return new AccessLogDtos.AccessLogPageDto(documentId, dtos, nextCursor);
    }

    /**
     * Агрегированная статистика по документу.
     *
     * @param documentId документ
     * @return статистика
     */
    @Transactional
    public AccessLogDtos.AccessLogStatsDto stats(UUID documentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> grouped = DocumentAccessLog.getEntityManager()
                .createQuery(
                        "select l.versionId, l.action, count(l), max(l.occurredAt) " +
                                "from DocumentAccessLog l " +
                                "where l.documentId = :docId " +
                                "group by l.versionId, l.action",
                        Object[].class)
                .setParameter("docId", documentId)
                .getResultList();

        long totalViews = 0, totalPreviews = 0, totalDownloads = 0;

        Map<UUID, Map<AccessAction, long[]>> perVersionRaw = new HashMap<>();
        Map<UUID, Instant> perVersionLast = new HashMap<>();
        for (Object[] row : grouped) {
            UUID versionId = (UUID) row[0];
            AccessAction action = (AccessAction) row[1];
            long count = (Long) row[2];
            Instant lastAt = (Instant) row[3];

            switch (action) {
                case VIEW -> totalViews += count;
                case PREVIEW -> totalPreviews += count;
                case DOWNLOAD -> totalDownloads += count;
            }

            perVersionRaw.computeIfAbsent(versionId, k -> new HashMap<>())
                    .merge(action, new long[]{count}, (a, b) -> new long[]{a[0] + b[0]});
            perVersionLast.merge(versionId,
                    lastAt,
                    (a, b) -> a.isAfter(b) ? a : b);
        }

        Map<UUID, Integer> versionNumbers = new HashMap<>();
        List<DocumentVersion> versions = DocumentVersion
                .find("document.id = ?1", documentId).list();
        for (DocumentVersion v : versions) {
            versionNumbers.put(v.id, v.versionNumber);
        }

        List<AccessLogDtos.PerVersionStatDto> perVersion = new ArrayList<>();
        for (var e : perVersionRaw.entrySet()) {
            UUID vid = e.getKey();
            Map<AccessAction, long[]> actions = e.getValue();
            perVersion.add(new AccessLogDtos.PerVersionStatDto(
                    vid,
                    versionNumbers.get(vid),
                    actionCount(actions, AccessAction.VIEW),
                    actionCount(actions, AccessAction.PREVIEW),
                    actionCount(actions, AccessAction.DOWNLOAD),
                    perVersionLast.get(vid)));
        }
        // Записи без версии — наверх, потом по убыванию номера версии.
        perVersion.sort((a, b) -> {
            if (a.versionNumber() == null && b.versionNumber() != null) return -1;
            if (a.versionNumber() != null && b.versionNumber() == null) return 1;
            int an = a.versionNumber() == null ? 0 : a.versionNumber();
            int bn = b.versionNumber() == null ? 0 : b.versionNumber();
            return Integer.compare(bn, an);
        });

        @SuppressWarnings("unchecked")
        List<Object[]> perUserRaw = DocumentAccessLog.getEntityManager()
                .createQuery(
                        "select l.accountId, l.action, count(l), max(l.occurredAt) " +
                                "from DocumentAccessLog l " +
                                "where l.documentId = :docId " +
                                "group by l.accountId, l.action",
                        Object[].class)
                .setParameter("docId", documentId)
                .getResultList();

        Map<UUID, Map<AccessAction, long[]>> userActions = new LinkedHashMap<>();
        Map<UUID, Instant> userLast = new HashMap<>();
        for (Object[] row : perUserRaw) {
            UUID uid = (UUID) row[0];
            AccessAction action = (AccessAction) row[1];
            long count = (Long) row[2];
            Instant lastAt = (Instant) row[3];

            userActions.computeIfAbsent(uid, k -> new HashMap<>())
                    .merge(action, new long[]{count}, (a, b) -> new long[]{a[0] + b[0]});
            userLast.merge(uid, lastAt, (a, b) -> a.isAfter(b) ? a : b);
        }

        Set<UUID> accountIds = userActions.keySet();
        Map<UUID, String> names = new HashMap<>();
        if (!accountIds.isEmpty()) {
            List<Account> accounts = Account.find("id in ?1", accountIds).list();
            for (Account a : accounts) names.put(a.id, a.displayName);
        }

        List<AccessLogDtos.PerUserStatDto> perUser = new ArrayList<>();
        for (var e : userActions.entrySet()) {
            UUID uid = e.getKey();
            Map<AccessAction, long[]> actions = e.getValue();

            perUser.add(new AccessLogDtos.PerUserStatDto(
                    uid,
                    names.getOrDefault(uid, "Неизвестный аккаунт"),
                    actionCount(actions, AccessAction.VIEW),
                    actionCount(actions, AccessAction.PREVIEW),
                    actionCount(actions, AccessAction.DOWNLOAD),
                    userLast.get(uid)));
        }
        perUser.sort((a, b) -> {
            long ta = a.views() + a.previews() + a.downloads();
            long tb = b.views() + b.previews() + b.downloads();
            return Long.compare(tb, ta);
        });

        long uniqueViewers = new HashSet<>(userActions.keySet()).size();

        return new AccessLogDtos.AccessLogStatsDto(
                documentId,
                totalViews,
                totalPreviews,
                totalDownloads,
                uniqueViewers,
                perVersion,
                perUser);
    }

    private long actionCount(Map<AccessAction, long[]> m, AccessAction a) {
        long[] v = m.get(a);
        return v == null ? 0 : v[0];
    }

    private AccessLogDtos.AccessLogEntryDto toEntry(DocumentAccessLog l) {
        return new AccessLogDtos.AccessLogEntryDto(
                l.id,
                l.occurredAt,
                l.action.name(),
                l.versionId,
                l.version == null ? null : l.version.versionNumber,
                l.accountId,
                l.account == null ? null : l.account.displayName,
                l.account == null ? null : l.account.email,
                l.ipAddress,
                l.deviceType,
                l.osName,
                l.browserName,
                l.userAgent);
    }
}