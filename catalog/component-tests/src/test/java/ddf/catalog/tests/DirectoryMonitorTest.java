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
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withBnd;
import static ddf.catalog.tests.configurators.KarafConfigurator.karafConfiguration;
import static ddf.catalog.tests.features.CamelFeatures.CamelFeature.CAMEL;
import static ddf.catalog.tests.features.CamelFeatures.camelFeatures;
import static ddf.catalog.tests.features.CxfFeatures.CxfFeature.CXF_BINDINGS_SOAP;
import static ddf.catalog.tests.features.CxfFeatures.CxfFeature.CXF_FRONTEND_JAVASCRIPT;
import static ddf.catalog.tests.features.CxfFeatures.CxfFeature.CXF_JAXRS;
import static ddf.catalog.tests.features.CxfFeatures.CxfFeature.CXF_RT_SECURITY_SAML;
import static ddf.catalog.tests.features.CxfFeatures.CxfFeature.CXF_WS_POLICY;
import static ddf.catalog.tests.features.CxfFeatures.CxfFeature.CXF_WS_SECURITY;
import static ddf.catalog.tests.features.CxfFeatures.cxfFeatures;
import static ddf.catalog.tests.features.KarafSpringFeatures.SpringFeature.SPRING;
import static ddf.catalog.tests.features.KarafSpringFeatures.karafSpringFeatures;
import static ddf.catalog.tests.features.KarafStandardFeatures.StandardFeature.STANDARD;
import static ddf.catalog.tests.features.KarafStandardFeatures.karafStandardFeatures;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.karaf.features.BootFinished;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
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
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.tests.configurators.KeystoreTruststoreConfigurator;
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
                karafStandardFeatures(STANDARD),
                karafSpringFeatures(SPRING),
                camelFeatures(CAMEL),
                cxfFeatures(CXF_RT_SECURITY_SAML,
                        CXF_BINDINGS_SOAP,
                        CXF_WS_POLICY,
                        CXF_WS_SECURITY,
                        CXF_FRONTEND_JAVASCRIPT,
                        CXF_JAXRS),
                keystoreAndTruststoreConfig(),
                mockBundle(MockSecurityManager.class),
                contentDirectoryMonitorDependencies(),
                createContentDirectoryMonitorConfig());
    }

    private Option testDependencies() {
        return startBundle("org.awaitility", "awaitility");
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

    private Option contentDirectoryMonitorDependencies() {
        return composite(startBundle("org.apache.commons", "commons-lang3"),
                startBundle("commons-lang", "commons-lang"),
                startBundle("commons-io", "commons-io"),
                startBundle("commons-collections", "commons-collections"),
                startBundle("commons-configuration", "commons-configuration"),

                startBundle("com.google.guava", "guava"),

                startBundle("org.apache.shiro", "shiro-core"),
                startBundle("javax.servlet", "javax.servlet-api"),
                startBundle("javax.validation", "validation-api"),
                startBundle("org.bouncycastle", "bcprov-jdk15on"),

                startBundle("ddf.platform.util", "util-uuidgenerator-api"),
                startBundle("ddf.platform.util", "util-uuidgenerator-impl"),
                startBundle("ddf.catalog.core", "catalog-core-camelcontext"),
                startBundle("ddf.platform.api", "platform-api"),
                startBundle("org.codice.thirdparty", "gt-opengis"),
                startBundle("ddf.catalog.core", "catalog-core-api"),
                startBundle("ddf.security.core", "security-core-api"),
                startBundle("ddf.security.expansion", "security-expansion-api"),
                startBundle("ddf.security.expansion", "security-expansion-impl"),
                startBundle("ddf.distribution", "branding-api"),
                startBundle("ddf.distribution", "ddf-branding-plugin"),
                startBundle("ddf.platform", "platform-configuration"),
                startBundle("ddf.security.handler", "security-handler-api"),

                startBundle("ddf.mime.core", "mime-core-api"),
                startBundle("ddf.mime.core", "mime-core-impl"),
                startBundle("ddf.catalog.core", "catalog-core-camelcomponent"),

                startBundle("ddf.catalog.core", "catalog-core-directorymonitor"));
    }

    private Option startBundle(String artifactId, String groupId) {
        return mavenBundle(artifactId, groupId).versionAsInProject()
                .start();
    }

    private Option mockBundle(Class bundle) {
        return streamBundle(bundle().add(bundle)
                .set(Constants.BUNDLE_SYMBOLICNAME, bundle.getName())
                .set(Constants.EXPORT_PACKAGE, "*")
                .set(Constants.IMPORT_PACKAGE, "*")
                .set(Constants.BUNDLE_ACTIVATOR, bundle.getName())
                .build(withBnd()));
    }

    @Before
    public void setup() {
        createCatalogFramework();
    }

    private void createCatalogFramework() {
        catalogFramework = new MockCatalogFramework();
        bundleContext.registerService(CatalogFramework.class, catalogFramework, null);
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
                .until(() -> catalogFramework.createStorageRequests.size() > 0);

        ContentItem item = catalogFramework.createStorageRequests.get(0)
                .getContentItems()
                .get(0);
        assertThat(item.getFilename(), allOf(startsWith("test"), endsWith(".txt")));
    }

    private void createTestFile(String directoryPath) throws IOException {
        File file = File.createTempFile("test", ".txt", new File(directoryPath));
        Files.write("Hello, World", file, Charset.forName("UTF-8"));
    }
}

