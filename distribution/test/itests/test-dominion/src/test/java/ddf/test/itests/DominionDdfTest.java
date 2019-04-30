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
package ddf.test.itests;

import ddf.test.itests.annotations.ClearCatalog;
import org.codice.ddf.dominion.commons.options.DDFCommonOptions;
import org.codice.ddf.dominion.options.DDFOptions;
import org.codice.dominion.Dominion;
import org.codice.pax.exam.junit.ConfigurationAdmin;
import org.codice.pax.exam.junit.ServiceAdmin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DDFCommonOptions.ConfigureVMOptionsForTesting
@DDFCommonOptions.ConfigureDebugging
@DDFCommonOptions.ConfigurePorts
@DDFCommonOptions.ConfigureLogging
@DDFOptions.InstallDistribution(solr = true)
@ClearCatalog
@ConfigurationAdmin
@ServiceAdmin
@RunWith(Dominion.class)
public class DominionDdfTest {
  private final Logger logger = LoggerFactory.getLogger(DominionDdfTest.class);

  @Test
  public void test() {
    logger.error("Hello, World!");
  }
}
