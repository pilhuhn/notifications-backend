package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.events.FromCamelHistoryFiller;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class NotificationResources {

    private static final Logger log = Logger.getLogger(NotificationResources.class.getName());

    @Inject
    Mutiny.Session session;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        return Uni.createFrom().item(history)
                .onItem().transform(this::addEndpointReference)
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(history);
    }

    public Multi<NotificationHistory> getNotificationHistory(String tenant, UUID endpoint) {
        String query = "SELECT NEW NotificationHistory(nh.id, nh.accountId, nh.invocationTime, nh.invocationResult, nh.eventId, nh.endpoint, nh.created) " +
                "FROM NotificationHistory nh WHERE nh.accountId = :accountId AND nh.endpoint.id = :endpointId";
        return session.createQuery(query, NotificationHistory.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, Query limiter, UUID endpoint, UUID historyId) {
        String query = "SELECT details FROM NotificationHistory WHERE accountId = :accountId AND endpoint.id = :endpointId AND id = :historyId";
        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<Map> mutinyQuery = session.createQuery(query, Map.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointId", endpoint)
                .setParameter("historyId", historyId);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return mutinyQuery.getSingleResultOrNull()
                .onItem().ifNotNull().transform(JsonObject::new);
    }

    public Uni<Void> updateHistoryItem(Map<String, String> jo) {

        String historyId = jo.get("historyId");
        String outcome = jo.get("outcome");
        String details = jo.get("details");

   /*     String query = "SELECT n FROM NotificationHistory n  WHERE n.id = :id";

        Mutiny.Query<Object> q = session.createQuery(query)
                .setParameter("id",UUID.fromString(historyId));

        q.getSingleResultOrNull()
                .onItem().ifNotNull().invoke(h -> System.out.println("Hi2 " + h))
                .onItem().ifNull().failWith(new NotFoundException(historyId))
        ;*/


        Uni<NotificationHistory> history = session.find(NotificationHistory.class, historyId);
        return history.onItem().invoke(h -> System.out.println("Hi " + h))
                .replaceWith(Uni.createFrom().voidItem());

/*
                .transform(i -> {
                    i.setDetails(details);
                    i.setInvocationResult(result);
                    return Uni.createFrom().item(true);
                })
                .call(() -> session.flush())
                .replaceWith(Uni.createFrom().item(true));
*/

    }

    /**
     * Adds to the given {@link NotificationHistory} a reference to a persistent {@link Endpoint} without actually
     * loading its state from the database. The notification history will remain unchanged if it does not contain
     * a non-null endpoint identifier.
     *
     * @param history the notification history that will hold the endpoint reference
     * @return the same notification history instance, possibly modified if an endpoint reference was added
     */
    private NotificationHistory addEndpointReference(NotificationHistory history) {
        if (history.getEndpointId() != null && history.getEndpoint() == null) {
            history.setEndpoint(session.getReference(Endpoint.class, history.getEndpointId()));
        }
        return history;
    }
}
