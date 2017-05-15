package ddf.catalog.tests;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

import java.io.File;

import javax.inject.Inject;

import org.apache.karaf.features.BootFinished;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DirectoryMonitorTest {
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryMonitorTest.class);

    /**
     * To make sure the tests run only when the boot features are fully
     * installed
     */
    @Inject
    private BootFinished bootFinished;

    @Inject
    private BundleContext bc;

    @Configuration
    public Option[] config() {
        return options(karafDistributionConfiguration().frameworkUrl(maven().groupId(
                "org.apache.karaf")
                        .artifactId("apache-karaf")
                        .version(karafVersion())
                        .type("zip"))
                        .unpackDirectory(new File("target", "exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(),
                configureConsole().ignoreLocalConsole(),
                logLevel().logLevel(LogLevelOption.LogLevel.WARN),
                features(maven().groupId("org.apache.karaf.features")
                        .artifactId("standard")
                        .versionAsInProject()
                        .classifier("features")
                        .type("xml"), "scr"));
    }

    public static String karafVersion() {
        ConfigurationManager cm = new ConfigurationManager();
        return cm.getProperty("pax.exam.karaf.version", "4.0.7");
    }

    @Test
    public void testBundleContext() {
        LOG.error("HELLOOOOOOO WORLD!!!!!!!");
        LOG.error("Bundle Context: " + bc);
    }
}
