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
package ddf.test.itests.rules;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearCatalog implements MethodRule {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClearCatalog.class);

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          LOGGER.error("Clearing Catalog");
          statement.evaluate();
        } finally {
          Thread.interrupted();
        }
      }
    };
  }
}
