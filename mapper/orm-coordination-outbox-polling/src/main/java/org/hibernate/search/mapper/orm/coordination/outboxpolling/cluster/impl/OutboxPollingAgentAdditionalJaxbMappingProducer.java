/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;

import org.hibernate.boot.jaxb.Origin;
import org.hibernate.boot.jaxb.SourceType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmHibernateMapping;
import org.hibernate.boot.jaxb.internal.MappingBinder;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.model.source.internal.hbm.MappingDocument;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.logging.impl.Log;
import org.hibernate.search.util.common.annotation.impl.SuppressForbiddenApis;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import org.jboss.jandex.IndexView;

@SuppressWarnings("deprecation")
public class OutboxPollingAgentAdditionalJaxbMappingProducer
		implements org.hibernate.boot.spi.AdditionalJaxbMappingProducer {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	// WARNING: Always use this prefix for all tables added by Hibernate Search:
	// we guarantee that in the documentation.
	public static final String HSEARCH_PREFIX = "HSEARCH_";

	// Must not be longer than 20 characters, so that the generator does not exceed the 30 characters for Oracle11g
	private static final String TABLE_NAME = HSEARCH_PREFIX + "AGENT";

	private static final String CLASS_NAME = Agent.class.getName();

	// Setting both the JPA entity name and the native entity name to the FQCN so that:
	// 1. We don't pollute the namespace of JPA entity names with something like
	// "Agent" that could potentially conflict with user-defined entities.
	// 2. We can still use session methods (persist, ...) without passing the entity name,
	// because our override actually matches the default for the native entity name.
	public static final String ENTITY_NAME = CLASS_NAME;

	private static final String ENTITY_DEFINITION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<hibernate-mapping>\n" +
			"    <class name=\"" + CLASS_NAME + "\" entity-name=\"" + ENTITY_NAME + "\" table=\"" + TABLE_NAME + "\">\n" +
			"        <id name=\"id\">\n" +
			"            <generator class=\"org.hibernate.id.enhanced.SequenceStyleGenerator\">\n" +
			"                <param name=\"sequence_name\">" + TABLE_NAME + "_GENERATOR</param>\n" +
			"                <param name=\"table_name\">" + TABLE_NAME + "_GENERATOR</param>\n" +
			"                <param name=\"initial_value\">1</param>\n" +
			"                <param name=\"increment_size\">1</param>\n" +
			"            </generator>\n" +
			"        </id>\n" +
			"        <property name=\"type\" nullable=\"false\" />\n" +
			"        <property name=\"name\" nullable=\"false\" />\n" +
			"        <property name=\"expiration\" nullable=\"false\" />\n" +
			"        <property name=\"state\" nullable=\"false\" />\n" +
			"        <property name=\"totalShardCount\" nullable=\"true\" />\n" +
			"        <property name=\"assignedShardIndex\" nullable=\"true\" />\n" +
			// Reserved for future use
			"        <property name=\"payload\" nullable=\"true\" type=\"materialized_blob\" />\n" +
			"    </class>\n" +
			"</hibernate-mapping>\n";

	@Override
	@SuppressForbiddenApis(reason = "Strangely, this SPI involves the internal MappingBinder class,"
			+ " and there's nothing we can do about it")
	public Collection<MappingDocument> produceAdditionalMappings(final MetadataImplementor metadata,
			IndexView jandexIndex, final MappingBinder mappingBinder, final MetadataBuildingContext buildingContext) {
		log.applicationNodeGeneratedEntityMapping( ENTITY_DEFINITION );
		Origin origin = new Origin( SourceType.OTHER, "search" );

		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( ENTITY_DEFINITION.getBytes() );
		BufferedInputStream bufferedInputStream = new BufferedInputStream( byteArrayInputStream );
		Binding<?> binding = mappingBinder.bind( bufferedInputStream, origin );

		JaxbHbmHibernateMapping root = (JaxbHbmHibernateMapping) binding.getRoot();

		MappingDocument mappingDocument = new MappingDocument( root, origin, buildingContext );
		return Collections.singletonList( mappingDocument );
	}
}