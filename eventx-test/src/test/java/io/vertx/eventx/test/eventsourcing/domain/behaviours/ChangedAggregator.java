package io.vertx.eventx.test.eventsourcing.domain.behaviours;


import io.vertx.eventx.Aggregator;
import io.vertx.eventx.test.eventsourcing.domain.FakeAggregate;
import io.vertx.eventx.test.eventsourcing.domain.events.DataChanged;

public class ChangedAggregator implements Aggregator<FakeAggregate, DataChanged> {

  @Override
  public FakeAggregate apply(FakeAggregate aggregateState, DataChanged event) {
    return aggregateState.replaceData(event.newData());
  }




}
