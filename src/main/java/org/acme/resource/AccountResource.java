package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.AccountDto;
import org.acme.entity.Account;
import org.acme.mapper.DtoMapper;
import org.acme.service.AccountService;

import java.util.List;
import java.util.UUID;

/**
 * REST-ресурс управления аккаунтами.
 */
@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject
    AccountService accounts;

    /**
     * Тело запроса на создание аккаунта.
     *
     * @param tenantId    тенант
     * @param email       email
     * @param displayName отображаемое имя
     */
    public record CreateAccountRequest(UUID tenantId, String email, String displayName) {
    }

    /**
     * Создаёт аккаунт.
     *
     * @param request тело запроса
     * @return 201 и созданный аккаунт
     */
    @POST
    public Response create(CreateAccountRequest request) {
        Account account = accounts.create(request.tenantId(), request.email(), request.displayName());
        return Response.status(Response.Status.CREATED).entity(DtoMapper.toDto(account)).build();
    }

    /**
     * Возвращает аккаунт по id.
     *
     * @param id идентификатор
     * @return DTO аккаунта
     */
    @GET
    @Path("/{id}")
    public AccountDto get(@PathParam("id") UUID id) {
        return DtoMapper.toDto(accounts.get(id));
    }

    /**
     * Постраничный список аккаунтов тенанта.
     *
     * @param tenantId тенант
     * @param page     номер страницы
     * @param size     размер страницы
     * @return список DTO
     */
    @GET
    public List<AccountDto> list(@QueryParam("tenantId") UUID tenantId,
                                 @QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("50") int size) {
        return accounts.list(tenantId, page, size).stream().map(DtoMapper::toDto).toList();
    }
}