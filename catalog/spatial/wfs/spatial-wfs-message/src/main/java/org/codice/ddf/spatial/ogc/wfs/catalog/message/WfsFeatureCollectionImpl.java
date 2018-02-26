/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.spatial.ogc.wfs.catalog.message;

import ddf.catalog.data.Metacard;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.WfsFeatureCollection;

public class WfsFeatureCollectionImpl implements WfsFeatureCollection {
  private List<Metacard> members;

  public WfsFeatureCollectionImpl(List<Metacard> metacardList) {
    this.members = Collections.unmodifiableList(metacardList);
  }

  public WfsFeatureCollectionImpl() {
    this.members = new ArrayList<Metacard>();
  }

  @Override
  public BigInteger getNumberReturned() {
    return null;
  }

  @Override
  public String getNumberMatched() {
    return null;
  }

  @Override
  public List<Metacard> getMembers() {
    return this.members;
  }

  @Override
  public Date getTimeStamp() {
    return null;
  }

  public void setMembers(List<Metacard> members) {
    this.members = Collections.unmodifiableList(members);
  }
}
