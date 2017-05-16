/**
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
package ddf.catalog.tests;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KarafConfigurator {
    private static final Logger LOG = LoggerFactory.getLogger(KarafConfigurator.class);

    public static Option karafConfiguration() {
        return composite(karafDistributionConfiguration().frameworkUrl(maven().groupId(
                "org.apache.karaf")
                        .artifactId("apache-karaf")
                        .version(karafVersion())
                        .type("zip"))
                        .unpackDirectory(new File("target", "exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(),
                configureConsole().ignoreLocalConsole(),
                logLevel().logLevel(LogLevelOption.LogLevel.WARN));
    }

    public static String karafVersion() {
        ConfigurationManager cm = new ConfigurationManager();
        return cm.getProperty("pax.exam.karaf.version");
    }

    public static Option addConfigFile(String filename) {
        return addConfigFile(filename, null);
    }

    public static Option addConfigFile(String filename, InputStream input) {
        try {
            File configFile = createTempFile(input);
            return replaceConfigurationFile("etc/" + filename, configFile);
        } catch (IOException e) {
            LOG.warn("Failed to add configuration file: " + filename, e);
            return null;
        }
    }

    private static File createTempFile(InputStream input) throws IOException {
        File tempFile = Files.createTempFile("configFile", ".temp")
                .toFile();
        tempFile.deleteOnExit();
        if (input != null) {
            FileUtils.copyInputStreamToFile(input, tempFile);
        }
        return tempFile;
    }
}
