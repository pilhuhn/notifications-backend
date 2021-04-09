package com.redhat.cloud.notifications.db;

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

    /**
     * Update a stub history item with data we have received from the Camel sender
     * @param jo Map containing the returned data
     * @return Nothing
     *
     * @see com.redhat.cloud.notifications.events.FromCamelHistoryFiller for the source of data
     */
    public Uni<Void> updateHistoryItem(Map<String, Object> jo) {

        String historyId = (String) jo.get("historyId");
        String outcome = (String) jo.get("outcome");
        boolean result = "Success".equalsIgnoreCase(outcome);
        Map details = (Map) jo.get("details");
        Integer duration = (Integer) jo.get("duration");

        String updateQuery = "UPDATE NotificationHistory SET details = :details, invocationResult = :result, invocationTime= :invocationTime WHERE id = :id";
        return session.createQuery(updateQuery)
                .setParameter("details", details)
                .setParameter("result", result)
                .setParameter("id", UUID.fromString(historyId))
                .setParameter("invocationTime", (long) duration)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount  -> rowCount > 0)
                .replaceWith(Uni.createFrom().voidItem());
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
