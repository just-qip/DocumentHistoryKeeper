package org.acme.security;

import jakarta.enterprise.context.RequestScoped;
import org.acme.entity.Account;

import java.util.UUID;

/**
 * Аккаунт, аутентифицированный в текущем HTTP-запросе.
 *
 * <p>Хранит только идентификатор и всегда возвращает managed-сущность
 * через {@link Account#findById}. Это важно, потому что сущность,
 * полученная в {@code SessionAuthFilter} (в отдельной транзакции), уже
 * detached к моменту вызова ресурса — мутации на ней не сохраняются.</p>
 */
@RequestScoped
public class CurrentAccount {

    private UUID id;

    /**
     * @param account аккаунт из фильтра (используется только для id)
     */
    public void set(Account account) {
        this.id = account == null ? null : account.id;
    }

    /**
     * @return managed-сущность из текущей сессии Hibernate или {@code null}
     */
    public Account get() {
        if (id == null) return null;
        return Account.findById(id);
    }

    /**
     * @return id аккаунта или {@code null}
     */
    public UUID id() {
        return id;
    }
}