package io.vertx.eventx.queue.models;

public enum MessageState {
  CREATED, PROCESSING, SCHEDULED, EXPIRED, RETRY, RETRIES_EXHAUSTED, RECOVERY, PROCESSED, FATAL_FAILURE
}
