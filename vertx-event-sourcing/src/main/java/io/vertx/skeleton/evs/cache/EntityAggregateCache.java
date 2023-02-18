package io.vertx.skeleton.evs.cache;

import io.vertx.skeleton.evs.objects.EntityAggregateState;
import io.vertx.skeleton.evs.EntityAggregate;
import io.vertx.skeleton.evs.objects.EntityAggregateKey;
import io.smallrye.mutiny.Uni;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.shareddata.Shareable;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.shareddata.LocalMap;

import java.util.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toMap;

public class EntityAggregateCache<T extends EntityAggregate, V extends EntityAggregateState<T>> {

  private final Vertx vertx;
  private final Long aggregateTtlInMinutes;
  private static final Logger LOGGER = LoggerFactory.getLogger(EntityAggregateCache.class);
  private final Class<T> aggregateClass;
  private final String handlerAddress;
  private long refreshTaskTimerId;

  public EntityAggregateCache(
    Vertx vertx,
    Class<T> aggregateClass,
    String handlerAddress,
    Long aggregateTtlInMinutes
  ) {
    this.vertx = vertx;
    this.aggregateClass = aggregateClass;
    this.aggregateTtlInMinutes = aggregateTtlInMinutes;
    this.handlerAddress = handlerAddress;
  }

  public V get(EntityAggregateKey k) {
    final var holder = localEntityMap().get(k);
    if (holder != null && holder.hasNotExpired()) {
      return holder.value;
    } else {
      return null;
    }
  }

  public Uni<V> put(EntityAggregateKey k, V v) {
    long timestamp = System.nanoTime();
    long timerId = vertx.setTimer(aggregateTtlInMinutes, l -> removeIfExpired(k));
    Holder<V> previous = localEntityMap().put(k, new Holder<>(v, timerId, aggregateTtlInMinutes * 60000, timestamp));
    LOGGER.info("EntityAggregate added to handler cache -> " + k + " handler address -> " + handlerAddress);
    if (previous != null) {
      vertx.cancelTimer(previous.timerId);
    }
    return Uni.createFrom().item(v);
  }

  public Integer size() {
    return localEntityMap().size();
  }

  public List<Holder<V>> values() {
    return vertx.getDelegate().sharedData().<EntityAggregateKey, Holder<V>>getLocalMap(aggregateClass.getName())
      .values().stream()
      .toList();
  }

  private LocalMap<EntityAggregateKey, Holder<V>> localEntityMap() {
    return vertx.sharedData().<EntityAggregateKey, Holder<V>>getLocalMap(aggregateClass.getName());
  }

  private void removeIfExpired(EntityAggregateKey k) {
    final var value = localEntityMap().get(k);
    if (!value.hasNotExpired()) {
      LOGGER.info(k + " evicted form cache");
      remove(k);
    }
  }

  public V remove(EntityAggregateKey k) {
    final var previous = localEntityMap().remove(k);
    if (previous != null) {
      if (previous.expires()) {
        vertx.cancelTimer(previous.timerId);
      }
      return previous.value;
    } else {
      return null;
    }
  }

  private static class Holder<V> implements Shareable {
    final V value;
    final long timerId;
    final long ttl;
    final long timestamp;

    Holder(V value) {
      Objects.requireNonNull(value);
      this.value = value;
      timestamp = ttl = timerId = 0;
    }

    Holder(V value, long timerId, long ttl, long timestamp) {
      Objects.requireNonNull(value);
      if (ttl < 1) {
        throw new IllegalArgumentException("ttl must be positive: " + ttl);
      }
      this.value = value;
      this.timerId = timerId;
      this.ttl = ttl;
      this.timestamp = timestamp;
    }

    boolean expires() {
      return ttl > 0;
    }

    boolean hasNotExpired() {
      return !expires() || MILLISECONDS.convert(System.nanoTime() - timestamp, NANOSECONDS) < ttl;
    }

    public String toString() {
      return "Holder{" + "value=" + value + ", verticleId=" + timerId + ", ttl=" + ttl + ", timestamp=" + timestamp + '}';
    }
  }
}
