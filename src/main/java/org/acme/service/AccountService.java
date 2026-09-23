package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.acme.entity.Account;

import java.util.List;
import java.util.UUID;

/**
 * Операции над аккаунтами.
 */
@ApplicationScoped
public class AccountService {

    /**
     * Создаёт новый аккаунт.
     *
     * @param tenantId    тенант
     * @param email       email
     * @param displayName отображаемое имя
     * @return сохранённый аккаунт
     * @throws BadRequestException если поля пусты или email уже занят
     */
    @Transactional
    public Account create(UUID tenantId, String email, String displayName) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("email is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new BadRequestException("displayName is required");
        }
        if (Account.find("tenantId = ?1 and email = ?2", tenantId, email).firstResult() != null) {
            throw new BadRequestException("Account with this email already exists");
        }
        Account account = new Account();
        account.tenantId = tenantId;
        account.email = email;
        account.displayName = displayName;
        account.persist();
        return account;
    }

    /**
     * Возвращает аккаунт или бросает 404.
     *
     * @param id идентификатор
     * @return аккаунт
     * @throws NotFoundException если аккаунт не найден
     */
    public Account get(UUID id) {
        Account account = Account.findById(id);
        if (account == null) {
            throw new NotFoundException("Account not found: " + id);
        }
        return account;
    }

    /**
     * Возвращает страницу аккаунтов тенанта.
     *
     * @param tenantId тенант
     * @param page     номер страницы (с нуля)
     * @param size     размер страницы
     * @return список аккаунтов
     */
    public List<Account> list(UUID tenantId, int page, int size) {
        return Account
                .<Account>find("tenantId = ?1 order by displayName", tenantId)
                .page(page, Math.min(size, 100))
                .list();
    }
}