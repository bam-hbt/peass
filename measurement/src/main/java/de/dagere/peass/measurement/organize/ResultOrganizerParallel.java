package de.dagere.peass.measurement.organize;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.folders.PeassFolders;

public class ResultOrganizerParallel extends ResultOrganizer {
   
   private static final Logger LOG = LogManager.getLogger(ResultOrganizerParallel.class);

   private final Map<String, PeassFolders> sourceFolders = new HashMap<>();
   
   public ResultOrganizerParallel(final PeassFolders folders, final String currentVersion, final long currentChunkStart, final boolean isUseKieker, final boolean saveAll, final TestMethodCall test,
         final int expectedIterations) {
      super(folders, currentVersion, currentChunkStart, isUseKieker, saveAll, test, expectedIterations);
      LOG.trace("Creating new ResultOrganizerParallel");
      LOG.trace("Instance: {}", System.identityHashCode(this));
   }

   public void addCommitFolders(final String commit, final PeassFolders commitTempFolder) {
      LOG.debug("Adding commit: {}", commit);
      sourceFolders.put(commit, commitTempFolder);
      LOG.trace("Instance: {} Keys: {}", System.identityHashCode(this), sourceFolders.keySet());
   }
   
   @Override
   public File getTempResultsFolder(final String commit) {
      PeassFolders currentFolders = sourceFolders.get(commit);
      LOG.info("Searching method: {} Version: {} Existing commits: {}", testcase, commit, sourceFolders.keySet());
      LOG.info("Instance: " + System.identityHashCode(this));
      final Collection<File> folderCandidates = currentFolders.findTempClazzFolder(testcase);
      if (folderCandidates.size() != 1) {
         LOG.error("Folder with name {} is existing {} times.", testcase.getClazz(), folderCandidates.size());
         return null;
      } else {
         final File folder = folderCandidates.iterator().next();
         return folder;
      }
   }
}
