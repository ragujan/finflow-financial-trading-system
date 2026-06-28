package com.finflow.order.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.events.OrderPlaced;
import com.finflow.common.mapper.OrderPlacedMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderPlacedPublisher {

    private static final Logger LOG = Logger.getLogger(OrderPlacedPublisher.class);

    @Inject
    OrderPlacedMapper orderPlacedMapper;

    @Inject
    ObjectMapper objectMapper;

    @Channel("orders-out")
    Emitter<String> ordersOut;

    public OrderPlaced publish(OrderPlaced event) {
        try {
            String payload = objectMapper.writeValueAsString(orderPlacedMapper.toDto(event));
            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(event.symbol())
                    .build();
            ordersOut.send(Message.of(payload, Metadata.of(metadata)));
            LOG.infof("Published OrderPlaced to orders topic: orderId=%s symbol=%s", event.orderId(), event.symbol());
            return event;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OrderPlaced event", e);
        }
    }
}
