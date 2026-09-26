package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище краткоживущих SRP-челленджей. In-memory, TTL = 5 минут.
 *
 * <p>Подходит для однонодового деплоя. Для кластера — вынести в Redis.</p>
 */
@ApplicationScoped
public class SrpChallengeStore {

    private static final Duration TTL = Duration.ofMinutes(5);

    /**
     * @param accountId аккаунт
     * @param b         приватный эфемер сервера
     * @param B         публичный эфемер сервера
     * @param salt      соль аккаунта (чтобы не читать повторно)
     * @param verifier  верификатор
     */
    public record Challenge(UUID accountId, BigInteger b, BigInteger B,
                            byte[] salt, BigInteger verifier, Instant expiresAt) {
    }

    private final ConcurrentHashMap<UUID, Challenge> store = new ConcurrentHashMap<>();

    /**
     * @param accountId аккаунт
     * @param b         эфемер
     * @param B         публичный эфемер
     * @param salt      соль
     * @param verifier  верификатор
     * @return id челленджа
     */
    public UUID put(UUID accountId, BigInteger b, BigInteger B, byte[] salt, BigInteger verifier) {
        cleanup();
        UUID id = UUID.randomUUID();
        store.put(id, new Challenge(accountId, b, B, salt, verifier,
                Instant.now().plus(TTL)));
        return id;
    }

    /**
     * @param id идентификатор
     * @return челлендж или {@code null}, если не найден/истёк
     */
    public Challenge consume(UUID id) {
        Challenge c = store.remove(id);
        if (c == null || c.expiresAt().isBefore(Instant.now())) return null;
        return c;
    }

    private void cleanup() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}