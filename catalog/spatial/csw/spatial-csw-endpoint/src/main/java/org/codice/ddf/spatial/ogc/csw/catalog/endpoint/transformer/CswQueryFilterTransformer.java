/*
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */

package org.codice.ddf.spatial.ogc.csw.catalog.endpoint.transformer;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.codice.ddf.spatial.ogc.csw.catalog.common.converter.DefaultCswRecordMap;
import org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings.CswRecordMap;
import org.codice.ddf.spatial.ogc.csw.catalog.endpoint.mappings.CswRecordMapperFilterVisitor;
import org.geotools.filter.FilterFactoryImpl;
import org.opengis.filter.Filter;
import org.xml.sax.helpers.NamespaceSupport;

import ddf.catalog.data.MetacardType;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.transform.QueryFilterTransformer;

public class CswQueryFilterTransformer implements QueryFilterTransformer {

    private CswRecordMapperFilterVisitor filterVisitor;

    public CswQueryFilterTransformer(MetacardType metacardType, List<MetacardType> metacardTypes) {
        filterVisitor = new CswRecordMapperFilterVisitor(new MetacardCswRecordMap(),
                metacardType,
                metacardTypes);
    }

    @Override
    public QueryRequest transform(QueryRequest queryRequest, Map<String, Serializable> properties) {
        Query query = queryRequest.getQuery();
        Filter filter = (Filter) query.accept(filterVisitor, new FilterFactoryImpl());
        Query transformedQuery = new QueryImpl(filter,
                query.getStartIndex(),
                query.getPageSize(),
                query.getSortBy(),
                query.requestsTotalResultsCount(),
                query.getTimeoutMillis());

        return new QueryRequestImpl(transformedQuery,
                queryRequest.isEnterprise(),
                queryRequest.getSourceIds(),
                queryRequest.getProperties());
    }

    public static class MetacardCswRecordMap implements CswRecordMap {

        @Override
        public String getProperty(String propertyName, NamespaceSupport context) {
            return DefaultCswRecordMap.getDefaultMetacardFieldForPrefixedString(propertyName,
                    context);
        }

        @Override
        public String getProperty(String propertyName) {
            return DefaultCswRecordMap.getDefaultMetacardFieldFor(propertyName);
        }
    }
}
