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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.WfsObjectFactory;

public final class WfsObjectFactoryImpl implements WfsObjectFactory {
  private List<Handler> handlerList = new ArrayList<>();

  public <T> void addMessageBodyHandler(
      Class<T> clazz, Function<List<Metacard>, T> factoryFunction) {
    Handler handler = new Handler();
    handler.handlerFunction = factoryFunction;
    handler.handlerClass = clazz;
    handlerList.add(handler);
  }

  public boolean canCreate(Class<?> targetClass) {
    return handlerList.stream().anyMatch(h -> h.handlerClass.isAssignableFrom(targetClass));
  }

  public <T> T create(Class<T> targetClass, List<Metacard> metacardList) {
    Optional<Handler> handlerOptional =
        handlerList.stream().filter(h -> h.handlerClass.isAssignableFrom(targetClass)).findFirst();

    if (!handlerOptional.isPresent()) {
      return null;
    }

    Function<List<Metacard>, T> handlerFunction = handlerOptional.get().handlerFunction;
    return handlerFunction.apply(metacardList);
  }

  private static class Handler<T> {
    Function<List<Metacard>, T> handlerFunction;
    Class<T> handlerClass;
  }
}
