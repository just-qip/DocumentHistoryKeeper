package org.acme.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Утилиты хеширования. Все методы — статические, класс не инстанцируется.
 */
public final class HashUtil {

    private static final String ALGORITHM = "SHA-256";

    private HashUtil() {
        // utility class
    }

    /**
     * Считает SHA-256 от конкатенации переданных кусков.
     * {@code null}-элементы игнорируются.
     *
     * @param chunks последовательность байтовых фрагментов
     * @return 32-байтовый хеш
     */
    public static byte[] sha256(byte[]... chunks) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    md.update(chunk);
                }
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Переводит байты в hex-строку нижнего регистра.
     *
     * @param bytes байты (может быть {@code null})
     * @return hex-строка или {@code null}
     */
    public static String hex(byte[] bytes) {
        return bytes == null ? null : HexFormat.of().formatHex(bytes);
    }
}