package io.vertx.eventx.queue.misc;

import io.vertx.eventx.queue.postgres.models.MessageRecord;
import io.vertx.eventx.queue.postgres.models.MessageRecordID;
import io.vertx.eventx.queue.postgres.models.MessageRecordQuery;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.eventx.sql.Repository;

import java.util.List;

public class FileSystemFallBack {
  public static final String TASK_QUEUE_MESSAGES_FOLDER = "task-queue-messages";
  private final Repository<MessageRecordID, MessageRecord, MessageRecordQuery> messageQueue;

  public FileSystemFallBack(Repository<MessageRecordID, MessageRecord, MessageRecordQuery> messageQueue) {
    this.messageQueue = messageQueue;
  }

  public Uni<Void> offload() {
    return messageQueue.repositoryHandler().vertx().fileSystem().readDir(TASK_QUEUE_MESSAGES_FOLDER)
      .onItem().transformToMulti(messageIDs -> Multi.createFrom().iterable(messageIDs))
      .onItem().transformToUniAndMerge(messageID -> messageQueue.repositoryHandler().vertx().fileSystem().readFile(messageID)
        .flatMap(buffer -> {
            final var messageRecord = new JsonObject(buffer.getDelegate()).mapTo(MessageRecord.class);
            return messageQueue.transaction(
              sqlConnection -> messageQueue.insert(messageRecord)
                .flatMap(avoid -> messageQueue.repositoryHandler().vertx().fileSystem().delete(messageID))
            );
          }
        )
      ).collect().asList()
      .replaceWithVoid();
  }

  public Uni<Void> load(MessageRecord messageRecord) {
    return messageQueue.repositoryHandler().vertx().fileSystem().writeFile(
      messagePath(messageRecord),
      Buffer.newInstance(JsonObject.mapFrom(messageRecord).toBuffer())
    );
  }

  public Uni<Void> load(List<MessageRecord> messages) {
    return Multi.createFrom().iterable(messages)
      .onItem().transformToUniAndMerge(messageRecord -> messageQueue.repositoryHandler().vertx().fileSystem().writeFile(
          messagePath(messageRecord),
          Buffer.newInstance(JsonObject.mapFrom(messageRecord).toBuffer())
        )
      )
      .collect().last()
      .replaceWithVoid();
  }

  private String messagePath(MessageRecord messageRecord) {
    return TASK_QUEUE_MESSAGES_FOLDER + "/" + messageRecord.id() + ".json";
  }
}
