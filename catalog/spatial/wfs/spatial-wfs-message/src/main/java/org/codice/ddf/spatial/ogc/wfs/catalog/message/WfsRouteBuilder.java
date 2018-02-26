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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.codice.ddf.spatial.ogc.wfs.catalog.message.api.WfsFeatureCollection;

public final class WfsRouteBuilder extends RouteBuilder {
  protected static final String ENDPOINT_URL = "direct://wfsTransform";

  private static final String READ_FROM_URL = "direct://readFrom";

  private static final String IS_READABLE_URL = "direct://isReadable";

  private Function<Class<?>, Boolean> isReadableFunction =
      WfsFeatureCollection.class::isAssignableFrom;

  public void configure() {
    from(ENDPOINT_URL)
        .choice()
        .when()
        .simple("${body.method.name} == 'readFrom'")
        .to(READ_FROM_URL)
        .when()
        .simple("${body.method.name} == 'isReadable'")
        .to(IS_READABLE_URL)
        .end();

    from(READ_FROM_URL)
        .setHeader("type", simple("${body.getArgs()[0]}"))
        .setBody(simple("${body.getArgs()[5]}"))
        .streamCaching()
        .split(
            body().tokenizeXML("featureMember", "FeatureCollection"),
            new WfsMemberAggregationStrategy())
        .streaming()
        .bean("wfsTransformerProcessor", "apply")
        .end()
        .bean("wfsObjectFactory", "create(${header.type}, ${body})");

    from(IS_READABLE_URL)
        .setBody(simple("${body.getArgs()[0]}"))
        .bean("wfsObjectFactory", "canCreate");
  }

  public static class WfsMemberAggregationStrategy implements AggregationStrategy {
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
      Optional<Metacard> metacardOpt = newExchange.getIn().getBody(Optional.class);

      if (oldExchange == null) {
        final List<Metacard> metacardList = new ArrayList<>();
        metacardOpt.ifPresent(metacardList::add);
        newExchange.getIn().setBody(metacardList);
        return newExchange;
      }

      final List<Metacard> metacardList =
          Optional.ofNullable(oldExchange.getIn().getBody(List.class)).orElse(new ArrayList<>());

      metacardOpt.ifPresent(metacardList::add);
      return oldExchange;
    }
  }
}
