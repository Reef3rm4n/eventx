package io.vertx.eventx.queue.postgres.mappers;

import io.vertx.eventx.queue.postgres.models.MessageTransaction;
import io.vertx.eventx.queue.postgres.models.MessageTransactionID;
import io.vertx.eventx.queue.postgres.models.MessageTransactionQuery;
import io.vertx.eventx.sql.RecordMapper;
import io.vertx.eventx.sql.generator.filters.QueryBuilder;
import io.vertx.eventx.sql.models.QueryFilters;
import io.vertx.sqlclient.Row;

import java.util.Map;
import java.util.Set;


public class MessageTransactionMapper implements RecordMapper<MessageTransactionID, MessageTransaction, MessageTransactionQuery> {
  public static final MessageTransactionMapper INSTANCE = new MessageTransactionMapper();
  private static final String PROCESSOR = "processor";
  private static final String MESSAGE_CLASS = "message_class";
  private static final String MESSAGE_ID = "message_id";
  public static final String TASK_QUEUE_TX = "task_queue_tx";

  private MessageTransactionMapper() {
  }

  @Override
  public String table() {
    return TASK_QUEUE_TX;
  }

  @Override
  public Set<String> columns() {
    return Set.of(PROCESSOR, MESSAGE_ID, MESSAGE_CLASS);
  }


  @Override
  public Set<String> keyColumns() {
    return Set.of(MESSAGE_ID);
  }

  @Override
  public MessageTransaction rowMapper(Row row) {
    return new MessageTransaction(
      row.getString(MESSAGE_ID),
      row.getString(PROCESSOR),
      row.getString(MESSAGE_CLASS),
      baseRecord(row)
    );
  }

  @Override
  public void params(Map<String, Object> params, MessageTransaction actualRecord) {
    params.put(MESSAGE_ID, actualRecord.id());
    params.put(PROCESSOR, actualRecord.processorClass());
    params.put(MESSAGE_CLASS, actualRecord.messageClass());
  }

  @Override
  public void keyParams(Map<String, Object> params, MessageTransactionID key) {
    params.put(MESSAGE_ID, key.messageId());
  }

  @Override
  public void queryBuilder(MessageTransactionQuery query, QueryBuilder builder) {
    builder
      .iLike(
        new QueryFilters<>(String.class)
          .filterColumn(PROCESSOR)
          .filterParams(query.processors())
      )
      .iLike(
        new QueryFilters<>(String.class)
          .filterColumn(MESSAGE_ID)
          .filterParams(query.ids())
      );
  }


}
