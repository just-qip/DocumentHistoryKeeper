package org.acme.dto;

import java.util.UUID;

/**
 * DTO для SRP6-хендшейка.
 */
public final class AuthDto {

    private AuthDto() {
    }

    /**
     * @param email email для поиска аккаунта
     */
    public record LookupRequest(String email) {
    }

    /**
     * Краткая информация об аккаунте — для формы входа.
     *
     * @param accountId   идентификатор
     * @param displayName отображаемое имя
     */
    public record AccountChoice(UUID accountId, String displayName) {
    }

    /**
     * @param email       email (он же I в SRP)
     * @param displayName отображаемое имя
     * @param saltHex     соль (hex)
     * @param verifierHex verifier (hex)
     */
    public record RegisterRequest(
            String email,
            String displayName,
            String saltHex,
            String verifierHex) {
    }

    /**
     * @param email email
     */
    public record ChallengeRequest(String email) {
    }

    /**
     * @param challengeId id челленджа
     * @param saltHex     соль (hex)
     * @param BHex        публичный эфемер сервера (hex)
     */
    public record ChallengeResponse(UUID challengeId, String saltHex, String BHex) {
    }

    /**
     * @param challengeId id челленджа
     * @param AHex        публичный эфемер клиента (hex)
     * @param M1Hex       доказательство клиента (hex)
     */
    public record VerifyRequest(UUID challengeId, String AHex, String M1Hex) {
    }

    /**
     * @param token   сессионный токен (hex)
     * @param M2Hex   доказательство сервера (hex)
     * @param account аккаунт
     */
    public record VerifyResponse(String token, String M2Hex, AccountDto account) {
    }
}