package com.finflow.order.resource;

import com.finflow.common.domain.OrderSide;
import com.finflow.common.domain.OrderType;
import com.finflow.common.events.OrderPlaced;
import com.finflow.order.kafka.OrderPlacedPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/internal/smoke")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class OrderSmokeResource {

    @Inject
    OrderPlacedPublisher orderPlacedPublisher;

    @ConfigProperty(name = "EVENTHUBS_CONNECTION_STRING")
    Optional<String> eventHubsConnectionString;

    @POST
    @Path("/orders")
    public Response publishSmokeOrder() {
        if (eventHubsConnectionString.isEmpty()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of(
                            "error", "EVENTHUBS_CONNECTION_STRING is not set",
                            "hint", "Load env.azure.example into your shell before starting quarkus:dev"))
                    .build();
        }

        OrderPlaced event = new OrderPlaced(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("150.00"),
                new BigDecimal("10"),
                Instant.now());

        orderPlacedPublisher.publish(event);

        return Response.ok(Map.of(
                "status", "published",
                "orderId", event.orderId(),
                "symbol", event.symbol(),
                "topic", "orders")).build();
    }
}
