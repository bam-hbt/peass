package de.peass.measurement.rca.treeanalysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Test;

import de.dagere.peass.measurement.rca.data.CallTreeNode;
import de.dagere.peass.measurement.rca.treeanalysis.LevelDifferentNodeDeterminer;
import de.peass.dependencyprocessors.ViewNotFoundException;
import de.peass.measurement.rca.helper.TestConstants;
import de.peass.measurement.rca.helper.TreeBuilder;
import kieker.analysis.exception.AnalysisConfigurationException;

public class TestLevelDifferentNodeDeterminer {
   @Test
   public void testLevelDifferingDeterminer() throws Exception {
      final TreeBuilder predecessorBuilder = new TreeBuilder();
      predecessorBuilder.addDE();
      final CallTreeNode rootPredecessor = predecessorBuilder.getRoot();
      final CallTreeNode root = new TreeBuilder().getRoot();
      
      root.setOtherVersionNode(rootPredecessor);
      rootPredecessor.setOtherVersionNode(root);
      predecessorBuilder.buildMeasurements(rootPredecessor);

      final LevelDifferentNodeDeterminer determiner = getDiff(rootPredecessor, root);
      Assert.assertEquals(3, determiner.getMeasureNextLevel().size());
      
      final LevelDifferentNodeDeterminer determinerD = getDiff(predecessorBuilder.getB(), predecessorBuilder.getB());
      Assert.assertEquals(1, determinerD.getCurrentLevelDifferent().size());
   }

   private LevelDifferentNodeDeterminer getDiff(final CallTreeNode rootOld, final CallTreeNode rootMain)
         throws IOException, XmlPullParserException, InterruptedException, ViewNotFoundException, AnalysisConfigurationException, JAXBException {
      final List<CallTreeNode> currentPredecessorNodeList = Arrays.asList(new CallTreeNode[] { rootOld });
      final List<CallTreeNode> currentVersionNodeList = Arrays.asList(new CallTreeNode[] { rootMain });
      final LevelDifferentNodeDeterminer determiner = new LevelDifferentNodeDeterminer(currentPredecessorNodeList, currentVersionNodeList,
            TestConstants.SIMPLE_CAUSE_CONFIG, TestConstants.SIMPLE_MEASUREMENT_CONFIG);
      determiner.calculateDiffering();
      return determiner;
   }
}
