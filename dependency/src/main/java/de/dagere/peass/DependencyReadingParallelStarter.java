package de.dagere.peass;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.peass.config.DependencyReaderConfigMixin;
import de.peass.dependency.execution.EnvironmentVariables;
import de.peass.dependency.parallel.PartialDependenciesMerger;
import de.peass.dependency.persistence.Dependencies;
import de.peass.dependency.reader.DependencyParallelReader;
import de.peass.dependencyprocessors.VersionComparator;
import de.peass.vcs.GitCommit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(description = "Reads the dependencies using parallel threads", name = "readDependencies")
public class DependencyReadingParallelStarter implements Callable<Void> {

   private static final Logger LOG = LogManager.getLogger(DependencyReadingParallelStarter.class);
   
   @Mixin
   private DependencyReaderConfigMixin config;

   public static void main(final String[] args) {
      try {
         final CommandLine commandLine = new CommandLine(new DependencyReadingParallelStarter());
         commandLine.execute(args);
      } catch (final Throwable t) {
         t.printStackTrace();
      }
   }  
   
   @Override
   public Void call() throws Exception {
      final List<GitCommit> commits = CommitUtil.getGitCommits(config.getStartversion(), config.getEndversion(), config.getProjectFolder());
      VersionComparator.setVersions(commits);
      
      final DependencyParallelReader reader = new DependencyParallelReader(config.getProjectFolder(), config.getResultBaseFolder(), config.getProjectFolder().getName(), commits, 
            config.getDependencyConfig(), config.getTimeout(), config.getExecutionConfig(), new EnvironmentVariables());
      final File[] outFiles = reader.readDependencies();

      LOG.debug("Files: {}", outFiles);

      final File out = new File(config.getResultBaseFolder(), "deps_" + config.getProjectFolder().getName() + ".json");
      final Dependencies all = PartialDependenciesMerger.mergeVersions(out, outFiles);
      return null;
   }

}