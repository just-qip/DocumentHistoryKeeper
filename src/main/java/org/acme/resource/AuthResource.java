package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.AccountDto;
import org.acme.dto.AuthDto;
import org.acme.entity.Account;
import org.acme.mapper.DtoMapper;
import org.acme.service.SrpChallengeStore;
import org.acme.service.SrpService;
import org.acme.service.SessionService;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.UUID;

/**
 * SRP6-аутентификация (RFC 5054).
 *
 * <p>Пароль никогда не покидает браузер в открытом виде.
 * Сервер хранит только salt и verifier.</p>
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final HexFormat HEX = HexFormat.of();

    @Inject SrpService srp;
    @Inject SrpChallengeStore challenges;
    @Inject SessionService sessions;

    /**
     * Ищет активный аккаунт по email.
     *
     * @param req email
     * @return краткие сведения или {@code null}
     */
    @POST
    @Path("/lookup")
    public AuthDto.AccountChoice lookup(AuthDto.LookupRequest req) {
        if (req.email() == null || req.email().isBlank()) {
            throw new BadRequestException("email required");
        }
        Account a = Account.find("email = ?1 and status = ?2 and salt is not null",
                req.email().trim(), Account.Status.ACTIVE).firstResult();
        if (a == null) return null;
        return new AuthDto.AccountChoice(a.id, a.displayName);
    }

    /**
     * Регистрирует аккаунт с SRP6-кредами.
     *
     * @param req тело запроса
     * @return 201 и DTO аккаунта
     */
    @POST
    @Path("/register")
    @Transactional
    public Response register(AuthDto.RegisterRequest req) {
        if (req.email() == null || req.email().isBlank()) {
            throw new BadRequestException("email required");
        }
        if (req.saltHex() == null || req.verifierHex() == null) {
            throw new BadRequestException("salt and verifier required");
        }

        byte[] salt = HEX.parseHex(req.saltHex());
        byte[] verifier = HEX.parseHex(req.verifierHex());

        if (salt.length < 16 || salt.length > 64) {
            throw new BadRequestException("invalid salt length");
        }
        if (verifier.length < 64 || verifier.length > 256) {
            throw new BadRequestException("invalid verifier length");
        }

        String email = req.email().trim();
        Account existing = Account.find("email = ?1", email).firstResult();
        if (existing != null) {
            throw new BadRequestException("email already registered");
        }

        Account a = new Account();
        a.email = email;
        a.displayName = req.displayName() == null ? email : req.displayName().trim();
        a.salt = salt;
        a.verifier = verifier;
        a.persist();

        return Response.status(Response.Status.CREATED)
                .entity(DtoMapper.toDto(a)).build();
    }

    /**
     * Шаг 1 SRP: выдаёт соль и публичный эфемер B.
     *
     * @param req email
     * @return соль, B, id челленджа
     */
    @POST
    @Path("/challenge")
    public AuthDto.ChallengeResponse challenge(AuthDto.ChallengeRequest req) {
        if (req.email() == null) {
            throw new BadRequestException("email required");
        }
        Account a = Account.find("email = ?1", req.email().trim()).firstResult();
        if (a == null || a.salt == null || a.verifier == null) {
            throw new NotFoundException("Account not found or has no password");
        }

        BigInteger v = srp.fromBytes(a.verifier);
        BigInteger b = srp.randomScalar();
        BigInteger B = srp.computeB(v, b);

        UUID id = challenges.put(a.id, b, B, a.salt, v);

        return new AuthDto.ChallengeResponse(
                id, HEX.formatHex(a.salt), HEX.formatHex(srp.toBytes(B)));
    }

    /**
     * Шаг 2 SRP: проверяет доказательство клиента и выдаёт токен.
     *
     * @param req challengeId, A, M1
     * @return токен, M2, аккаунт
     */
    @POST
    @Path("/verify")
    @Transactional
    public AuthDto.VerifyResponse verify(AuthDto.VerifyRequest req) {
        SrpChallengeStore.Challenge c = challenges.consume(req.challengeId());
        if (c == null) {
            throw new BadRequestException("challenge expired or unknown");
        }

        Account a = Account.findById(c.accountId());
        if (a == null || !a.isActive()) {
            throw new ForbiddenException("account not active");
        }

        BigInteger A = srp.fromBytes(HEX.parseHex(req.AHex()));
        if (A.mod(srp.N).signum() == 0) {
            throw new BadRequestException("invalid A");
        }

        BigInteger u = srp.computeU(A, c.B());
        BigInteger S = srp.serverSharedSecret(A, c.verifier(), u, c.b());
        byte[] K = srp.sessionKey(S);

        byte[] M1client = HEX.parseHex(req.M1Hex());
        byte[] M1server = srp.clientProof(A, c.B(), K, c.salt(), a.email);

        if (!srp.constantTimeEquals(M1client, M1server)) {
            throw new ForbiddenException("bad credentials");
        }

        byte[] M2 = srp.serverProof(A, M1client, K);
        String token = sessions.issue(a);

        return new AuthDto.VerifyResponse(token, HEX.formatHex(M2), DtoMapper.toDto(a));
    }

    /**
     * @param token сессионный токен
     * @return текущий аккаунт
     */
    @GET
    @Path("/me")
    public AccountDto me(@HeaderParam("Authorization") String token) {
        Account a = sessions.resolve(stripBearer(token));
        if (a == null) throw new ForbiddenException("no session");
        return DtoMapper.toDto(a);
    }

    /**
     * @param token сессионный токен
     * @return 204
     */
    @POST
    @Path("/logout")
    public Response logout(@HeaderParam("Authorization") String token) {
        sessions.revoke(stripBearer(token));
        return Response.noContent().build();
    }

    private String stripBearer(String header) {
        if (header == null) return null;
        if (header.startsWith("Bearer ")) return header.substring(7).trim();
        return header.trim();
    }
}