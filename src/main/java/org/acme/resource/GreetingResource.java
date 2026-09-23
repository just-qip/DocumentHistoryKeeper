package org.acme.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Простой REST-ресурс для приветствия.
 */
@Path("/hello")
public class GreetingResource {

    /**
     * Возвращает приветственное сообщение.
     *
     * @return строка с приветствием
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }
}