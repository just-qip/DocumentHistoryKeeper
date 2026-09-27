package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.AccountDto;
import org.acme.entity.Account;
import org.acme.entity.Session;
import org.acme.mapper.DtoMapper;
import org.acme.security.CurrentAccount;
import org.acme.service.AvatarCacheService;
import org.acme.service.SrpChallengeStore;
import org.acme.service.SrpService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Профиль текущего пользователя: информация, аватар, смена пароля.
 */
@Path("/api/profile")
public class ProfileResource {

    private static final HexFormat HEX = HexFormat.of();
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_MIME = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    @Inject CurrentAccount current;
    @Inject SrpService srp;
    @Inject SrpChallengeStore challenges;
    @Inject AvatarCacheService avatarCache;

    /**
     * @return профиль текущего пользователя
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AccountDto me() {
        return DtoMapper.toDto(current.get());
    }

    /**
     * @param displayName новое отображаемое имя
     */
    public record UpdateProfileRequest(String displayName) {
    }

    /**
     * Обновляет отображаемое имя.
     *
     * @param req тело запроса
     * @return обновлённый DTO
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public AccountDto update(UpdateProfileRequest req) {
        String name = req.displayName() == null ? null : req.displayName().trim();
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("displayName required");
        }
        if (name.length() > 255) {
            throw new BadRequestException("displayName too long");
        }
        Account a = current.get();
        a.displayName = name;
        return DtoMapper.toDto(a);
    }

    /**
     * Загружает или заменяет аватар. Кэш инвалидируется.
     *
     * @param file PNG / JPEG / WebP / GIF не больше 2 МБ
     * @return обновлённый DTO
     * @throws IOException если не удалось прочитать файл
     */
    @POST
    @Path("/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public AccountDto uploadAvatar(@RestForm("file") FileUpload file) throws IOException {
        if (file == null) {
            throw new BadRequestException("file required");
        }
        byte[] content = Files.readAllBytes(file.uploadedFile());
        if (content.length == 0) {
            throw new BadRequestException("empty file");
        }
        if (content.length > MAX_AVATAR_BYTES) {
            throw new BadRequestException("avatar too large (max 2 MB)");
        }
        String mime = file.contentType();
        if (mime == null) {
            throw new BadRequestException("missing content type");
        }
        mime = mime.toLowerCase();
        if (!ALLOWED_AVATAR_MIME.contains(mime)) {
            throw new BadRequestException("unsupported avatar format");
        }

        Account a = current.get();
        a.avatar = content;
        a.avatarMime = mime;
        a.avatarUpdatedAt = Instant.now();

        // Сброс кэша: следующий GET /avatar перечитает из БД
        avatarCache.invalidate(a.id);

        return DtoMapper.toDto(a);
    }

    /**
     * Удаляет аватар и сбрасывает кэш.
     *
     * @return обновлённый DTO
     */
    @DELETE
    @Path("/avatar")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public AccountDto deleteAvatar() {
        Account a = current.get();
        a.avatar = null;
        a.avatarMime = null;
        a.avatarUpdatedAt = null;
        avatarCache.invalidate(a.id);
        return DtoMapper.toDto(a);
    }

    /**
     * Отдаёт байты аватара из кэша. На промахе — читает из БД
     * через {@link AvatarCacheService#get(UUID)}.
     *
     * @return бинарный ответ
     */
    @GET
    @Path("/avatar")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAvatar() {
        UUID id = current.id();
        if (id == null) {
            throw new ForbiddenException("no session");
        }

        Optional<AvatarCacheService.Avatar> av = avatarCache.get(id);

        if (av.isEmpty()) {
            throw new NotFoundException("no avatar");
        }

        byte[] bytes = av.get().bytes();
        return Response.ok(new ByteArrayInputStream(bytes))
                .header("Content-Type", av.get().mime())
                .header("Content-Length", bytes.length)
                .header("Cache-Control", "private, max-age=300")
                .build();
    }

    /**
     * @param challengeId    id SRP-челленджа
     * @param aHex           публичный эфемер клиента (hex)
     * @param m1Hex          доказательство старого пароля (hex)
     * @param newSaltHex     новая соль (hex)
     * @param newVerifierHex новый verifier (hex)
     */
    public record ChangePasswordRequest(
            UUID challengeId,
            String aHex,
            String m1Hex,
            String newSaltHex,
            String newVerifierHex) {
    }

    /**
     * Меняет пароль через SRP: сначала доказывается знание старого пароля
     * (M1), затем сохраняются новые salt + verifier. Все сессии отзываются.
     *
     * @param req тело запроса
     * @return 204
     */
    @POST
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response changePassword(ChangePasswordRequest req) {
        SrpChallengeStore.Challenge c = challenges.consume(req.challengeId());
        if (c == null) {
            throw new BadRequestException("challenge expired or unknown");
        }

        Account a = Account.findById(c.accountId());
        if (a == null || !a.isActive()) {
            throw new ForbiddenException("account not active");
        }

        BigInteger A = srp.fromBytes(HEX.parseHex(req.aHex()));
        if (A.mod(srp.N).signum() == 0) {
            throw new BadRequestException("invalid A");
        }
        BigInteger u = srp.computeU(A, c.B());
        BigInteger S = srp.serverSharedSecret(A, c.verifier(), u, c.b());
        byte[] K = srp.sessionKey(S);
        byte[] m1expected = srp.clientProof(A, c.B(), K, c.salt(), a.email);
        byte[] m1actual = HEX.parseHex(req.m1Hex());
        if (!srp.constantTimeEquals(m1expected, m1actual)) {
            throw new ForbiddenException("bad credentials");
        }

        byte[] newSalt = HEX.parseHex(req.newSaltHex());
        byte[] newVerifier = HEX.parseHex(req.newVerifierHex());
        if (newSalt.length < 16 || newSalt.length > 64) {
            throw new BadRequestException("invalid salt length");
        }
        if (newVerifier.length < 64 || newVerifier.length > 256) {
            throw new BadRequestException("invalid verifier length");
        }

        a.salt = newSalt;
        a.verifier = newVerifier;

        Session.delete("accountId", a.id);

        return Response.noContent().build();
    }
}