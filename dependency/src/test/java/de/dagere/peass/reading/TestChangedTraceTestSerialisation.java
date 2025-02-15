package de.dagere.peass.reading;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.analysis.testData.TestClazzCall;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependencyprocessors.VersionComparator;
import de.dagere.peass.utils.Constants;

public class TestChangedTraceTestSerialisation {

   @BeforeEach
   public void initializeComparator() {
      VersionComparator.setVersions(Arrays.asList("v1", "v2"));
   }

   @Test
   public void testModule() throws IOException {
      final ExecutionData allVersionsTests = new ExecutionData();
      final TestSet testSet = new TestSet();
      testSet.addTest(new TestMethodCall("MyClazz", "myMethod", "module-3-bla"));
      allVersionsTests.addCall("v1", testSet);

      final String serialized = Constants.OBJECTMAPPER.writeValueAsString(allVersionsTests);
      System.out.println(serialized);

      final ExecutionData deserialized = Constants.OBJECTMAPPER.readValue(serialized, ExecutionData.class);
      final TestSet testSetDeserialized = deserialized.getCommits().get("v1");
      Assert.assertNotNull(testSetDeserialized);

      final TestMethodCall testcaseDeserialized = testSetDeserialized.getTestMethods().iterator().next();
      Assert.assertEquals("MyClazz", testcaseDeserialized.getClazz());
   }

   @Test
   public void testVersionContent() throws IOException {
      final ExecutionData tests = new ExecutionData();
      tests.addEmptyCommit("v1", "v0");
      tests.addCall("v1", new TestMethodCall("Test1", "test"));
      tests.addEmptyCommit("v2", "v1");
      tests.addCall("v2", new TestMethodCall("Test1", "test"));

      final ObjectMapper mapper = Constants.OBJECTMAPPER;
      final String json = mapper.writeValueAsString(tests);

      Assert.assertNotNull(json);
      System.out.println(json);

      final ExecutionData deserialize = mapper.readValue(json, ExecutionData.class);

      Assert.assertNotNull(deserialize);
      Map<TestClazzCall, Set<String>> regularV1 = tests.getCommits().get("v1").getTestcases();
      Map<TestClazzCall, Set<String>> deserializedV1 = deserialize.getCommits().get("v1").getTestcases();
      Assert.assertEquals(regularV1, deserializedV1);
   }
}
