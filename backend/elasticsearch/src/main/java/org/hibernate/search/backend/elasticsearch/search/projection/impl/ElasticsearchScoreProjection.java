/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.search.projection.impl;

import org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor;
import org.hibernate.search.backend.elasticsearch.search.common.impl.ElasticsearchSearchIndexScope;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.projection.spi.ScoreProjectionBuilder;

import com.google.gson.JsonObject;

class ElasticsearchScoreProjection extends AbstractElasticsearchProjection<Float>
		implements ElasticsearchSearchProjection.Extractor<Float, Float> {

	private static final JsonAccessor<Boolean> TRACK_SCORES_ACCESSOR = JsonAccessor.root().property( "track_scores" )
			.asBoolean();

	private ElasticsearchScoreProjection(ElasticsearchSearchIndexScope<?> scope) {
		super( scope );
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public Extractor<?, Float> request(JsonObject requestBody, ProjectionRequestContext context) {
		TRACK_SCORES_ACCESSOR.set( requestBody, true );
		return this;
	}

	@Override
	public Float extract(ProjectionHitMapper<?, ?> projectionHitMapper, JsonObject hit,
			JsonObject source, ProjectionExtractContext context) {
		return hit.get( "_score" ).getAsFloat();
	}

	@Override
	public Float transform(LoadingResult<?, ?> loadingResult, Float extractedData,
			ProjectionTransformContext context) {
		return extractedData;
	}

	static class Builder extends AbstractElasticsearchProjection.AbstractBuilder<Float>
			implements ScoreProjectionBuilder {

		private final ElasticsearchScoreProjection projection;

		Builder(ElasticsearchSearchIndexScope<?> scope) {
			super( scope );
			this.projection = new ElasticsearchScoreProjection( scope );
		}

		@Override
		public SearchProjection<Float> build() {
			return projection;
		}
	}
}
