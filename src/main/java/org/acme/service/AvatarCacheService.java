package org.acme.service;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.entity.Account;

import java.util.Optional;
import java.util.UUID;

/**
 * Кэш байтов аватара. Использует декларативные аннотации quarkus-cache.
 *
 * <p>Ключ — id аккаунта. Значение — {@link Optional} с байтами и MIME,
 * либо пустой Optional для случая «аватара нет».</p>
 */
@ApplicationScoped
public class AvatarCacheService {

    /** Готовый к отдаче аватар. */
    public record Avatar(byte[] bytes, String mime) {
    }

    /**
     * Загружает аватар из БД. Вызывается только на промахе кэша.
     * Результат (включая {@code Optional.empty()}) кэшируется.
     *
     * @param accountId аккаунт
     * @return Optional с Avatar или пустой
     */
    @CacheResult(cacheName = "avatars")
    public Optional<Avatar> get(UUID accountId) {
        Account a = Account.findById(accountId);
        if (a == null || a.avatar == null || a.avatarMime == null) {
            return Optional.empty();
        }
        return Optional.of(new Avatar(a.avatar, a.avatarMime));
    }

    /**
     * Сбрасывает запись по id. Вызывается при upload/delete аватара.
     *
     * <p>Метод публичный и вызывается из другого бина
     * ({@code ProfileResource}) — иначе аннотация не сработает
     * из-за self-invocation.</p>
     *
     * @param accountId аккаунт
     */
    @CacheInvalidate(cacheName = "avatars")
    public void invalidate(UUID accountId) {
        // Тело пустое: аннотация сама удалит запись по ключу.
    }
}