package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * SRP-6a (RFC 5054) с группой 1024-bit и SHA-256.
 *
 * <p>Сервер хранит только salt и verifier = g^x mod N.
 * Пароль в открытом виде никогда не приходит и не хранится.</p>
 */
@ApplicationScoped
public class SrpService {

    /** Простое число из RFC 5054, 1024 бита. */
    public static final BigInteger N = new BigInteger(
            "EEAF0AB9ADB38DD69C33F80AFA8FC5E86072618775FF3C0B9EA2314C9C256576" +
                    "D674DF7496EA81D3383B4813D692C6E0E0D5D8E250B98BE48E495C1D6089DAD1" +
                    "5DC7D7B46154D6B6CE8EF4AD69B15D4982559B297BCF1885C529F566660E57EC" +
                    "68EDBC3C05726CC02FD4CBF4976EAA9AFD5138FE8376435B9FC61D2FC0EB06E3", 16);

    /** Генератор. */
    public static final BigInteger G = BigInteger.valueOf(2);

    /** Длина N в байтах. */
    public static final int N_LEN = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @param n количество байт
     * @return случайные байты
     */
    public byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /**
     * @return случайный 256-битный скаляр (для a и b)
     */
    public BigInteger randomScalar() {
        return new BigInteger(256, RANDOM);
    }

    /**
     * @param parts байтовые куски
     * @return SHA-256 от конкатенации
     */
    public byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) {
                if (p != null) md.update(p);
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Big-endian фиксированной длины {@link #N_LEN}.
     *
     * @param x целое
     * @return 128 байт
     */
    public byte[] toBytes(BigInteger x) {
        byte[] raw = x.toByteArray();
        int start = (raw.length > 1 && raw[0] == 0) ? 1 : 0;
        int len = raw.length - start;
        if (len > N_LEN) {
            throw new IllegalStateException("integer too big for N_LEN");
        }
        byte[] out = new byte[N_LEN];
        System.arraycopy(raw, start, out, N_LEN - len, len);
        return out;
    }

    /**
     * @param b байты big-endian
     * @return положительное целое
     */
    public BigInteger fromBytes(byte[] b) {
        return new BigInteger(1, b);
    }

    /**
     * @param a первый массив
     * @param b второй массив
     * @return побайтовый XOR (длина = минимум из двух)
     */
    public byte[] xor(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    /**
     * @return k = H(N | PAD(g))
     */
    public BigInteger k() {
        return fromBytes(sha256(toBytes(N), toBytes(G))).mod(N);
    }

    /**
     * @param email    идентификатор (I)
     * @param password пароль (P)
     * @param salt     соль
     * @return x = H(salt | H(I : P))
     */
    public BigInteger computeX(String email, String password, byte[] salt) {
        byte[] inner = sha256((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return fromBytes(sha256(salt, inner));
    }

    /**
     * @param x значение x
     * @return verifier = g^x mod N
     */
    public BigInteger verifier(BigInteger x) {
        return G.modPow(x, N);
    }

    /**
     * @param A клиентский эфемерный
     * @param B серверный эфемерный
     * @return u = H(PAD(A) | PAD(B))
     */
    public BigInteger computeU(BigInteger A, BigInteger B) {
        return fromBytes(sha256(toBytes(A), toBytes(B)));
    }

    /**
     * @param a эфемерный скаляр
     * @return A = g^a mod N
     */
    public BigInteger ephemeral(BigInteger a) {
        return G.modPow(a, N);
    }

    /**
     * @param v verifier
     * @param b серверный эфемерный скаляр
     * @return B = (k*v + g^b) mod N
     */
    public BigInteger computeB(BigInteger v, BigInteger b) {
        return k().multiply(v).add(ephemeral(b)).mod(N);
    }

    /**
     * @param A клиентский эфемерный
     * @param v verifier
     * @param u u
     * @param b серверный эфемерный скаляр
     * @return S = (A * v^u)^b mod N
     */
    public BigInteger serverSharedSecret(BigInteger A, BigInteger v, BigInteger u, BigInteger b) {
        return A.multiply(v.modPow(u, N)).mod(N).modPow(b, N);
    }

    /**
     * @param N modulus
     * @param g generator
     * @return H(N) XOR H(PAD(g))
     */
    public byte[] hNxorHg() {
        return xor(sha256(toBytes(N)), sha256(toBytes(G)));
    }

    /**
     * @param email почта
     * @return H(I)
     */
    public byte[] hI(String email) {
        return sha256(email.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param S общий секрет
     * @return K = H(S)
     */
    public byte[] sessionKey(BigInteger S) {
        return sha256(toBytes(S));
    }

    /**
     * @param A клиентский эфемерный
     * @param B серверный эфемерный
     * @param K сессионный ключ
     * @param salt соль
     * @param email почта
     * @return M1 = H(H(N) XOR H(g) | H(I) | salt | A | B | K)
     */
    public byte[] clientProof(BigInteger A, BigInteger B, byte[] K, byte[] salt, String email) {
        return sha256(hNxorHg(), hI(email), salt, toBytes(A), toBytes(B), K);
    }

    /**
     * @param A клиентский эфемерный
     * @param M1 доказательство клиента
     * @param K сессионный ключ
     * @return M2 = H(A | M1 | K)
     */
    public byte[] serverProof(BigInteger A, byte[] M1, byte[] K) {
        return sha256(toBytes(A), M1, K);
    }

    /**
     * @param a первый массив
     * @param b второй массив
     * @return true, если массивы совпадают побайтово
     */
    public boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }
}