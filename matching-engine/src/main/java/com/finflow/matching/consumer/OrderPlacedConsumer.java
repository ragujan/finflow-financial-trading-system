package com.finflow.matching.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderPlacedConsumer {

    private static final Logger LOG = Logger.getLogger(OrderPlacedConsumer.class);

    @Incoming("orders-in")
    public void consume(String payload) {
        LOG.infof("OrderPlaced smoke received: payload=%s", payload);
    }
}
