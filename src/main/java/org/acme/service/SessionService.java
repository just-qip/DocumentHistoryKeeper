package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.entity.Account;
import org.acme.entity.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Выдача и проверка сессионных токенов.
 *
 * <p>Наружу отдаётся 32-байтовый токен в hex. В БД лежит только его SHA-256.</p>
 */
@ApplicationScoped
public class SessionService {

    private static final Duration TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @param account аккаунт
     * @return свежий токен (hex)
     */
    @Transactional
    public String issue(Account account) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);

        Session s = new Session();
        s.id = UUID.randomUUID();
        s.accountId = account.id;
        s.tokenHash = sha256(token);
        s.createdAt = Instant.now();
        s.expiresAt = s.createdAt.plus(TTL);
        s.lastSeenAt = s.createdAt;
        s.persist();
        return token;
    }

    /**
     * @param token сырой токен
     * @return аккаунт или {@code null}
     */
    @Transactional
    public Account resolve(String token) {
        if (token == null || token.isBlank()) return null;
        byte[] hash = sha256(token);
        Session s = Session.find("tokenHash = ?1", hash).firstResult();
        if (s == null) return null;
        if (s.expiresAt.isBefore(Instant.now())) {
            s.delete();
            return null;
        }
        s.lastSeenAt = Instant.now();
        return Account.findById(s.accountId);
    }

    /**
     * @param token сырой токен
     */
    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        Session.delete("tokenHash", sha256(token));
    }

    /**
     * @param token сырой токен
     * @return SHA-256 от токена
     */
    private byte[] sha256(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}