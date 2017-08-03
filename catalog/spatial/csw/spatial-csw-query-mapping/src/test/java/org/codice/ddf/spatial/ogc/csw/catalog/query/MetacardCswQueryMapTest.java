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

package org.codice.ddf.spatial.ogc.csw.catalog.query;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.query.impl.MetacardCswQueryMap;
import org.junit.Test;

import ddf.catalog.data.Metacard;

public class MetacardCswQueryMapTest {
    private static final CswQueryMap METACARD_CSW_QUERY_MAP = new MetacardCswQueryMap();

    private static final String METACARD_SCHEMA_NAMESPACE = "urn:catalog:metacard";

    @Test
    public void testSchema() {
        String schema = METACARD_CSW_QUERY_MAP.getSchema();
        assertThat(schema, equalTo(METACARD_SCHEMA_NAMESPACE));
    }

    @Test
    public void testMappedParameter() {
        String result = METACARD_CSW_QUERY_MAP.getMetacardField(CswConstants.CSW_TITLE);
        assertThat(result, equalTo(Metacard.TITLE));
    }

    @Test
    public void testUnmappedParameter() {
        String unmappedParameter = "not mapped";
        String result = METACARD_CSW_QUERY_MAP.getMetacardField(unmappedParameter);
        assertThat(result, equalTo(unmappedParameter));
    }
}
