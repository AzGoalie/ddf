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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withBnd;
import static ddf.catalog.tests.KarafConfigurator.karafConfiguration;
import static ddf.catalog.tests.features.CamelFeatures.camelFeatures;
import static ddf.catalog.tests.features.CxfFeatures.cxfFeatures;
import static ddf.catalog.tests.features.KarafStandardFeatures.karafStandardFeatures;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.karaf.features.BootFinished;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

import ddf.catalog.CatalogFramework;
import ddf.catalog.tests.mocks.MockCatalogFramework;
import ddf.catalog.tests.mocks.MockSecurityManager;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DirectoryMonitorTest {
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryMonitorTest.class);

    private static final String MONITORED_DIRECTORY_PROPERTY =
            "ddf.catalog.tests.monitoredDirectory";

    private static final int TIMEOUT_IN_SECONDS = 10;

    @Inject
    private BundleContext bundleContext;

    @Inject
    private BootFinished bootFinished;

    @Inject
    private CamelContext camelContext;

    private MockCatalogFramework catalogFramework;

    @Configuration
    public Option[] config() throws IOException {

        return options(testDependencies(),
                karafConfiguration(),
                karafStandardFeatures("standard", "spring"),
                //                debugConfiguration(),
                keepRuntimeFolder(),
                camelFeatures("camel"),
                cxfFeatures("cxf-rt-security-saml",
                        "cxf-bindings-soap",
                        "cxf-ws-policy",
                        "cxf-ws-security",
                        "cxf-frontend-javascript",
                        "cxf-jaxrs"),
                keystoreAndTruststoreConfig(),
                //                mockBundle(MockCatalogFramework.class),
                mockBundle(MockSecurityManager.class),
                contentDirectoryMonitorDependencies(),
                createContentDirectoryMonitorConfig());
    }

    private Option testDependencies() {
        return mavenBundle("org.awaitility", "awaitility", "3.0.0");
    }

    private Option keystoreAndTruststoreConfig() {
        InputStream keystore =
                DirectoryMonitorTest.class.getResourceAsStream("/serverKeystore.jks");
        InputStream truststore = DirectoryMonitorTest.class.getResourceAsStream(
                "/serverTruststore.jks");

        return KeystoreTruststoreConfigurator.createKeystoreAndTruststore(keystore, truststore);
    }

    private Option createContentDirectoryMonitorConfig() throws IOException {
        File monitoredDirectory = Files.createTempDir();
        System.setProperty(MONITORED_DIRECTORY_PROPERTY, monitoredDirectory.getCanonicalPath());
        return composite(propagateSystemProperty(MONITORED_DIRECTORY_PROPERTY),
                editConfigurationFilePut(
                        "etc/org.codice.ddf.catalog.content.monitor.ContentDirectoryMonitor-test.cfg",
                        "monitoredDirectoryPath",
                        monitoredDirectory.getCanonicalPath()));
    }

    private Option apacheCommons() {
        return composite(mavenBundle("org.apache.commons", "commons-lang3", "3.4").start(),
                mavenBundle("commons-lang", "commons-lang", "2.6").start(),
                mavenBundle("commons-io", "commons-io", "2.5").start(),
                mavenBundle("commons-collections", "commons-collections", "3.2.2").start(),
                mavenBundle("commons-configuration", "commons-configuration", "1.10").start());
    }

    private Option contentDirectoryMonitorDependencies() {
        return composite(apacheCommons(),
                mavenBundle("ddf.platform.util", "util-uuidgenerator-api", getDdfVersion()).start(),
                mavenBundle("ddf.platform.util",
                        "util-uuidgenerator-impl",
                        getDdfVersion()).start(),
                mavenBundle("ddf.catalog.core",
                        "catalog-core-camelcontext",
                        getDdfVersion()).start(),
                mavenBundle("com.google.guava", "guava", "18.0").start(),
                mavenBundle("ddf.platform.api", "platform-api", getDdfVersion()).start(),
                mavenBundle("org.codice.thirdparty", "gt-opengis", "8.4_1").start(),
                mavenBundle("ddf.catalog.core", "catalog-core-api", getDdfVersion()).start(),
                mavenBundle("org.apache.shiro", "shiro-core", "1.3.2").start(),
                mavenBundle("javax.servlet", "javax.servlet-api", "3.1.0").start(),
                mavenBundle("javax.validation", "validation-api", "1.1.0.Final").start(),
                mavenBundle("org.bouncycastle", "bcprov-ext-jdk15on", "1.56").start(),
                mavenBundle("ddf.security.core", "security-core-api", getDdfVersion()).start(),
                mavenBundle("ddf.security.expansion",
                        "security-expansion-api",
                        getDdfVersion()).start(),
                mavenBundle("ddf.security.expansion",
                        "security-expansion-impl",
                        getDdfVersion()).start(),
                mavenBundle("ddf.distribution", "branding-api", getDdfVersion()).start(),
                mavenBundle("ddf.distribution", "ddf-branding-plugin", getDdfVersion()).start(),
                mavenBundle("ddf.platform", "platform-configuration", getDdfVersion()).start(),
                mavenBundle("ddf.security.handler",
                        "security-handler-api",
                        getDdfVersion()).start(),

                mavenBundle("ddf.mime.core", "mime-core-api", getDdfVersion()).start(),
                mavenBundle("ddf.mime.core", "mime-core-impl", getDdfVersion()).start(),
                mavenBundle("ddf.catalog.core",
                        "catalog-core-camelcomponent",
                        getDdfVersion()).start(),

                mavenBundle("ddf.catalog.core",
                        "catalog-core-directorymonitor",
                        getDdfVersion()).start());
    }

    private Option mockBundle(Class tClass) {
        return streamBundle(bundle().add(tClass)
                .set(Constants.BUNDLE_SYMBOLICNAME, tClass.getName())
                .set(Constants.EXPORT_PACKAGE, "*")
                .set(Constants.IMPORT_PACKAGE, "*")
                .set(Constants.BUNDLE_ACTIVATOR, tClass.getName())
                .build(withBnd()));
    }

    @Before
    public void setup() {
        createCatalogFramework();
    }

    private void createCatalogFramework() {
        catalogFramework = new MockCatalogFramework();
        Dictionary properties = new Properties();
        bundleContext.registerService(CatalogFramework.class, catalogFramework, properties);
        await().atMost(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .until(() -> bundleContext.getServiceReference(CatalogFramework.class) != null);
    }

    @Test
    public void testBundleContext() {
        LOG.error("HELLOOOOOOO WORLD!!!!!!!");
        LOG.error("Bundle Context: " + bundleContext);
        LOG.error("BootFinished: " + bootFinished);
    }

    @Test
    public void testCamelContext() {
        Component component = camelContext.getComponent("content");
        assertThat(component, notNullValue());
    }

    @Test
    public void testDirectoryMonitor() throws IOException, InterruptedException {
        String directoryPath = System.getProperty(MONITORED_DIRECTORY_PROPERTY);
        LOG.error("Directory being monitored: " + directoryPath);
        createTestFile(directoryPath);

        await().atMost(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
                .until(() -> catalogFramework.createRequests.size() == 1);
    }

    private void createTestFile(String directoryPath) throws IOException {
        File file = File.createTempFile("test", ".txt", new File(directoryPath));
        FileWriter writer = new FileWriter(file);
        writer.append("Hello, World!");
        writer.flush();
        writer.close();
    }

    private String getDdfVersion() {
        ConfigurationManager cm = new ConfigurationManager();
        return cm.getProperty("ddf.version");
    }
}

