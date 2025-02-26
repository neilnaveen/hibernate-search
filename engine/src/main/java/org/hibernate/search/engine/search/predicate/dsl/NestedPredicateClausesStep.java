/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.search.predicate.dsl;

/**
 * The step in a nested predicate definition where clauses can be added.
 * <p>
 * Different types of clauses have different effects;
 * see {@link BooleanPredicateOptionsCollector}.
 *
 * @param <S> The "self" type (the actual exposed type of this step).
 */
public interface NestedPredicateClausesStep<S extends NestedPredicateClausesStep<?>>
		extends GenericBooleanPredicateClausesStep<S, NestedPredicateOptionsCollector<?>>,
				NestedPredicateOptionsCollector<NestedPredicateOptionsCollector<?>> {

	// TODO HSEARCH-3090 add tuning methods, like the "score_mode" in Elasticsearch (avg, min, ...)

}
