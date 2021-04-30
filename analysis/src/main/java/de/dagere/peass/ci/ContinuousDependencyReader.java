package de.dagere.peass.ci;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.config.DependencyConfig;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.dependency.PeASSFolders;
import de.dagere.peass.dependency.ResultsFolders;
import de.dagere.peass.dependency.execution.EnvironmentVariables;
import de.dagere.peass.dependency.persistence.Dependencies;
import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependency.reader.VersionKeeper;
import de.dagere.peass.dependencyprocessors.VersionComparator;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitCommit;
import de.dagere.peass.vcs.GitUtils;
import de.dagere.peass.vcs.VersionIterator;
import de.dagere.peass.vcs.VersionIteratorGit;

public class ContinuousDependencyReader {

   private static final Logger LOG = LogManager.getLogger(ContinuousDependencyReader.class);

   private final DependencyConfig dependencyConfig;
   private final ExecutionConfig executionConfig;
   private final PeASSFolders folders;
   private final ResultsFolders resultsFolders;
   private final EnvironmentVariables env;

   public ContinuousDependencyReader(final DependencyConfig dependencyConfig, final ExecutionConfig executionConfig, final PeASSFolders folders, final ResultsFolders resultsFolders,
         final EnvironmentVariables env) {
      this.dependencyConfig = dependencyConfig;
      this.executionConfig = executionConfig;
      this.folders = folders;
      this.resultsFolders = resultsFolders;
      this.env = env;
   }

   Dependencies getDependencies(final VersionIterator iterator, final String url)
         throws Exception {
      Dependencies dependencies;

      final VersionKeeper noChanges = new VersionKeeper(new File(resultsFolders.getDependencyFile().getParentFile(), "nonChanges_" + folders.getProjectName() + ".json"));

      if (!resultsFolders.getDependencyFile().exists()) {
         dependencies = fullyLoadDependencies(url, iterator, noChanges);
      } else {
         dependencies = Constants.OBJECTMAPPER.readValue(resultsFolders.getDependencyFile(), Dependencies.class);
         VersionComparator.setDependencies(dependencies);

         if (dependencies.getVersions().size() > 0) {
            partiallyLoadDependencies(dependencies);
         } else {
            dependencies = fullyLoadDependencies(url, iterator, noChanges);
         }
      }
      VersionComparator.setDependencies(dependencies);

      return dependencies;
   }

   public VersionIterator getIterator(final String lastVersionName) {
      String versionName = GitUtils.getName(executionConfig.getVersion() != null ? executionConfig.getVersion() : "HEAD", folders.getProjectFolder());
      if (versionName.equals(lastVersionName)) {
         return null;
      }
      GitCommit currentCommit = new GitCommit(versionName, "", "", "");
      GitCommit lastAnalyzedCommit = new GitCommit(executionConfig.getVersionOld() != null ? executionConfig.getVersionOld() : lastVersionName, "", "", "");

      List<GitCommit> commits = new LinkedList<>();
      commits.add(lastAnalyzedCommit);
      commits.add(currentCommit);
      LOG.info("Analyzing {} - {}", lastAnalyzedCommit, currentCommit);
      VersionIteratorGit newIterator = new VersionIteratorGit(folders.getProjectFolder(), commits, lastAnalyzedCommit);
      return newIterator;
   }

   private void partiallyLoadDependencies(final Dependencies dependencies) throws FileNotFoundException, Exception {
      final String lastVersionName = dependencies.getNewestVersion();

      VersionIterator newIterator = getIterator(lastVersionName);
      if (newIterator != null) {
         executePartialRTS(dependencies, newIterator);
      }
   }

   private void executePartialRTS(final Dependencies dependencies, final VersionIterator newIterator) throws FileNotFoundException {
      if (executionConfig.isRedirectSubprocessOutputToFile()) {
         File logFile = new File(getDependencyreadingFolder(), newIterator.getTag() + "_" + newIterator.getPredecessor() + ".txt");
         LOG.info("Executing regression test selection update (step 1) - Log goes to {}", logFile.getAbsolutePath());
         try (LogRedirector director = new LogRedirector(logFile)) {
            doPartialRCS(dependencies, newIterator);
         }
      } else {
         doPartialRCS(dependencies, newIterator);
      }

   }

   private void doPartialRCS(final Dependencies dependencies, final VersionIterator newIterator) {
      DependencyReader reader = new DependencyReader(dependencyConfig, folders, resultsFolders, dependencies.getUrl(), newIterator, 
            new VersionKeeper(new File(resultsFolders.getDependencyFile().getParentFile(), "nochanges.json")), executionConfig, env);
      newIterator.goTo0thCommit();

      reader.readCompletedVersions(dependencies);
      reader.readDependencies();
   }

   public File getDependencyreadingFolder() {
      File folder = new File(resultsFolders.getDependencyFile().getParentFile(), "dependencyreading");
      if (!folder.exists()) {
         folder.mkdirs();
      }
      return folder;
   }

   private Dependencies fullyLoadDependencies(final String url, final VersionIterator iterator, final VersionKeeper nonChanges)
         throws Exception {
      if (executionConfig.isRedirectSubprocessOutputToFile()) {
         File logFile = new File(getDependencyreadingFolder(), iterator.getTag() + "_" + iterator.getPredecessor() + ".txt");
         LOG.info("Executing regression test selection (step 1) - Log goes to {}", logFile.getAbsolutePath());

         try (LogRedirector director = new LogRedirector(logFile)) {
            return doFullyLoadDependencies(url, iterator, nonChanges);
         }
      } else {
         return doFullyLoadDependencies(url, iterator, nonChanges);
      }
   }

   private Dependencies doFullyLoadDependencies(final String url, final VersionIterator iterator, final VersionKeeper nonChanges)
         throws IOException, InterruptedException, XmlPullParserException, JsonParseException, JsonMappingException {
      final DependencyReader reader = new DependencyReader(dependencyConfig, folders, resultsFolders, url, iterator, nonChanges, executionConfig, env);
      iterator.goToPreviousCommit();
      if (!reader.readInitialVersion()) {
         LOG.error("Analyzing first version was not possible");
      } else {
         reader.readDependencies();
      }
      Dependencies dependencies = Constants.OBJECTMAPPER.readValue(resultsFolders.getDependencyFile(), Dependencies.class);
      return dependencies;
   }
}
