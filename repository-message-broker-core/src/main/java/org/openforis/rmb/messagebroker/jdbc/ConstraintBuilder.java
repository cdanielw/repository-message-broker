package org.openforis.rmb.messagebroker.jdbc;

import org.openforis.rmb.messagebroker.MessageConsumer;
import org.openforis.rmb.messagebroker.spi.Clock;
import org.openforis.rmb.messagebroker.spi.MessageProcessingFilter;
import org.openforis.rmb.messagebroker.spi.MessageProcessingStatus.State;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class ConstraintBuilder {
    private final Collection<MessageConsumer<?>> consumers;
    private final MessageProcessingFilter filter;
    private final Clock clock;
    private final StringBuilder whereClause = new StringBuilder();
    private int i = 0;

    public ConstraintBuilder(Collection<MessageConsumer<?>> consumers, MessageProcessingFilter filter, Clock clock) {
        this.consumers = consumers;
        this.filter = filter;
        this.clock = clock;
        consumers();
        states();
        publishedBefore();
        publishedAfter();
        lastUpdatedBefore();
        lastUpdatedAfter();
        messageIds();
    }

    public String whereClause() {
        return whereClause.toString();
    }

    public void updateStatement(PreparedStatement ps) throws SQLException {
        consumers(ps);
        states(ps);
        publishedBefore(ps);
        publishedAfter(ps);
        lastUpdatedBefore(ps);
        lastUpdatedAfter(ps);
        messageIds(ps);
    }

    private void consumers() {
        whereClause.append("\nconsumer_id in (")
                .append(questionMarks(consumers.size()))
                .append(")");
    }

    private void consumers(PreparedStatement ps) throws SQLException {
        for (MessageConsumer<?> consumer : consumers)
            ps.setString(nextIndex(), consumer.id);
    }

    private void states() {
        if (filter.states.isEmpty())
            return;
        whereClause.append("\nAND (");
        List<String> statesWithoutTimedOut = statesWithoutTimedOut();
        if (!statesWithoutTimedOut.isEmpty())
            whereClause.append("state in (").append(questionMarks(statesWithoutTimedOut.size())).append(')');
        if (includeTimedOut()) {
            if (!statesWithoutTimedOut.isEmpty())
                whereClause.append(" OR ");
            whereClause.append("times_out < ?");
        }
        whereClause.append(")");
    }

    private void states(PreparedStatement ps) throws SQLException {
        if (filter.states.isEmpty())
            return;
        for (String state : statesWithoutTimedOut())
            ps.setString(nextIndex(), state);
        if (includeTimedOut())
            ps.setTimestamp(nextIndex(), new Timestamp(clock.millis()));
    }

    private void publishedBefore() {
        if (filter.publishedBefore == null)
            return;
        whereClause.append("\nAND publication_time < ?");
    }

    private void publishedBefore(PreparedStatement ps) throws SQLException {
        if (filter.publishedBefore == null)
            return;
        ps.setTimestamp(nextIndex(), new Timestamp(filter.publishedBefore.getTime()));
    }

    private void publishedAfter() {
        if (filter.publishedAfter == null)
            return;
        whereClause.append("\nAND publication_time > ?");
    }

    private void publishedAfter(PreparedStatement ps) throws SQLException {
        if (filter.publishedAfter == null)
            return;
        ps.setTimestamp(nextIndex(), new Timestamp(filter.publishedAfter.getTime()));
    }

    private void lastUpdatedBefore() {
        if (filter.lastUpdatedBefore == null)
            return;
        whereClause.append("\nAND last_updated < ?");
    }


    private void lastUpdatedBefore(PreparedStatement ps) throws SQLException {
        if (filter.lastUpdatedBefore == null)
            return;
        ps.setTimestamp(nextIndex(), new Timestamp(filter.lastUpdatedBefore.getTime()));
    }

    private void lastUpdatedAfter() {
        if (filter.lastUpdatedAfter == null)
            return;
        whereClause.append("\nAND last_updated > ?");
    }

    private void lastUpdatedAfter(PreparedStatement ps) throws SQLException {
        if (filter.lastUpdatedAfter == null)
            return;
        ps.setTimestamp(nextIndex(), new Timestamp(filter.lastUpdatedAfter.getTime()));
    }

    private void messageIds() {
        if (filter.messageIds.isEmpty())
            return;
        whereClause.append("\nAND message_id in (").append(questionMarks(filter.messageIds.size())).append(')');

    }

    private void messageIds(PreparedStatement ps) throws SQLException {
        if (filter.messageIds.isEmpty())
            return;
        for (String messageId : filter.messageIds)
            ps.setString(nextIndex(), messageId);
    }

    private boolean includeTimedOut() {
        return filter.states.size() != statesWithoutTimedOut().size();
    }

    private List<String> statesWithoutTimedOut() {
        List<String> result = new ArrayList<String>();
        for (State state : filter.states)
            if (state != State.TIMED_OUT)
                result.add(state.name());
        return result;
    }

    private int nextIndex() {
        return ++i;
    }

    private String questionMarks(int count) {
        StringBuilder s = new StringBuilder(count * 3);
        s.append('?');
        for (int i = 1; i < count; i++)
            s.append(", ?");
        return s.toString();
    }
}
