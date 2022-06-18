package de.dagere.peass.ci;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.analysis.changes.ChangeReader;
import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.config.TestSelectionConfig;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.persistence.StaticTestSelection;
import de.dagere.peass.dependencyprocessors.VersionComparator;
import de.dagere.peass.dependencyprocessors.VersionComparatorInstance;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitUtils;
import de.dagere.peass.vcs.VersionControlSystem;
import de.dagere.peass.vcs.VersionIteratorGit;

public class ContinuousExecutor {

   private static final Logger LOG = LogManager.getLogger(ContinuousExecutor.class);

   private final MeasurementConfig measurementConfig;
   private final TestSelectionConfig dependencyConfig;

   private final String version;
   private final String versionOld;
   private final VersionIteratorGit iterator;
   private final VersionComparatorInstance comparator;

   private final File originalProjectFolder;
   private final File localFolder;
   private final PeassFolders folders;
   private final ResultsFolders resultsFolders;

   private final EnvironmentVariables env;

   public ContinuousExecutor(final File projectFolder, final MeasurementConfig measurementConfig, final TestSelectionConfig dependencyConfig, final EnvironmentVariables env)
         throws InterruptedException, IOException {
      this.originalProjectFolder = projectFolder;
      this.measurementConfig = measurementConfig;
      this.dependencyConfig = dependencyConfig;
      this.env = env;
      LOG.info("Properties: " + env.getProperties());

      File vcsFolder = VersionControlSystem.findVCSFolder(projectFolder);
      localFolder = ContinuousFolderUtil.getLocalFolder(vcsFolder);
      String projectName = ContinuousFolderUtil.getSubFolderPath(projectFolder);
      File projectFolderLocal = new File(localFolder, projectName);
      getGitRepo(projectFolder, measurementConfig, projectFolderLocal);
      resultsFolders = new ResultsFolders(localFolder, projectName);

      folders = new PeassFolders(projectFolderLocal);

      StaticTestSelection dependencies = null;
      if (resultsFolders.getStaticTestSelectionFile().exists()) {
         dependencies = Constants.OBJECTMAPPER.readValue(resultsFolders.getStaticTestSelectionFile(), StaticTestSelection.class);
      }

      DependencyIteratorBuilder iteratorBuiler = new DependencyIteratorBuilder(measurementConfig.getExecutionConfig(), dependencies, folders);
      iterator = iteratorBuiler.getIterator();
      version = iteratorBuiler.getVersion();
      versionOld = iteratorBuiler.getVersionOld();
      measurementConfig.getExecutionConfig().setCommit(version);
      measurementConfig.getExecutionConfig().setCommitOld(versionOld);
      LOG.debug("Version: {} VersionOld: {}", version, versionOld);

      List<String> commits = GitUtils.getCommits(projectFolderLocal, false, true);
      comparator = new VersionComparatorInstance(commits);
   }

   private void getGitRepo(final File projectFolder, final MeasurementConfig measurementConfig, final File projectFolderLocal) throws InterruptedException, IOException {
      if (!localFolder.exists() || !projectFolderLocal.exists()) {
         ContinuousFolderUtil.cloneProject(projectFolder, localFolder);
         if (!projectFolderLocal.exists()) {
            throw new RuntimeException("Was not able to clone project to " + projectFolderLocal.getAbsolutePath() + " (folder not existing)");
         }
      } else {
         LOG.debug("Going to commit {} on existing folder", measurementConfig.getExecutionConfig().getCommit());
         GitUtils.reset(projectFolderLocal);
         GitUtils.clean(projectFolderLocal);
         GitUtils.pull(projectFolderLocal);
         GitUtils.goToTag(measurementConfig.getExecutionConfig().getCommit(), projectFolderLocal);
      }
   }

   public RTSResult executeRTS() {
      final String url = GitUtils.getURL(originalProjectFolder);

      RTSResult tests = executeRegressionTestSelection(url);
      return tests;
   }

   public void measure(final Set<TestCase> tests) {
      try {
         File measurementFolder = executeMeasurement(tests);
         analyzeMeasurements(measurementFolder);
      } catch (IOException | InterruptedException | XmlPullParserException e) {
         throw new RuntimeException(e);
      }
   }

   public void execute() throws Exception {
      Set<TestCase> tests = executeRTS().getTests();
      measure(tests);
   }

   protected RTSResult executeRegressionTestSelection(final String url) {
      ContinuousDependencyReader dependencyReader = new ContinuousDependencyReader(dependencyConfig, measurementConfig.getExecutionConfig(), measurementConfig.getKiekerConfig(),
            folders, resultsFolders, env);
      final RTSResult tests = dependencyReader.getTests(iterator, url, version, measurementConfig);
      tests.setVersionOld(versionOld);

      SourceReader sourceReader = new SourceReader(measurementConfig.getExecutionConfig(), version, versionOld, resultsFolders, folders);
      sourceReader.readMethodSources(tests.getTests());

      return tests;
   }

   protected File executeMeasurement(final Set<TestCase> tests) throws IOException, InterruptedException, XmlPullParserException {
      final File fullResultsVersion = resultsFolders.getVersionFullResultsFolder(version, versionOld);
      File logFile = resultsFolders.getMeasurementLogFile(version, versionOld);
      final ContinuousMeasurementExecutor measurementExecutor = new ContinuousMeasurementExecutor(folders, measurementConfig, env, comparator);
      final File measurementFolder = measurementExecutor.executeMeasurements(tests, fullResultsVersion, logFile);
      return measurementFolder;
   }

   private void analyzeMeasurements(final File measurementFolder)
         throws InterruptedException, IOException, JsonGenerationException, JsonMappingException, XmlPullParserException {
      StaticTestSelection selectedTests = Constants.OBJECTMAPPER.readValue(resultsFolders.getStaticTestSelectionFile(), StaticTestSelection.class);
      ChangeReader changeReader = new ChangeReader(resultsFolders, selectedTests);
      changeReader.readFile(measurementFolder.getParentFile());
   }

   public String getLatestVersion() {
      return version;
   }

   public PeassFolders getFolders() {
      return folders;
   }

   public String getVersionOld() {
      LOG.debug("Version old: {}", versionOld);
      return versionOld;
   }

   public File getProjectFolder() {
      return folders.getProjectFolder();
   }

   public File getLocalFolder() {
      return localFolder;
   }
}
