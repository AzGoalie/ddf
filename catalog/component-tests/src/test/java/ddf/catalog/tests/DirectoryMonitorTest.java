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
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static ddf.catalog.tests.KarafConfigurator.addConfigFile;
import static ddf.catalog.tests.KarafConfigurator.karafConfiguration;
import static ddf.catalog.tests.features.CamelFeatures.camelFeatures;
import static ddf.catalog.tests.features.CxfFeatures.cxfFeatures;
import static ddf.catalog.tests.features.KarafStandardFeatures.karafStandardFeatures;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.karaf.features.BootFinished;
import org.codice.ddf.catalog.content.monitor.ContentDirectoryMonitor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DirectoryMonitorTest {
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryMonitorTest.class);

    @Inject
    private BundleContext bundleContext;

    @Inject
    private BootFinished bootFinished;

    @Inject
    private ContentDirectoryMonitor directoryMonitor;

    @Inject
    private CamelContext camelContext;

    @Configuration
    public Option[] config() {
        return options(karafConfiguration(),
                karafStandardFeatures("standard", "spring"),
                camelFeatures("camel"),
                cxfFeatures("cxf-rt-security-saml",
                        "cxf-bindings-soap",
                        "cxf-ws-policy",
                        "cxf-ws-security",
                        "cxf-frontend-javascript",
                        "cxf-jaxrs"),
                contentDirectoryMonitorDependencies(),
                addConfigFile(
                        "org.codice.ddf.catalog.content.monitor.ContentDirectoryMonitor-test.cfg"));
    }

    private Option contentDirectoryMonitorDependencies() {
        return composite(mavenBundle("ddf.catalog.core",
                "catalog-core-camelcontext",
                getDdfVersion()).start(),
                mavenBundle("com.google.guava", "guava", "18.0").start(),
                mavenBundle("commons-lang", "commons-lang", "2.6").start(),
                mavenBundle("ddf.platform.api", "platform-api", getDdfVersion()).start(),
                mavenBundle("org.codice.thirdparty", "gt-opengis", "8.4_1").start(),
                mavenBundle("ddf.catalog.core", "catalog-core-api", getDdfVersion()).start(),
                mavenBundle("org.apache.shiro", "shiro-core", "1.3.2").start(),
                mavenBundle("javax.servlet", "javax.servlet-api", "3.1.0").start(),
                mavenBundle("javax.validation", "validation-api", "1.1.0.Final").start(),
                mavenBundle("commons-io", "commons-io", "2.5").start(),
                mavenBundle("org.bouncycastle", "bcprov-ext-jdk15on", "1.56").start(),
                mavenBundle("ddf.security.core", "security-core-api", getDdfVersion()).start(),
                mavenBundle("ddf.security.expansion",
                        "security-expansion-api",
                        getDdfVersion()).start(),
                mavenBundle("ddf.security.expansion",
                        "security-expansion-impl",
                        getDdfVersion()).start(),
                mavenBundle("commons-collections", "commons-collections", "3.2.2").start(),
                mavenBundle("commons-configuration", "commons-configuration", "1.10").start(),
                mavenBundle("ddf.distribution", "branding-api", getDdfVersion()).start(),
                mavenBundle("ddf.distribution", "ddf-branding-plugin", getDdfVersion()).start(),
                mavenBundle("ddf.platform", "platform-configuration", getDdfVersion()).start(),
                mavenBundle("ddf.security.handler",
                        "security-handler-api",
                        getDdfVersion()).start(),
                mavenBundle("ddf.catalog.core",
                        "catalog-core-directorymonitor",
                        getDdfVersion()).start());
    }

    @Test
    public void testBundleContext() {
        LOG.error("HELLOOOOOOO WORLD!!!!!!!");
        LOG.error("Bundle Context: " + bundleContext);
        LOG.error("BootFinished: " + bootFinished);
    }

    private String getDdfVersion() {
        ConfigurationManager cm = new ConfigurationManager();
        return cm.getProperty("ddf.version");
    }
}

