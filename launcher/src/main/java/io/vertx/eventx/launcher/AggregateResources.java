package io.vertx.eventx.launcher;

import io.activej.inject.Injector;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Named;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.smallrye.mutiny.Uni;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.eventx.Aggregate;
import io.vertx.eventx.Projection;
import io.vertx.eventx.handlers.*;
import io.vertx.eventx.common.CustomClassLoader;
import io.vertx.eventx.config.ConfigurationDeployer;
import io.vertx.eventx.config.ConfigurationHandler;
import io.vertx.eventx.http.HealthCheck;
import io.vertx.eventx.infrastructure.PgInfrastructure;
import io.vertx.eventx.infrastructure.proxies.AggregateEventbusProxy;
import io.vertx.eventx.objects.ProjectionWrapper;
import io.vertx.eventx.infrastructure.proxies.AggregateHttpProxy;
import io.vertx.eventx.sql.Repository;
import io.vertx.eventx.sql.RepositoryHandler;
import io.vertx.eventx.infrastructure.pg.mappers.EntityProjectionHistoryMapper;
import io.vertx.eventx.infrastructure.pg.mappers.EventJournalMapper;
import io.vertx.eventx.infrastructure.pg.mappers.EventJournalOffsetMapper;
import io.vertx.eventx.task.TimerTaskDeployer;
import io.vertx.ext.healthchecks.Status;
import io.vertx.mutiny.config.ConfigRetriever;
import io.vertx.mutiny.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static io.vertx.eventx.infrastructure.bus.AggregateBus.HASH_RING_MAP;
import static io.vertx.eventx.infrastructure.bus.AggregateBus.createChannel;
import static io.vertx.eventx.launcher.EventxMain.MAIN_MODULES;

public class AggregateResources<T extends Aggregate> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AggregateResources.class);
  private final Vertx vertx;
  private final String deploymentID;
  private final ArrayList<Module> localModules;
  private final TimerTaskDeployer timerTaskDeployer;
  private final ConfigurationDeployer configurationDeployer;
  private ConfigRetriever deploymentConfiguration;
  private final Class<T> aggregateClass;
  private PgInfrastructure<T> infrastructure;

  public AggregateResources(
    Class<T> aggregateClass,
    Vertx vertx,
    String deploymentID
  ) {
    this.aggregateClass = aggregateClass;
    this.vertx = vertx;
    this.deploymentID = deploymentID;
    this.localModules = new ArrayList<>(MAIN_MODULES);
    injectHttpProxyInMainModule(aggregateClass);
    this.timerTaskDeployer = new TimerTaskDeployer();
    this.configurationDeployer = new ConfigurationDeployer();
  }


  public void deploy(final Promise<Void> startPromise) {
    injectHeardBeatTimerInLocalModule();
    injectProjectionsTimerLocalModule();
    injectHealthChecksInMainModule();
    final var moduleBuilder = ModuleBuilder.create().install(localModules);
    this.deploymentConfiguration = ConfigurationHandler.configure(
      vertx,
      aggregateClass.getSimpleName().toLowerCase(),
      newConfiguration -> {
        LOGGER.info("---------------------------------- Starting Event.x Aggregate " + aggregateClass.getSimpleName() + "-----------------------------------" + newConfiguration.encodePrettily());
        close()
          .flatMap(avoid -> {
              this.infrastructure = new PgInfrastructure<>(aggregateClass, newConfiguration, vertx);
              moduleBuilder.bind(PgInfrastructure.class).toInstance(infrastructure);
              moduleBuilder.bind(Vertx.class).toInstance(vertx);
              moduleBuilder.bind(JsonObject.class).toInstance(newConfiguration);
              moduleBuilder.bind(RepositoryHandler.class).toInstance(infrastructure.repositoryHandler());
              final var injector = Injector.of(moduleBuilder.build());
              return infrastructure.start().replaceWith(injector);
            }
          )
          .call(configurationDeployer::deploy)
          .invoke(timerTaskDeployer::deploy)
          .call(injector -> {
              final Supplier<Verticle> supplier = () -> new AggregateVerticle<>(aggregateClass, ModuleBuilder.create().install(localModules));
              return createChannel(vertx, aggregateClass, deploymentID)
                .flatMap(avoid -> vertx.deployVerticle(supplier, new DeploymentOptions()
                      .setConfig(newConfiguration)
                      .setInstances(CpuCoreSensor.availableProcessors() * 2)
                    )
                    .replaceWithVoid()
                );
            }
          )
          .subscribe()
          .with(
            aVoid -> {
              startPromise.complete();
              LOGGER.info("---------------------------------- Event.x started aggregate " + aggregateClass.getSimpleName() + " -----------------------------------");
            }
            , throwable -> {
              LOGGER.error("---------------------- Error deploying Event.x aggregate " + aggregateClass.getSimpleName() + " ---------------------------------------", throwable);
              startPromise.fail(throwable);
            }
          );
      }
    );
  }

  private void injectHealthChecksInMainModule() {
    MAIN_MODULES.add(
      new AbstractModule() {

        @Inject
        @Provides
        HealthCheck aggregateHealthCheck() {
          return new HealthCheck() {
            @Override
            public String name() {
              return aggregateClass.getSimpleName() + "-bus";
            }

            // implement health check on aggregate bus
            @Override
            public Uni<Status> checkHealth() {
              if (HASH_RING_MAP.isEmpty()) {
                return Uni.createFrom().item(Status.KO());
              }
              return Uni.createFrom().item(Status.OK());
            }
          };

        }

        @Inject
        @Provides
        @Named("aggregateHashRing")
        HealthCheck aggregateHealthCheckRing() {
          return new HealthCheck() {
            @Override
            public String name() {
              return aggregateClass.getSimpleName() + "-hash-ring";
            }

            // implement health check on aggregate bus

            @Override
            public Uni<Status> checkHealth() {
              return Uni.createFrom().item(Status.OK());
            }
          };

        }
      }
    );
  }

  private void injectHttpProxyInMainModule(Class<? extends Aggregate> aggregateClass) {
    MAIN_MODULES.add(
      new AbstractModule() {
        @Provides
        @Inject
        AggregateHttpProxy httpProxy(Vertx vertx) {
          return new AggregateHttpProxy(vertx, aggregateClass);
        }
      }
    );
  }

  private void injectProjectionsTimerLocalModule() {
    localModules.add(
      new AbstractModule() {
        @Provides
        @Inject
        AggregateProjectionPoller<T> projectionUpdateActor(
          final List<ProjectionWrapper<T>> projections,
          final JsonObject configuration,
          final Vertx vertx
        ) {
          final var rh = RepositoryHandler.leasePool(configuration, vertx, aggregateClass);
          return new AggregateProjectionPoller<>(
            projections,
            new AggregateEventbusProxy<>(vertx, aggregateClass),
            new Repository<>(EventJournalMapper.INSTANCE, rh),
            new Repository<>(EventJournalOffsetMapper.INSTANCE, rh),
            new Repository<>(EntityProjectionHistoryMapper.INSTANCE, rh)
          );
        }

        @Provides
        @Inject
        List<ProjectionWrapper<T>> eventJournal(Injector injector) {
          return CustomClassLoader.loadFromInjector(injector, Projection.class).stream()
            .filter(projection -> CustomClassLoader.getFirstGenericType(projection).isAssignableFrom(aggregateClass))
            .map(projection -> new ProjectionWrapper<T>(
              projection,
              aggregateClass
            ))
            .toList();
        }
      }
    );
  }

  private void injectHeardBeatTimerInLocalModule() {
    localModules.add(
      new AbstractModule() {
        @Inject
        @Provides
        AggregateHeartbeat<T> heartbeat(Vertx vertx) {
          return new AggregateHeartbeat<>(vertx, aggregateClass);
        }
      }
    );
  }

  public Uni<Void> close() {
    deploymentConfiguration.close();
    timerTaskDeployer.close();
    return configurationDeployer.close().flatMap(avoid -> infrastructure.stop());
  }

}