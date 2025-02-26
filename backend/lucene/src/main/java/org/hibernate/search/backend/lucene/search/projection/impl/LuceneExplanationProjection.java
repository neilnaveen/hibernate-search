/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.search.projection.impl;

import org.hibernate.search.backend.lucene.lowlevel.collector.impl.ExplanationValues;
import org.hibernate.search.backend.lucene.lowlevel.collector.impl.Values;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexScope;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.projection.spi.SearchProjectionBuilder;

import org.apache.lucene.search.Explanation;

class LuceneExplanationProjection extends AbstractLuceneProjection<Explanation>
		implements LuceneSearchProjection.Extractor<Explanation, Explanation> {

	private LuceneExplanationProjection(LuceneSearchIndexScope<?> scope) {
		super( scope );
	}

	@Override
	public Extractor<?, Explanation> request(ProjectionRequestContext context) {
		return this;
	}

	@Override
	public Values<Explanation> values(ProjectionExtractContext context) {
		return new ExplanationValues( context.collectorExecutionContext() );
	}

	@Override
	public Explanation transform(LoadingResult<?, ?> loadingResult, Explanation extractedData,
			ProjectionTransformContext context) {
		return extractedData;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	public static class Builder extends AbstractLuceneProjection.AbstractBuilder<Explanation>
			implements SearchProjectionBuilder<Explanation> {

		public Builder(LuceneSearchIndexScope<?> scope) {
			super( scope );
		}

		@Override
		public SearchProjection<Explanation> build() {
			return new LuceneExplanationProjection( scope );
		}
	}
}
