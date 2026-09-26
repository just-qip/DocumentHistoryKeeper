package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

/** REST-ресурс управления аккаунтами. */
@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject AccountService accounts;

    /**
     * @param email       email
     * @param displayName отображаемое имя
     */
    public record CreateAccountRequest(String email, String displayName) {
    }

    /**
     * Создаёт аккаунт.
     *
     * @param request тело запроса
     * @return 201 и созданный аккаунт
     */
    @POST
    @Transactional
    public Response create(CreateAccountRequest request) {
        Account account = accounts.create(request.email(), request.displayName());
        return Response.status(Response.Status.CREATED)
                .entity(DtoMapper.toDto(account)).build();
    }

    /**
     * @param id идентификатор
     * @return DTO аккаунта
     */
    @GET
    @Path("/{id}")
    public AccountDto get(@PathParam("id") UUID id) {
        return DtoMapper.toDto(accounts.get(id));
    }

    /**
     * Постраничный список аккаунтов.
     *
     * @param page номер страницы
     * @param size размер страницы
     * @return список DTO
     */
    @GET
    @Transactional
    public List<AccountDto> list(@QueryParam("page") @DefaultValue("0") int page,
                                 @QueryParam("size") @DefaultValue("500") int size) {
        List<Account> list = Account.<Account>find("order by displayName")
                .page(page, Math.min(Math.max(size, 1), 1000))
                .list();
        return list.stream().map(DtoMapper::toDto).toList();
    }
}