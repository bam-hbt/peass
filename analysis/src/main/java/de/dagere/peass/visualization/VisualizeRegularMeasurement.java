package de.dagere.peass.visualization;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.dagere.kopeme.datastorage.JSONDataLoader;
import de.dagere.kopeme.kopemedata.Kopemedata;
import de.dagere.kopeme.kopemedata.TestMethod;
import de.dagere.kopeme.kopemedata.VMResultChunk;
import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.measurement.dataloading.KoPeMeDataHelper;
import de.dagere.peass.measurement.rca.CauseSearcherConfig;
import de.dagere.peass.measurement.rca.data.CauseSearchData;
import de.dagere.peass.visualization.html.HTMLWriter;


public class VisualizeRegularMeasurement {
   
   private static final Logger LOG = LogManager.getLogger(VisualizeRegularMeasurement.class);

   private final File resultFolder;

   public VisualizeRegularMeasurement(final File resultFolder) {
      this.resultFolder = resultFolder;
   }

   public void analyzeFile(final File peassFolder) throws  JsonProcessingException, FileNotFoundException, IOException {
      PeassFolders folders = new PeassFolders(peassFolder);
      for (File kopemeFile : folders.getFullMeasurementFolder().listFiles((FilenameFilter) new WildcardFileFilter("*xml"))) {
         LOG.debug("Visualizing: {}", kopemeFile);
         Kopemedata data = JSONDataLoader.loadData(kopemeFile);
         for (TestMethod test : data.getMethods()) {
            for (VMResultChunk chunk : test.getDatacollectorResults().get(0).getChunks()) {
               List<String> commits = KoPeMeDataHelper.getCommitList(chunk);
               TestMethodCall testcase = new TestMethodCall(data.getClazz(), test.getMethod());
               for (String commit : commits) {
                  KoPeMeTreeConverter koPeMeTreeConverter = new KoPeMeTreeConverter(folders, commit, testcase);
                  GraphNode node = koPeMeTreeConverter.getData();
                  if (node != null) {
                     visualizeNode(commits, testcase, node);
                  }
               }
            }
         }
      }
   }

   private void visualizeNode(final List<String> commits, final TestMethodCall testcase, final GraphNode node) throws IOException, JsonProcessingException, FileNotFoundException {
      File destFolder = new File(resultFolder, commits.get(0));
      GraphNode emptyNode = new GraphNode(testcase.getExecutable(), "void " + testcase.getExecutable().replace("#", ".") + "()", CauseSearchData.ADDED);
      emptyNode.setName(testcase.getExecutable());
      CauseSearchData data2 = createEmptyData(commits, testcase);
      HTMLWriter htmlWriter = new HTMLWriter(emptyNode, data2, destFolder, null, node);
      htmlWriter.writeHTML();
   }

   private CauseSearchData createEmptyData(final List<String> commits, final TestMethodCall testcase) {
      CauseSearchData data2 = new CauseSearchData();
      data2.setCauseConfig(new CauseSearcherConfig(testcase, false, 1.0, false, false, null, 1));
      data2.setConfig(new MeasurementConfig(2, commits.get(0), commits.get(1)));
      return data2;
   }

   

}
