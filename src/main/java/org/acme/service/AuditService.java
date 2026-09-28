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

    /** Верхняя граница размера страницы. */
    private static final int MAX_PAGE_SIZE = 200;

    @Inject RequestMetaProvider metaProvider;

    /**
     * Пишет запись успешного доступа.
     */
    @Transactional
    public void record(UUID documentId, UUID versionId, Account actor, AccessAction action) {
        write(documentId, versionId, null, actor, action, null);
    }

    /**
     * Пишет запись отказанной попытки. Транзакция независимая.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordDenied(UUID documentId,
                             AccessAction action,
                             Integer attemptedVersionNumber,
                             Account actor,
                             String reason) {
        write(documentId, null, attemptedVersionNumber, actor, action, reason);
    }

    private void write(UUID documentId,
                       UUID versionId,
                       Integer attemptedVersionNumber,
                       Account actor,
                       AccessAction action,
                       String deniedReason) {
        var meta = metaProvider.current();
        var parsed = UserAgentParser.parse(meta.userAgent());

        DocumentAccessLog log = new DocumentAccessLog();
        log.documentId = documentId;
        log.versionId = versionId;
        log.attemptedVersionNumber = attemptedVersionNumber;
        log.accountId = actor == null ? null : actor.id;
        log.action = action;
        log.deniedReason = deniedReason;
        log.ipAddress = meta.ip();
        log.userAgent = meta.userAgent();
        log.deviceType = parsed.device();
        log.osName = parsed.os();
        log.browserName = parsed.browser();
        log.persist();
    }

    /**
     * Страница записей аудита.
     *
     * @param documentId     документ
     * @param action         фильтр по действию
     * @param accountId      фильтр по аккаунту
     * @param versionId      фильтр по конкретной версии
     * @param withoutVersion только записи без привязки к версии
     * @param deniedOnly     только отказанные попытки
     * @param page           номер страницы (0-based)
     * @param size           размер страницы
     * @return страница с общим количеством
     */
    @Transactional
    public AccessLogDtos.AccessLogPageDto list(UUID documentId,
                                               AccessAction action,
                                               UUID accountId,
                                               UUID versionId,
                                               boolean withoutVersion,
                                               boolean deniedOnly,
                                               int page,
                                               int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        // Общая часть WHERE, одинаковая для выборки и count.
        StringBuilder where = new StringBuilder(" where l.documentId = :docId");
        Map<String, Object> params = new HashMap<>();
        params.put("docId", documentId);

        if (action != null) {
            where.append(" and l.action = :action");
            params.put("action", action);
        }
        if (accountId != null) {
            where.append(" and l.accountId = :accountId");
            params.put("accountId", accountId);
        }
        if (deniedOnly) {
            where.append(" and l.deniedReason is not null");
        }
        if (withoutVersion) {
            where.append(" and l.versionId is null and l.deniedReason is null");
        } else if (versionId != null) {
            where.append(" and l.versionId = :versionId");
            params.put("versionId", versionId);
        }

        // 1) count
        long total = DocumentAccessLog
                .find("select count(l) from DocumentAccessLog l" + where, params)
                .project(Long.class)
                .firstResult();

        int totalPages = total == 0 ? 0 : (int) ((total + safeSize - 1) / safeSize);

        // 2) выборка страницы с join fetch на account/version
        String dataJpql =
                "select l from DocumentAccessLog l " +
                        "left join fetch l.account " +
                        "left join fetch l.version " +
                        where +
                        " order by l.occurredAt desc, l.id desc";

        List<DocumentAccessLog> rows = DocumentAccessLog
                .find(dataJpql, params)
                .page(safePage, safeSize)
                .list();

        List<AccessLogDtos.AccessLogEntryDto> dtos = rows.stream()
                .map(this::toEntry)
                .toList();

        return new AccessLogDtos.AccessLogPageDto(
                documentId, dtos, safePage, safeSize, total, totalPages);
    }

    /**
     * Агрегированная статистика по документу.
     */
    @Transactional
    public AccessLogDtos.AccessLogStatsDto stats(UUID documentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> grouped = DocumentAccessLog.getEntityManager()
                .createQuery(
                        "select l.versionId, l.action, count(l), max(l.occurredAt) " +
                                "from DocumentAccessLog l " +
                                "where l.documentId = :docId and l.deniedReason is null " +
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
            perVersionLast.merge(versionId, lastAt, (a, b) -> a.isAfter(b) ? a : b);
        }

        long totalDenied = DocumentAccessLog.count(
                "documentId = ?1 and deniedReason is not null", documentId);

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
                                "  and l.deniedReason is null " +
                                "  and l.accountId is not null " +
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
                totalDenied,
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
                l.attemptedVersionNumber,
                l.accountId,
                l.account == null ? null : l.account.displayName,
                l.account == null ? null : l.account.email,
                l.ipAddress,
                l.deviceType,
                l.osName,
                l.browserName,
                l.userAgent,
                l.deniedReason);
    }
}