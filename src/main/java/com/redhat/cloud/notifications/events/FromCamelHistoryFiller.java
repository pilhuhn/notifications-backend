package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.NotificationResources;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * We sent data via Camel. Now Camel informs us about the outcome,
 * which we need to put into the notifications history.
 */
@ApplicationScoped
public class FromCamelHistoryFiller {

    private static final Logger log = Logger.getLogger(FromCamelHistoryFiller.class.getName());

    @Inject
    NotificationResources notificationResources;

    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Incoming("fromCamel")
    // Can be modified to use Multi<Message<String>> input also for more concurrency
    public Uni<Void> processAsync(Message<String> input) {
         return Uni.createFrom().item(() -> input.getPayload())
                 .onItem().invoke(payload -> log.info(() -> "Processing return from camel: " + payload))
                 .stage(self -> self
                    .onItem().transform(this::decodeItem)
                 )
                 .stage(self -> self
                        .onItem()
                         .transform(payload -> notificationResources.updateHistoryItem(payload)
//                        .onFailure().invoke(t -> log.info(() -> "|  Fail: " + t)
                                 )
                 )
                .onItemOrFailure()
                 .transformToUni((unused, t) -> {
                    if (t != null) {
                        log.severe( "|  Fail : " + t);
                    } else {
                        log.info("| Success: " + unused);
                    }
                    return Uni.createFrom().voidItem();
                 });
    }

    private Map<String,String> decodeItem(String s) {

        Map<String,String> map = Json.decodeValue(s, Map.class);

        return map;

    }

}
