/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.model.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.search.engine.mapper.model.spi.MappableTypeModel;
import org.hibernate.search.mapper.pojo.model.hcann.spi.PojoCommonsAnnotationsHelper;
import org.hibernate.search.mapper.pojo.model.spi.GenericContextAwarePojoGenericTypeModel.RawTypeDeclaringContext;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeIdentifier;

public class HibernateOrmClassRawTypeModel<T> extends AbstractHibernateOrmRawTypeModel<T> {

	private final HibernateOrmBasicClassTypeMetadata ormTypeMetadata;

	private final Map<String, HibernateOrmClassPropertyModel<?>> propertyModelCache = new HashMap<>();

	private Map<String, XProperty> declaredFieldAccessXPropertiesByName;
	private Map<String, XProperty> declaredMethodAccessXPropertiesByName;

	HibernateOrmClassRawTypeModel(HibernateOrmBootstrapIntrospector introspector,
			PojoRawTypeIdentifier<T> typeIdentifier,
			HibernateOrmBasicClassTypeMetadata ormTypeMetadata, RawTypeDeclaringContext<T> rawTypeDeclaringContext) {
		super( introspector, typeIdentifier, rawTypeDeclaringContext );
		this.ormTypeMetadata = ormTypeMetadata;
	}

	@Override
	public boolean isAbstract() {
		return xClass.isAbstract();
	}

	@Override
	public boolean isSubTypeOf(MappableTypeModel superTypeCandidate) {
		return superTypeCandidate instanceof HibernateOrmClassRawTypeModel
				&& ( (HibernateOrmClassRawTypeModel<?>) superTypeCandidate ).xClass.isAssignableFrom( xClass );
	}

	@Override
	public Stream<HibernateOrmClassRawTypeModel<? super T>> getAscendingSuperTypes() {
		return introspector.getAscendingSuperTypes( xClass );
	}

	@Override
	public Stream<HibernateOrmClassRawTypeModel<? super T>> getDescendingSuperTypes() {
		return introspector.getDescendingSuperTypes( xClass );
	}

	@Override
	public Stream<Annotation> getAnnotations() {
		return introspector.getAnnotations( xClass );
	}

	@Override
	Stream<String> getDeclaredPropertyNames() {
		return Stream.concat(
				getDeclaredFieldAccessXPropertiesByName().keySet().stream(),
				getDeclaredMethodAccessXPropertiesByName().keySet().stream()
		)
				.distinct();
	}

	@Override
	HibernateOrmClassPropertyModel<?> getPropertyOrNull(String propertyName) {
		return propertyModelCache.computeIfAbsent( propertyName, this::createPropertyModel );
	}

	private Map<String, XProperty> getDeclaredFieldAccessXPropertiesByName() {
		if ( declaredFieldAccessXPropertiesByName == null ) {
			declaredFieldAccessXPropertiesByName =
					introspector.getDeclaredFieldAccessXPropertiesByName( xClass );
		}
		return declaredFieldAccessXPropertiesByName;
	}

	private Map<String, XProperty> getDeclaredMethodAccessXPropertiesByName() {
		if ( declaredMethodAccessXPropertiesByName == null ) {
			declaredMethodAccessXPropertiesByName =
					introspector.getDeclaredMethodAccessXPropertiesByName( xClass );
		}
		return declaredMethodAccessXPropertiesByName;
	}

	private HibernateOrmClassPropertyModel<?> createPropertyModel(String propertyName) {
		List<XProperty> declaredXProperties = new ArrayList<>( 2 );
		// Add the method first on purpose: the first XProperty may be used as a default to create the value accessor handle
		XProperty methodAccessXProperty = getDeclaredMethodAccessXPropertiesByName().get( propertyName );
		if ( methodAccessXProperty != null ) {
			declaredXProperties.add( methodAccessXProperty );
		}
		XProperty fieldAccessXProperty = getDeclaredFieldAccessXPropertiesByName().get( propertyName );
		if ( fieldAccessXProperty != null ) {
			declaredXProperties.add( fieldAccessXProperty );
		}

		HibernateOrmBasicClassPropertyMetadata ormPropertyMetadata;
		if ( ormTypeMetadata == null ) {
			// There isn't any Hibernate ORM metadata for this type
			ormPropertyMetadata = null;
		}
		else {
			ormPropertyMetadata = ormTypeMetadata.getClassPropertyMetadataOrNull( propertyName );
		}

		Member member = findPropertyMember(
				propertyName, methodAccessXProperty, fieldAccessXProperty, ormPropertyMetadata
		);

		if ( member == null ) {
			return null;
		}

		return new HibernateOrmClassPropertyModel<>(
				introspector, this, propertyName,
				declaredXProperties, ormPropertyMetadata, member
		);
	}

	private Member findPropertyMember(String propertyName,
			XProperty methodAccessXProperty, XProperty fieldAccessXProperty,
			HibernateOrmBasicClassPropertyMetadata propertyMetadataFromHibernateOrmMetamodel) {
		if ( propertyMetadataFromHibernateOrmMetamodel != null ) {
			Member memberFromHibernateOrmMetamodel = propertyMetadataFromHibernateOrmMetamodel.getMember();
			/*
			 * Hibernate ORM has metadata for this property,
			 * which means this property is persisted.
			 *
			 * Hibernate ORM might return us the member as declared in a supertype,
			 * in which case the type of that member will not be up-to-date.
			 * Thus we try to get the overridden member declared in the current type,
			 * and failing that we look for the member in supertypes.
			 *
			 * We still try to comply with JPA's configured access type,
			 * which explains the two if/else branches below.
			 */
			if ( memberFromHibernateOrmMetamodel instanceof Method ) {
				return methodAccessXProperty == null ? memberFromHibernateOrmMetamodel : PojoCommonsAnnotationsHelper.getUnderlyingMember( methodAccessXProperty );
			}
			else if ( memberFromHibernateOrmMetamodel instanceof Field ) {
				return fieldAccessXProperty == null ? memberFromHibernateOrmMetamodel : PojoCommonsAnnotationsHelper.getUnderlyingMember( fieldAccessXProperty );
			}
			else {
				/*
				 * We don't have a declared XProperty for this member in the current type.
				 * Try to find the member used to access the same property in the closest supertype.
				 */
				return getPropertyMemberFromParentTypes( propertyName );
			}
		}
		else {
			/*
			 * Hibernate ORM doesn't have any metadata for this property,
			 * which means this property is transient.
			 * We don't need to worry about JPA's access type.
			 */
			if ( methodAccessXProperty != null ) {
				// We managed to find a declared, method-access XProperty on the current type. Use it.
				return PojoCommonsAnnotationsHelper.getUnderlyingMember( methodAccessXProperty );
			}
			else if ( fieldAccessXProperty != null ) {
				// We managed to find a declared, field-access XProperty on the current type. Use it.
				return PojoCommonsAnnotationsHelper.getUnderlyingMember( fieldAccessXProperty );
			}
			else {
				/*
				 * We did not manage to find a declared XProperty on the current type.
				 * Either the property is declared in a supertype, or it does not exist.
				 * Try to find the member used to access the same property in the closest supertype.
				 */
				return getPropertyMemberFromParentTypes( propertyName );
			}
		}
	}

	private Member getPropertyMemberFromParentTypes(String propertyName) {
		// TODO HSEARCH-3056 remove lambdas if possible
		return getAscendingSuperTypes()
				.skip( 1 ) // Ignore self
				.map( type -> type.getPropertyOrNull( propertyName ) )
				.filter( Objects::nonNull )
				.findFirst()
				.map( HibernateOrmClassPropertyModel::getMember )
				.orElse( null );
	}

}