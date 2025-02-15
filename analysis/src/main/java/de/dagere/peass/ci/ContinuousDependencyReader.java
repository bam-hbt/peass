package de.dagere.peass.ci;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.javaparser.ParseException;

import de.dagere.peass.ci.logHandling.LogRedirector;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.config.KiekerConfig;
import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.config.TestSelectionConfig;
import de.dagere.peass.dependency.ExecutorCreator;
import de.dagere.peass.dependency.RTSTestTransformerBuilder;
import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependency.persistence.CommitStaticSelection;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.persistence.StaticTestSelection;
import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependency.reader.CommitKeeper;
import de.dagere.peass.dependency.traces.coverage.CoverageSelectionInfo;
import de.dagere.peass.dependencyprocessors.CommitComparatorInstance;
import de.dagere.peass.dependencyprocessors.VersionComparator;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.execution.utils.TestExecutor;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.testtransformation.TestTransformer;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.CommitIterator;
import net.kieker.sourceinstrumentation.AllowedKiekerRecord;

public class ContinuousDependencyReader {

   private static final Logger LOG = LogManager.getLogger(ContinuousDependencyReader.class);

   private final TestSelectionConfig dependencyConfig;
   private final ExecutionConfig executionConfig;
   private final KiekerConfig kiekerConfig;
   private final PeassFolders folders;
   private final ResultsFolders resultsFolders;
   private final EnvironmentVariables env;

   public ContinuousDependencyReader(final TestSelectionConfig dependencyConfig, final ExecutionConfig executionConfig, final KiekerConfig kiekerConfig, final PeassFolders folders,
         final ResultsFolders resultsFolders, final EnvironmentVariables env) {
      this.dependencyConfig = dependencyConfig;
      this.executionConfig = executionConfig;
      this.kiekerConfig = new KiekerConfig(kiekerConfig);
      this.kiekerConfig.setUseKieker(true);
      this.kiekerConfig.setRecord(AllowedKiekerRecord.OPERATIONEXECUTION);
      this.kiekerConfig.setUseAggregation(false);
      this.folders = folders;
      this.resultsFolders = resultsFolders;
      this.env = env;
   }

   public RTSResult getTests(final CommitIterator iterator, final String url, final String commit, final MeasurementConfig measurementConfig) {
      final StaticTestSelection dependencies = getDependencies(iterator, url);

      RTSResult result;
      final Set<TestMethodCall> tests;
      if (dependencies.getCommits().size() > 0) {
         CommitStaticSelection commitStaticSelection = dependencies.getCommits().get(commit);
         LOG.debug("Commit static selection for commit {}, running was: {}", commit, commitStaticSelection != null ? commitStaticSelection.isRunning() : "null");
         if (dependencyConfig.isGenerateTraces()) {
            tests = selectResults(commit);
            result = new RTSResult(tests, commitStaticSelection.isRunning());
         } else {
            tests = commitStaticSelection.getTests().getTestMethods();
            result = new RTSResult(tests, commitStaticSelection.isRunning());
         }

         // final Set<TestCase> tests = selectIncludedTests(dependencies);
         NonIncludedTestRemover.removeNotIncludedMethods(tests, measurementConfig.getExecutionConfig());
      } else if (!dependencies.getInitialcommit().isRunning()) {
         tests = new HashSet<>();
         result = new RTSResult(tests, false);
         LOG.info("No test executed - predecessor test is not running.");
      } else {
         tests = new HashSet<>();
         result = new RTSResult(tests, true);
         LOG.info("No test executed - commit did not contain changed tests.");
      }
      return result;
   }

   private Set<TestMethodCall> selectResults(final String commit) {
      try {
         final Set<TestMethodCall> tests;
         if (dependencyConfig.isGenerateCoverageSelection()) {
            LOG.info("Using coverage-based test selection");
            ExecutionData executionData = Constants.OBJECTMAPPER.readValue(resultsFolders.getCoverageSelectionFile(), ExecutionData.class);
            tests = fetchTestset(commit, executionData);
         } else {
            if (dependencyConfig.isGenerateTwiceExecutability()) {
               LOG.info("Using twice executable test selection results");
               ExecutionData executionData = Constants.OBJECTMAPPER.readValue(resultsFolders.getTwiceExecutableFile(), ExecutionData.class);
               tests = fetchTestset(commit, executionData);
            } else {
               LOG.info("Using trace test selection results");
               ExecutionData executionData = Constants.OBJECTMAPPER.readValue(resultsFolders.getTraceTestSelectionFile(), ExecutionData.class);
               tests = fetchTestset(commit, executionData);
            }
         }
         return tests;
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Fetches the test set from the current commit; it is required to allow null, in case a compile error occured
    * 
    * @param commit
    * @param executionData
    * @return
    */
   private Set<TestMethodCall> fetchTestset(final String commit, final ExecutionData executionData) {
      final Set<TestMethodCall> tests;
      TestSet commitTestSet = executionData.getCommits().get(commit);
      tests = commitTestSet != null ? commitTestSet.getTestMethods() : new HashSet<TestMethodCall>();
      return tests;
   }

   StaticTestSelection getDependencies(final CommitIterator iterator, final String url) {
      try {
         StaticTestSelection dependencies;

         final CommitKeeper noChanges = new CommitKeeper(
               new File(resultsFolders.getStaticTestSelectionFile().getParentFile(), "nonChanges_" + folders.getProjectName() + ".json"));

         if (!resultsFolders.getStaticTestSelectionFile().exists()) {
            LOG.debug("Fully loading dependencies");
            dependencies = fullyLoadDependencies(url, iterator, noChanges);
         } else {
            LOG.debug("Partially loading dependencies");
            dependencies = Constants.OBJECTMAPPER.readValue(resultsFolders.getStaticTestSelectionFile(), StaticTestSelection.class);
            CommitComparatorInstance comparator = new CommitComparatorInstance(dependencies);

            if (iterator != null) {
               executePartialRTS(dependencies, iterator, comparator);
            }
         }
         VersionComparator.setDependencies(dependencies);

         return dependencies;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void executePartialRTS(final StaticTestSelection dependencies, final CommitIterator newIterator, CommitComparatorInstance comparator) throws FileNotFoundException {
      if (executionConfig.isRedirectSubprocessOutputToFile()) {
         File logFile = resultsFolders.getRTSLogFile(newIterator.getCommitName(), newIterator.getPredecessor());
         LOG.info("Executing regression test selection update - Log goes to {}", logFile.getAbsolutePath());
         try (LogRedirector director = new LogRedirector(logFile)) {
            doPartialRCS(dependencies, newIterator, comparator);
         }
      } else {
         doPartialRCS(dependencies, newIterator, comparator);
      }

   }

   private void doPartialRCS(final StaticTestSelection dependencies, final CommitIterator newIterator, CommitComparatorInstance comparator) {
      DependencyReader reader = new DependencyReader(dependencyConfig, folders, resultsFolders, dependencies.getUrl(), newIterator,
            new CommitKeeper(new File(resultsFolders.getStaticTestSelectionFile().getParentFile(), "nochanges.json")), executionConfig, kiekerConfig, env);
      newIterator.goTo0thCommit();

      reader.readCompletedCommits(dependencies, comparator);

      try {
         ExecutionData executions = Constants.OBJECTMAPPER.readValue(resultsFolders.getTraceTestSelectionFile(), ExecutionData.class);
         reader.setExecutionData(executions);

         if (resultsFolders.getCoverageSelectionFile().exists()) {
            ExecutionData coverageExecutions = Constants.OBJECTMAPPER.readValue(resultsFolders.getCoverageSelectionFile(), ExecutionData.class);
            reader.setCoverageExecutions(coverageExecutions);

            if (resultsFolders.getCoverageInfoFile().exists()) {
               CoverageSelectionInfo coverageInfo = Constants.OBJECTMAPPER.readValue(resultsFolders.getCoverageInfoFile(), CoverageSelectionInfo.class);
               reader.setCoverageInfo(coverageInfo);
            }
         }

         reader.readDependencies();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

   }

   private StaticTestSelection fullyLoadDependencies(final String url, final CommitIterator iterator, final CommitKeeper nonChanges)
         throws Exception {
      if (executionConfig.isRedirectSubprocessOutputToFile()) {
         File logFile = resultsFolders.getRTSLogFile(iterator.getCommitName(), iterator.getPredecessor());
         LOG.info("Executing regression test selection - Log goes to {}", logFile.getAbsolutePath());

         try (LogRedirector director = new LogRedirector(logFile)) {
            return doFullyLoadDependencies(url, iterator, nonChanges);
         }
      } else {
         return doFullyLoadDependencies(url, iterator, nonChanges);
      }
   }

   private StaticTestSelection doFullyLoadDependencies(final String url, final CommitIterator iterator, final CommitKeeper nonChanges)
         throws IOException, InterruptedException, XmlPullParserException, JsonParseException, JsonMappingException, ParseException {
      final DependencyReader reader = new DependencyReader(dependencyConfig, folders, resultsFolders, url, iterator, nonChanges, executionConfig, kiekerConfig, env);
      iterator.goToPreviousCommit();

      boolean isVersionRunning = checkCommitRunning(iterator);
      if (!isVersionRunning) {
         createFailedSelection(iterator);
      } else {
         if (!reader.readInitialCommit()) {
            LOG.error("Analyzing first commit did not yield results");
         } else {
            reader.readDependencies();
         }
      }

      StaticTestSelection dependencies = Constants.OBJECTMAPPER.readValue(resultsFolders.getStaticTestSelectionFile(), StaticTestSelection.class);
      return dependencies;
   }

   private boolean checkCommitRunning(CommitIterator iterator) {
      TestTransformer temporaryTransformer = RTSTestTransformerBuilder.createTestTransformer(folders, executionConfig, kiekerConfig);
      TestExecutor executor = ExecutorCreator.createExecutor(folders, temporaryTransformer, env);
      boolean isVersionRunning = executor.isCommitRunning(iterator.getCommitName());
      return isVersionRunning;
   }

   private void createFailedSelection(final CommitIterator iterator) throws IOException, StreamWriteException, DatabindException {
      LOG.debug("Predecessor commit is not running, skipping execution");
      StaticTestSelection initialVersionFailed = new StaticTestSelection();
      initialVersionFailed.getInitialcommit().setCommit(iterator.getCommitName());
      initialVersionFailed.getInitialcommit().setRunning(false);
      Constants.OBJECTMAPPER.writeValue(resultsFolders.getStaticTestSelectionFile(), initialVersionFailed);
   }
}
