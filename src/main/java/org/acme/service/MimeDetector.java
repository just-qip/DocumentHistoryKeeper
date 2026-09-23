package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Определяет MIME-тип по расширению файла и валидирует его по белому списку.
 */
@ApplicationScoped
public class MimeDetector {

    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private static final Set<String> ALLOWED_MIME = Set.copyOf(EXT_TO_MIME.values());

    /**
     * Определяет MIME-тип по имени файла.
     *
     * @param name имя файла (может быть {@code null})
     * @return MIME-тип, если расширение известно
     */
    public Optional<String> fromFilename(String name) {
        if (name == null) {
            return Optional.empty();
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(EXT_TO_MIME.get(name.substring(dot + 1).toLowerCase()));
    }

    /**
     * Проверяет, что MIME входит в белый список.
     *
     * @param mime MIME-тип
     * @return {@code true}, если тип разрешён
     */
    public boolean isAllowed(String mime) {
        return mime != null && ALLOWED_MIME.contains(mime);
    }
}