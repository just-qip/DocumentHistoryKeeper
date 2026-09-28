package org.acme.service;

/**
 * Минимальный парсер User-Agent без внешних зависимостей.
 * Нужен только для группировки в аудите, точность не критична.
 */
public final class UserAgentParser {

    private UserAgentParser() {
        // utility class
    }

    /**
     * @param device  desktop / mobile / tablet / bot / unknown
     * @param os      Windows / macOS / Linux / Android / iOS или null
     * @param browser Chrome / Firefox / Safari / Edge / Opera или null
     */
    public record Parsed(String device, String os, String browser) {
    }

    /**
     * @param ua сырой User-Agent
     * @return разобранные поля
     */
    public static Parsed parse(String ua) {
        if (ua == null || ua.isBlank()) {
            return new Parsed("unknown", null, null);
        }
        String s = ua.toLowerCase();

        String device;
        if (s.contains("bot") || s.contains("crawler") || s.contains("spider")
                || s.contains("curl/") || s.contains("wget/")) {
            device = "bot";
        } else if (s.contains("ipad") || (s.contains("tablet") && !s.contains("mobile"))) {
            device = "tablet";
        } else if (s.contains("mobile") || s.contains("android") || s.contains("iphone")) {
            device = "mobile";
        } else {
            device = "desktop";
        }

        String os = null;
        if (s.contains("windows")) os = "Windows";
        else if (s.contains("mac os") || s.contains("macintosh")) os = "macOS";
        else if (s.contains("android")) os = "Android";
        else if (s.contains("iphone") || s.contains("ipad") || s.contains("ios")) os = "iOS";
        else if (s.contains("linux")) os = "Linux";

        String browser = null;
        if (s.contains("edg/")) browser = "Edge";
        else if (s.contains("opr/") || s.contains("opera")) browser = "Opera";
        else if (s.contains("chrome/") || s.contains("crios/")) browser = "Chrome";
        else if (s.contains("firefox/") || s.contains("fxios/")) browser = "Firefox";
        else if (s.contains("safari/")) browser = "Safari";

        return new Parsed(device, os, browser);
    }
}