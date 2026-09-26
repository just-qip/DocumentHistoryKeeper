package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;

import java.util.List;
import java.util.UUID;

/** Сервис аккаунтов. */
@ApplicationScoped
public class AccountService {

    /**
     * @param id идентификатор
     * @return аккаунт
     * @throws NotFoundException если не найден
     */
    public Account get(UUID id) {
        Account a = Account.findById(id);
        if (a == null) throw new NotFoundException("Account not found");
        return a;
    }

    /**
     * @param email       email
     * @param displayName отображаемое имя
     * @return созданный аккаунт
     */
    public Account create(String email, String displayName) {
        Account a = new Account();
        a.email = email;
        a.displayName = displayName;
        a.persist();
        return a;
    }

    /**
     * @param page номер страницы
     * @param size размер страницы
     * @return список аккаунтов
     */
    public List<Account> list(int page, int size) {
        return Account.<Account>find("order by displayName")
                .page(page, Math.min(Math.max(size, 1), 1000))
                .list();
    }
}