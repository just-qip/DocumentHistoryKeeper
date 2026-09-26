package org.acme.security;

import jakarta.enterprise.context.RequestScoped;
import org.acme.entity.Account;

/** Аккаунт, аутентифицированный в текущем HTTP-запросе. */
@RequestScoped
public class CurrentAccount {

    private Account account;

    /**
     * @return аккаунт
     */
    public Account get() {
        return account;
    }

    /**
     * @param account аккаунт
     */
    public void set(Account account) {
        this.account = account;
    }
}