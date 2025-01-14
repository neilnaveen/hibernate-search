/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.mapper.pojo.testsupport.util.rule;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.hibernate.search.engine.environment.bean.spi.BeanProvider;
import org.hibernate.search.mapper.javabean.cfg.JavaBeanMapperSettings;
import org.hibernate.search.mapper.javabean.cfg.spi.JavaBeanMapperSpiSettings;
import org.hibernate.search.mapper.javabean.mapping.CloseableSearchMapping;
import org.hibernate.search.mapper.javabean.mapping.SearchMapping;
import org.hibernate.search.mapper.javabean.mapping.SearchMappingBuilder;
import org.hibernate.search.mapper.javabean.schema.management.SchemaManagementStrategyName;
import org.hibernate.search.util.common.impl.CollectionHelper;
import org.hibernate.search.util.impl.integrationtest.common.bean.ForbiddenBeanProvider;
import org.hibernate.search.util.impl.integrationtest.common.rule.BackendMock;
import org.hibernate.search.util.impl.integrationtest.common.rule.BackendSetupStrategy;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.BackendMappingHandle;
import org.hibernate.search.util.impl.integrationtest.common.rule.MappingSetupHelper;

public final class JavaBeanMappingSetupHelper
		extends MappingSetupHelper<JavaBeanMappingSetupHelper.SetupContext, SearchMappingBuilder, CloseableSearchMapping> {

	/**
	 * @param lookup A {@link MethodHandles.Lookup} with private access to the test method,
	 * to be passed to mapping builders created by {@link SetupContext#setup(Class[])} or {@link SetupContext#setup()}
	 * so that the javabean mapper will be able to inspect classes defined in the test methods.
	 * @param backendMock A backend mock.
	 */
	public static JavaBeanMappingSetupHelper withBackendMock(MethodHandles.Lookup lookup, BackendMock backendMock) {
		return new JavaBeanMappingSetupHelper( lookup, BackendSetupStrategy.withSingleBackendMock( backendMock ) );
	}

	public static JavaBeanMappingSetupHelper withBackendMocks(MethodHandles.Lookup lookup,
			BackendMock defaultBackendMock, Map<String, BackendMock> namedBackendMocks) {
		return new JavaBeanMappingSetupHelper(
				lookup,
				BackendSetupStrategy.withMultipleBackendMocks( defaultBackendMock, namedBackendMocks )
		);
	}

	private final MethodHandles.Lookup lookup;

	private JavaBeanMappingSetupHelper(MethodHandles.Lookup lookup, BackendSetupStrategy backendSetupStrategy) {
		super( backendSetupStrategy );
		this.lookup = lookup;
	}

	@Override
	protected SetupContext createSetupContext() {
		return new SetupContext();
	}

	@Override
	protected void close(CloseableSearchMapping toClose) {
		toClose.close();
	}

	public final class SetupContext
			extends MappingSetupHelper<SetupContext, SearchMappingBuilder, CloseableSearchMapping>.AbstractSetupContext {

		// Use a LinkedHashMap for deterministic iteration
		private final Map<String, Object> properties = new LinkedHashMap<>();

		// Disable the bean-manager-based bean provider by default,
		// so that we detect code that relies on beans from the bean manager
		// whereas it should rely on reflection or built-in beans.
		private BeanProvider beanManagerBeanProvider = new ForbiddenBeanProvider();

		SetupContext() {
			properties.put( JavaBeanMapperSettings.SCHEMA_MANAGEMENT_STRATEGY,
					SchemaManagementStrategyName.NONE );
			// Ensure overridden properties will be applied
			withConfiguration( builder -> properties.forEach( builder::property ) );
		}

		@Override
		public SetupContext withProperty(String key, Object value) {
			if ( value != null ) {
				properties.put( key, value );
			}
			else {
				properties.remove( key );
			}
			return thisAsC();
		}

		public SetupContext expectCustomBeans() {
			beanManagerBeanProvider = null;
			return thisAsC();
		}

		public SetupContext withAnnotatedEntityType(Class<?> annotatedEntityType, String entityName) {
			return withConfiguration( builder -> {
				builder.addEntityType( annotatedEntityType, entityName );
				builder.annotationMapping().add( annotatedEntityType );
			} );
		}

		public SetupContext withAnnotatedEntityTypes(Class<?> ... annotatedEntityTypes) {
			return withAnnotatedEntityTypes( CollectionHelper.asLinkedHashSet( annotatedEntityTypes ) );
		}

		public SetupContext withAnnotatedEntityTypes(Set<Class<?>> annotatedEntityTypes) {
			return withConfiguration( builder -> {
				builder.addEntityTypes( annotatedEntityTypes );
				builder.annotationMapping().add( annotatedEntityTypes );
			} );
		}

		public SetupContext withAnnotatedTypes(Class<?> ... annotatedTypes) {
			return withAnnotatedTypes( CollectionHelper.asLinkedHashSet( annotatedTypes ) );
		}

		public SetupContext withAnnotatedTypes(Set<Class<?>> annotatedTypes) {
			return withConfiguration( builder -> builder.annotationMapping().add( annotatedTypes ) );
		}

		public SearchMapping setup(Class<?> ... annotatedEntityTypes) {
			return withAnnotatedEntityTypes( annotatedEntityTypes ).setup();
		}

		@Override
		protected SearchMappingBuilder createBuilder() {
			return SearchMapping.builder( lookup )
					.property( JavaBeanMapperSpiSettings.BEAN_PROVIDER, beanManagerBeanProvider )
					.properties( properties );
		}

		@Override
		protected CloseableSearchMapping build(SearchMappingBuilder builder) {
			return builder.build();
		}

		@Override
		protected BackendMappingHandle toBackendMappingHandle(CloseableSearchMapping result) {
			return new JavaBeanMappingHandle();
		}

		@Override
		protected SetupContext thisAsC() {
			return this;
		}
	}
}
