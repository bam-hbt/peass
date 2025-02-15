package de.dagere.peass.analysis.changes;

import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.analysis.testData.TestClazzCall;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;

/**
 * Saves all changes for one commit. For each testcase it is saved which change has happened with method, difference in percent etc.
 * 
 * @author reichelt
 *
 */
public class Changes implements Serializable {

   private static final long serialVersionUID = -7339774896217980704L;

   private Map<String, List<Change>> testcaseChanges = new TreeMap<>();

   public Map<String, List<Change>> getTestcaseChanges() {
      return testcaseChanges;
   }

   public void setTestcaseChanges(final Map<String, List<Change>> testcaseChanges) {
      this.testcaseChanges = testcaseChanges;
   }

   @JsonIgnore
   public Map<TestClazzCall, List<Change>> getTestcaseObjectChanges() {
      Map<TestClazzCall, List<Change>> resultChanges = new LinkedHashMap<>();
      for (Entry<String, List<Change>> testcaseEntry : testcaseChanges.entrySet()) {
         TestClazzCall test = TestClazzCall.createFromString(testcaseEntry.getKey());
         resultChanges.put(test, testcaseEntry.getValue());
      }
      return resultChanges;
   }

   /**
    * Adds a change
    * 
    * @param testcase Testcase that has changes
    * @param viewName view-file where trace-diff should be saved
    * @param method Testmethod where performance changed
    * @param percent How much the performance was changed
    * @param mannWhitheyUStatistic 
    * @return Added Change
    */
   public Change addChange(final TestMethodCall testcase, final String viewName, final double oldTime, final double percent, final double tvalue, Double mannWhitheyUStatistic, final long vms) {
      Change change = new Change();
      change.setDiff(viewName);
      change.setTvalue(tvalue);
      change.setMannWhitneyUStatistic(mannWhitheyUStatistic);
      change.setOldTime(oldTime);
      change.setChangePercent(percent);
      change.setVms(vms);
      change.setMethod(testcase.getMethod());
      change.setParams(testcase.getParams());
      String clazz = testcase.getTestclazzWithModuleName();
      addChange(clazz, change);
      return change;
   }

   public Change getChange(final TestMethodCall test) {
      List<Change> changes = testcaseChanges.get(test.getClassWithModule());
      if (changes != null) {
         for (Change candidate : changes) {
            String candidateMethod = candidate.getMethod();
            String testMethod = test.getMethod();
            if (candidateMethod.equals(testMethod)) {
               return candidate;
            }
         }
      }
      return null;
   }

   public void addChange(final String testclazz, final Change change) {
      if (change == null) {
         throw new RuntimeException("Change should not be null! Testclass: " + testclazz);
      }
      List<Change> currentChanges = testcaseChanges.get(testclazz);
      if (currentChanges == null) {
         currentChanges = new LinkedList<>();
         testcaseChanges.put(testclazz, currentChanges);
      }
      for (Change existingChange : currentChanges) {
         if (existingChange.getMethodWithParams().equals(change.getMethodWithParams())) {
            if (existingChange.getTvalue() * change.getTvalue() < 0) {
               throw new RuntimeException("Test method was measured twice: " + existingChange.getMethodWithParams()
                     + " and t-value sign was differing: " + existingChange.getTvalue() + " vs " + change.getTvalue());
            }
         }
      }

      currentChanges.add(change);

      currentChanges.sort(new Comparator<Change>() {
         @Override
         public int compare(final Change o1, final Change o2) {
            return o1.getDiff().compareTo(o2.getDiff());
         }
      });
   }

   @JsonIgnore
   public TestSet getTests() {
      TestSet result = new TestSet();
      for (Entry<String, List<Change>> testclazz : testcaseChanges.entrySet()) {
         String clazzname = testclazz.getKey();
         for (Change method : testclazz.getValue()) {
            String methodName = method.getMethod();
            TestMethodCall testcase = TestMethodCall.createFromClassString(clazzname, methodName);
            result.addTest(testcase);
         }
      }
      return result;
   }
}