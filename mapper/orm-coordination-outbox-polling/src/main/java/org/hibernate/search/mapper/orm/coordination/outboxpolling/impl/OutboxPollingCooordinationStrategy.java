/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.coordination.outboxpolling.impl;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.hibernate.search.engine.cfg.ConfigurationPropertySource;
import org.hibernate.search.engine.cfg.spi.ConfigurationProperty;
import org.hibernate.search.engine.cfg.spi.OptionalConfigurationProperty;
import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.mapper.orm.coordination.common.spi.CoordinationConfigurationContext;
import org.hibernate.search.mapper.orm.coordination.common.spi.CooordinationStrategy;
import org.hibernate.search.mapper.orm.coordination.common.spi.CoordinationStrategyPreStopContext;
import org.hibernate.search.mapper.orm.coordination.common.spi.CoordinationStrategyStartContext;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cfg.HibernateOrmMapperOutboxPollingSettings;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cfg.impl.HibernateOrmMapperOutboxPollingImplSettings;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl.AgentRepositoryProvider;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl.OutboxPollingAgentAdditionalJaxbMappingProducer;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl.DefaultAgentRepository;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl.ShardAssignmentDescriptor;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl.OutboxPollingOutboxEventAdditionalJaxbMappingProducer;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl.OutboxPollingOutboxEventSendingPlan;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl.DefaultOutboxEventFinder;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl.OutboxPollingEventProcessor;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl.OutboxPollingEventProcessorClusterLink;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl.OutboxEventFinderProvider;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.logging.impl.Log;
import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

public class OutboxPollingCooordinationStrategy implements CooordinationStrategy {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private static final ConfigurationProperty<Boolean> SHARDS_STATIC =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.SHARDS_STATIC )
					.asBoolean()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_SHARDS_STATIC )
					.build();

	private static final OptionalConfigurationProperty<Integer> SHARDS_TOTAL_COUNT =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.SHARDS_TOTAL_COUNT )
					.asIntegerStrictlyPositive()
					.build();

	private static final OptionalConfigurationProperty<List<Integer>> SHARDS_ASSIGNED =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.SHARDS_ASSIGNED )
					.asIntegerPositiveOrZero()
					.multivalued()
					.build();

	private static final ConfigurationProperty<Boolean> PROCESSORS_INDEXING_ENABLED =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_ENABLED )
					.asBoolean()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_PROCESSORS_INDEXING_ENABLED )
					.build();

	private static final ConfigurationProperty<Integer> PROCESSORS_INDEXING_POLLING_INTERVAL =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_POLLING_INTERVAL )
					.asIntegerStrictlyPositive()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_PROCESSORS_INDEXING_POLLING_INTERVAL )
					.build();

	private static final ConfigurationProperty<Integer> PROCESSORS_INDEXING_PULSE_INTERVAL =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_PULSE_INTERVAL )
					.asIntegerStrictlyPositive()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_PROCESSORS_INDEXING_PULSE_INTERVAL )
					.build();

	private static final ConfigurationProperty<Integer> PROCESSORS_INDEXING_PULSE_EXPIRATION =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_PULSE_EXPIRATION )
					.asIntegerStrictlyPositive()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_PROCESSORS_INDEXING_PULSE_EXPIRATION )
					.build();

	private static final ConfigurationProperty<Integer> PROCESSORS_INDEXING_BATCH_SIZE =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_BATCH_SIZE )
					.asIntegerStrictlyPositive()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_PROCESSORS_INDEXING_BATCH_SIZE )
					.build();

	private static final OptionalConfigurationProperty<BeanReference<? extends AgentRepositoryProvider>> PROCESSORS_INDEXING_AGENT_REPOSITORY_PROVIDER =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingImplSettings.CoordinationRadicals.PROCESSORS_INDEXING_AGENT_REPOSITORY_PROVIDER )
					.asBeanReference( AgentRepositoryProvider.class )
					.build();

	private static final OptionalConfigurationProperty<BeanReference<? extends OutboxEventFinderProvider>> PROCESSORS_INDEXING_OUTBOX_EVENT_FINDER_PROVIDER =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingImplSettings.CoordinationRadicals.PROCESSORS_INDEXING_OUTBOX_EVENT_FINDER_PROVIDER )
					.asBeanReference( OutboxEventFinderProvider.class )
					.build();

	private static final OptionalConfigurationProperty<Integer> PROCESSORS_INDEXING_TRANSACTION_TIMEOUT =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_TRANSACTION_TIMEOUT )
					.asIntegerStrictlyPositive()
					.build();

	private static final ConfigurationProperty<Integer> PROCESSORS_INDEXING_RETRY_DELAY =
			ConfigurationProperty.forKey( HibernateOrmMapperOutboxPollingSettings.CoordinationRadicals.PROCESSORS_INDEXING_RETRY_DELAY )
					.asIntegerPositiveOrZero()
					.withDefault( HibernateOrmMapperOutboxPollingSettings.Defaults.COORDINATION_PROCESSORS_INDEXING_RETRY_DELAY )
					.build();

	public static final String PROCESSOR_NAME_PREFIX = "Outbox event processor";

	private BeanHolder<? extends OutboxEventFinderProvider> finderProviderHolder;
	private BeanHolder<? extends AgentRepositoryProvider> agentRepositoryProviderHolder;
	private ScheduledExecutorService scheduledExecutor;
	private List<OutboxPollingEventProcessor> indexingProcessors;

	@Override
	public void configure(CoordinationConfigurationContext context) {
		context.mappingProducer( new OutboxPollingOutboxEventAdditionalJaxbMappingProducer() );
		context.mappingProducer( new OutboxPollingAgentAdditionalJaxbMappingProducer() );
		context.sendIndexingEventsTo( ctx -> new OutboxPollingOutboxEventSendingPlan( ctx.session() ), true );
	}

	@Override
	public CompletableFuture<?> start(CoordinationStrategyStartContext context) {
		Optional<BeanHolder<? extends AgentRepositoryProvider>> agentRepositoryProviderHolderOptional =
				PROCESSORS_INDEXING_AGENT_REPOSITORY_PROVIDER.getAndMap(
						context.configurationPropertySource(), context.beanResolver()::resolve );
		if ( agentRepositoryProviderHolderOptional.isPresent() ) {
			agentRepositoryProviderHolder = agentRepositoryProviderHolderOptional.get();
			log.debugf(
					"Outbox processing will use custom agent repository provider '%s'.",
					agentRepositoryProviderHolder.get()
			);
		}
		else {
			agentRepositoryProviderHolder = BeanHolder.of( new DefaultAgentRepository.Provider() );
		}

		Optional<BeanHolder<? extends OutboxEventFinderProvider>> finderProviderHolderOptional =
				PROCESSORS_INDEXING_OUTBOX_EVENT_FINDER_PROVIDER.getAndMap(
						context.configurationPropertySource(), context.beanResolver()::resolve );
		if ( finderProviderHolderOptional.isPresent() ) {
			finderProviderHolder = finderProviderHolderOptional.get();
			log.debugf(
					"Outbox processing will use custom outbox event finder provider '%s'.",
					finderProviderHolder.get()
			);
		}
		else {
			finderProviderHolder = BeanHolder.of( new DefaultOutboxEventFinder.Provider() );
		}

		ConfigurationPropertySource configurationSource = context.configurationPropertySource();

		if ( PROCESSORS_INDEXING_ENABLED.get( configurationSource ) ) {
			initializeProcessors( context );
		}
		else {
			// IMPORTANT: in this case we don't even configure sharding, and that's on purpose.
			// Application developers may want a fixed number of processing nodes (static sharding)
			// with a varying number of non-processing nodes,
			// so we want to make those non-processing nodes easy to configure.
			log.indexingProcessorDisabled();
		}

		return CompletableFuture.completedFuture( null );
	}

	private void initializeProcessors(CoordinationStrategyStartContext context) {
		ConfigurationPropertySource configurationSource = context.configurationPropertySource();
		OutboxEventFinderProvider finderProvider = finderProviderHolder.get();

		// IMPORTANT: we only configure sharding here, if processors are enabled.
		// See the comment in the caller method.
		boolean shardsStatic = SHARDS_STATIC.get( configurationSource );
		List<ShardAssignmentDescriptor> shardAssignmentOrNulls;
		if ( shardsStatic ) {
			int totalShardCount = SHARDS_TOTAL_COUNT.getAndMapOrThrow(
					configurationSource,
					this::checkTotalShardCount,
					log::missingPropertyForStaticSharding
			);
			shardAssignmentOrNulls = SHARDS_ASSIGNED.getAndMapOrThrow(
					configurationSource,
					shardIndices -> toStaticShardAssignments( configurationSource, totalShardCount, shardIndices ),
					log::missingPropertyForStaticSharding
			);
		}
		else {
			shardAssignmentOrNulls = Collections.singletonList( null );
		}

		int pollingInterval = PROCESSORS_INDEXING_POLLING_INTERVAL.get( configurationSource );
		int batchSize = PROCESSORS_INDEXING_BATCH_SIZE.get( configurationSource );
		Integer transactionTimeout = PROCESSORS_INDEXING_TRANSACTION_TIMEOUT.get( configurationSource )
				.orElse( null );
		int retryAfter = PROCESSORS_INDEXING_RETRY_DELAY.get( configurationSource );

		Duration pollingIntervalAsDuration = Duration.ofMillis( pollingInterval );
		Duration pulseInterval = PROCESSORS_INDEXING_PULSE_INTERVAL.getAndTransform( configurationSource,
				v -> checkPulseInterval( Duration.ofMillis( v ), pollingIntervalAsDuration ) );
		Duration pulseExpiration = PROCESSORS_INDEXING_PULSE_EXPIRATION.getAndTransform( configurationSource,
				v -> checkPulseExpiration( Duration.ofMillis( v ), pulseInterval ) );

		scheduledExecutor = context.threadPoolProvider()
				.newScheduledExecutor( shardAssignmentOrNulls.size(), PROCESSOR_NAME_PREFIX );
		indexingProcessors = new ArrayList<>();
		for ( ShardAssignmentDescriptor shardAssignmentOrNull : shardAssignmentOrNulls ) {
			String agentName = PROCESSOR_NAME_PREFIX
					+ ( shardAssignmentOrNull == null ? "" : " - " + shardAssignmentOrNull.assignedShardIndex );
			OutboxPollingEventProcessorClusterLink clusterLink = new OutboxPollingEventProcessorClusterLink(
					agentName, context.mapping().failureHandler(), Clock.systemUTC(),
					finderProvider, pollingIntervalAsDuration, pulseInterval, pulseExpiration, shardAssignmentOrNull );

			OutboxPollingEventProcessor processor = new OutboxPollingEventProcessor(
					agentName, context.mapping(), scheduledExecutor, agentRepositoryProviderHolder.get(), clusterLink,
					pollingInterval, batchSize, transactionTimeout, retryAfter );
			indexingProcessors.add( processor );
		}
		for ( OutboxPollingEventProcessor indexingProcessor : indexingProcessors ) {
			indexingProcessor.start();
		}
	}

	private Integer checkTotalShardCount(Integer totalShardCount) {
		if ( totalShardCount <= 0 ) {
			throw log.invalidTotalShardCount();
		}
		return totalShardCount;
	}

	private List<ShardAssignmentDescriptor> toStaticShardAssignments(ConfigurationPropertySource configurationPropertySource,
			int totalShardCount, List<Integer> shardIndices) {
		// Remove duplicates
		Set<Integer> uniqueShardIndices = new HashSet<>( shardIndices );
		for ( Integer shardIndex : uniqueShardIndices ) {
			if ( !( 0 <= shardIndex && shardIndex < totalShardCount ) ) {
				throw log.invalidShardIndex( totalShardCount,
						SHARDS_TOTAL_COUNT.resolveOrRaw( configurationPropertySource ) );
			}
		}
		List<ShardAssignmentDescriptor> shardAssignment = new ArrayList<>();
		for ( Integer shardIndex : uniqueShardIndices ) {
			shardAssignment.add( new ShardAssignmentDescriptor( totalShardCount, shardIndex ) );
		}
		return shardAssignment;
	}

	private Duration checkPulseInterval(Duration pulseInterval, Duration pollingInterval) {
		if ( pulseInterval.compareTo( pollingInterval ) < 0 ) {
			throw log.invalidPollingIntervalAndPulseInterval( pollingInterval.toMillis() );
		}
		return pulseInterval;
	}

	private Duration checkPulseExpiration(Duration pulseExpiration, Duration pulseInterval) {
		Duration pulseIntervalTimes3 = pulseInterval.multipliedBy( 3 );
		if ( pulseExpiration.compareTo( pulseIntervalTimes3 ) < 0 ) {
			throw log.invalidPulseIntervalAndPulseExpiration( pulseIntervalTimes3.toMillis() );
		}
		return pulseExpiration;
	}

	@Override
	public CompletableFuture<?> completion() {
		if ( indexingProcessors == null ) {
			// Nothing to do
			return CompletableFuture.completedFuture( null );
		}
		CompletableFuture<?>[] futures = new CompletableFuture[indexingProcessors.size()];
		int i = 0;
		for ( OutboxPollingEventProcessor indexingProcessor : indexingProcessors ) {
			futures[i] = indexingProcessor.completion();
			i++;
		}
		return CompletableFuture.allOf( futures );
	}

	@Override
	public CompletableFuture<?> preStop(CoordinationStrategyPreStopContext context) {
		if ( indexingProcessors == null ) {
			// Nothing to do
			return CompletableFuture.completedFuture( null );
		}
		CompletableFuture<?>[] futures = new CompletableFuture[indexingProcessors.size()];
		int i = 0;
		for ( OutboxPollingEventProcessor indexingProcessor : indexingProcessors ) {
			futures[i] = indexingProcessor.preStop();
			i++;
		}
		return CompletableFuture.allOf( futures );
	}

	@Override
	public void stop() {
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.pushAll( OutboxPollingEventProcessor::stop, indexingProcessors );
			closer.push( ScheduledExecutorService::shutdownNow, scheduledExecutor );
			closer.push( BeanHolder::close, finderProviderHolder );
			closer.push( BeanHolder::close, agentRepositoryProviderHolder );
		}
	}
}