package io.vertx.eventx.objects;


import com.google.common.collect.EvictingQueue;
import io.vertx.eventx.Aggregate;

import io.vertx.core.shareddata.Shareable;

import java.util.List;

public class AggregateState<T extends Aggregate> implements Shareable {

  private final Class<T> aggregateClass;
  private T state = null;
  private final EvictingQueue<String> knownCommands = EvictingQueue.create(100);
  private Long snapshotOffset = null;
  private Long currentVersion = null;

  public AggregateState(Class<T> aggregateClass) {
    this.aggregateClass = aggregateClass;
  }

  public Long snapshotOffset() {
    return snapshotOffset;
  }

  public AggregateState<T> setSnapshotOffset(Long snapshotOffset) {
    this.snapshotOffset = snapshotOffset;
    return this;
  }

  public Class<T> aggregateClass() {
    return aggregateClass;
  }

  public Long currentVersion() {
    return currentVersion;
  }

  public AggregateState<T> setCurrentVersion(Long currentVersion) {
    this.currentVersion = currentVersion;
    return this;
  }

  public T state() {
    return state;
  }

  public AggregateState<T> setState(final T state) {
    this.state = state;
    return this;
  }

  public EvictingQueue<String> knownCommands() {
    return knownCommands;
  }

  public AggregateState<T> addKnownCommand(String commandId) {
    if (!knownCommands.contains(commandId)) {
      knownCommands.add(commandId);
    }
    return this;
  }
  public AggregateState<T> addKnownCommands(List<String> commandIds) {
    commandIds.stream()
      .filter(cmd -> !knownCommands.contains(cmd))
      .forEach(knownCommands::add);
    return this;
  }
}
