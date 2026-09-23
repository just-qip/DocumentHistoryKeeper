package org.acme.error;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.StaleObjectStateException;
import org.jboss.logging.Logger;

/**
 * Единый маппер исключений в JSON-ошибку.
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    /**
     * Преобразует исключение в HTTP-ответ с кодом и сообщением.
     *
     * @param throwable исключение
     * @return JSON-ответ с полями {@code code} и {@code message}
     */
    @Override
    public Response toResponse(Throwable throwable) {
        int status;
        String code;

        if (throwable instanceof NotFoundException) {
            status = Response.Status.NOT_FOUND.getStatusCode();
            code = "not_found";
        } else if (throwable instanceof BadRequestException) {
            status = Response.Status.BAD_REQUEST.getStatusCode();
            code = "bad_request";
        } else if (throwable instanceof NotAllowedException) {
            status = Response.Status.METHOD_NOT_ALLOWED.getStatusCode();
            code = "method_not_allowed";
        } else if (throwable instanceof OptimisticLockException
                || throwable instanceof StaleObjectStateException) {
            status = Response.Status.CONFLICT.getStatusCode();
            code = "conflict";
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            code = "internal_error";
            LOG.error("Unhandled error", throwable);
        }

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorBody(code, throwable.getMessage()))
                .build();
    }

    /**
     * Тело ошибки API.
     *
     * @param code    машинный код
     * @param message человекочитаемое сообщение
     */
    public record ErrorBody(String code, String message) {
    }
}