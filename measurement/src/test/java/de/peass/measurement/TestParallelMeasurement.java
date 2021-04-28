package de.peass.measurement;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.dagere.peass.dependencyprocessors.DependencyTester;
import de.peass.config.MeasurementConfiguration;
import de.peass.config.MeasurementStrategy;
import de.peass.dependency.ExecutorCreator;
import de.peass.dependency.PeASSFolders;
import de.peass.dependency.execution.EnvironmentVariables;
import de.peass.measurement.analysis.TestDependencyTester;
import de.peass.measurement.rca.helper.VCSTestUtils;
import de.peass.vcs.GitUtils;
import de.peass.vcs.VersionControlSystem;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ VersionControlSystem.class, ExecutorCreator.class, GitUtils.class })
@PowerMockIgnore({ "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.dom.*" })
public class TestParallelMeasurement {

   @Rule
   public TemporaryFolder folder = new TemporaryFolder();

   @Test
   public void testFiles() throws Exception {
      VCSTestUtils.mockGetVCS();
      VCSTestUtils.mockGoToTagAny();
      
      final PeASSFolders folders = new PeASSFolders(folder.getRoot());
      final MeasurementConfiguration configuration = new MeasurementConfiguration(4, "2", "1");
      configuration.setMeasurementStrategy(MeasurementStrategy.PARALLEL);

      MavenTestExecutorMocker.mockExecutor(folders, configuration);

      final DependencyTester tester = new DependencyTester(folders, configuration, new EnvironmentVariables());

      tester.evaluate(TestDependencyTester.EXAMPLE_TESTCASE);

      TestDependencyTester.checkResult(folders);
   }
}
