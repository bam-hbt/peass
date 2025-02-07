package de.dagere.peass.measurement.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.AggregateSummaryStatistics;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.kopeme.kopemedata.Fulldata;
import de.dagere.kopeme.kopemedata.MeasuredValue;
import de.dagere.kopeme.kopemedata.VMResult;
import de.dagere.peass.config.StatisticsConfig;
import de.dagere.peass.measurement.statistics.bimodal.BimodalityTester;
import de.dagere.peass.measurement.statistics.bimodal.CompareData;

public class StatisticUtil {

   private static final Logger LOG = LogManager.getLogger(StatisticUtil.class);

   public static double getMean(final List<StatisticalSummary> statistics) {
      final StatisticalSummaryValues vals = AggregateSummaryStatistics.aggregate(statistics);
      return vals != null ? vals.getMean() : Double.NaN;
   }

   private static Relation bimodalTTest(final CompareData data, final double type1error) {
      final BimodalityTester tester = new BimodalityTester(data);
      if (tester.isTChange(type1error)) {
         return tester.getRelation();
      } else {
         return Relation.EQUAL;
      }
   }

   public static boolean isBimodal(final List<VMResult> valuesPredecessor, final List<VMResult> valuesCurrent) {
      CompareData data = new CompareData(valuesPredecessor, valuesCurrent);
      final BimodalityTester tester = new BimodalityTester(data);
      return tester.isBimodal();
   }

   /**
    * Agnostic T-Test from Coscrato et al.: Agnostic tests can control the type I and type II errors simultaneously
    */
   public static Relation agnosticTTest(final StatisticalSummary statisticsPredecessor, final StatisticalSummary statisticsCurrent, final double type1error, final double type2error) {
      final double tValue = getTValue(statisticsPredecessor, statisticsCurrent, 0);
      System.out.println(tValue);
      return agnosticTTest(tValue, statisticsPredecessor.getN() + statisticsCurrent.getN() - 2, type1error, type2error);
   }

   public static Relation agnosticTTest(final double tValue, final long degreesOfFreedom, final double type1error, final double type2error) {
      final double criticalValueEqual = getCriticalValueEqual(type2error, degreesOfFreedom);
      final double criticalValueUnequal = getCriticalValueUnequal(type1error, degreesOfFreedom);

      LOG.debug("Allowed errors: {} {}", type1error, type2error);
      LOG.debug("Critical values: {} {}", criticalValueUnequal, criticalValueEqual);

      LOG.debug("T: {}", tValue);

      if (Math.abs(tValue) > criticalValueUnequal) {
         return Relation.UNEQUAL;
      } else if (Math.abs(tValue) < criticalValueEqual) {
         return Relation.EQUAL;
      } else {
         return Relation.UNKOWN;
      }
   }

   /**
    * Gets the critical t value for regular t-test based on the type-1-error (=probability of false positive).
    * 
    * Required for peass-ci, please do not remove!
    * 
    * @param type1error The probability that a false positive is reported
    * @param degreesOfFreedom The degrees of freedom
    * @return The critical t value, if the calculated t value is above the critical t-tvalue, it is considered to be a significant performance change
    */
   public static double getCriticalValueTTest(final double type1error, final long degreesOfFreedom) {
      return getCriticalValueUnequal(type1error, degreesOfFreedom);
   }

   public static double getCriticalValueUnequal(final double type2error, final long degreesOfFreedom) {
      final TDistribution tDistribution = new TDistribution(null, degreesOfFreedom);
      final double criticalValueUnequal = Math.abs(tDistribution.inverseCumulativeProbability(1. - 0.5 * type2error));
      return criticalValueUnequal;
   }

   public static double getCriticalValueEqual(final double type1error, final long degreesOfFreedom) {
      final TDistribution tDistribution = new TDistribution(null, degreesOfFreedom);
      final double criticalValueEqual = Math.abs(tDistribution.inverseCumulativeProbability(0.5 * (1 + type1error)));
      return criticalValueEqual;
   }

   public static double getTValue(final StatisticalSummary statisticsCurrent, final StatisticalSummary statisticsPredecessor, final double omega) {
      final double n = statisticsCurrent.getN();
      final double m = statisticsPredecessor.getN();
      final double sizeFactor = Math.sqrt(m * n / (m + n));
      final double upperPart = (m - 1) * Math.pow(statisticsPredecessor.getStandardDeviation(), 2) + (n - 1) * Math.pow(statisticsCurrent.getStandardDeviation(), 2);
      final double s = Math.sqrt(upperPart / (m + n - 2));
      final double difference = (statisticsCurrent.getMean() - statisticsPredecessor.getMean() - omega);
      final double tAlternative = sizeFactor * difference / s;
      return tAlternative;
   }

   /**
    * Determines whether there is a change, no change or no decission can be made. Usually, first the predecessor and than the current commit are given.
    * 
    * @param statisticsPredecessor
    * @param statisticsCurrent
    * @param measurementConfig
    * @return
    */
   public static Relation agnosticTTest(final StatisticalSummary statisticsPredecessor, final StatisticalSummary statisticsCurrent, final StatisticsConfig statisticsConfig) {
      return agnosticTTest(statisticsPredecessor, statisticsCurrent, statisticsConfig.getType1error(), statisticsConfig.getType2error());
   }

   public static Relation isChange(final StatisticalSummary statisticsPrev, final StatisticalSummary statisticsVersion, final StatisticsConfig statisticsConfig) {
      final double maxVal = Math.max(statisticsPrev.getMean(), statisticsVersion.getMean());
      if (maxVal > 1) {
         return agnosticTTest(statisticsPrev, statisticsVersion, statisticsConfig);
         // } else if (maxVal > 1) {
         // final Relation r1 = isConfidenceIntervalOverlap(statisticsPrev, statisticsVersion, measurementConfig.getType2error());
         // final Relation r2 = agnosticTTest(statisticsPrev, statisticsVersion, measurementConfig.getType1error() / 10, measurementConfig.getType2error() / 10);
         // final Relation result = (r1 == r2) ? r2 : Relation.UNKOWN;
         // return result;
      } else {
         Relation r1 = agnosticTTest(statisticsPrev, statisticsVersion, statisticsConfig.getType1error() / 20, statisticsConfig.getType2error() / 20);
         final double tValue = getTValue(statisticsPrev, statisticsVersion, 0);
         if (Math.abs(tValue) < 10) {
            r1 = Relation.UNKOWN;
         }
         return r1;
      }
   }

   public static VMResult shortenResult(final VMResult result, final int start, final int end) {
      final VMResult resultShort = copyResultBasics(result);
      final DescriptiveStatistics statistics = new DescriptiveStatistics();
      // LOG.debug("Size: " + result.getFulldata().getValue().size());
      final int size = (Math.min(end, result.getFulldata().getValues().size()));
      if (start > size) {
         throw new RuntimeException("Start (" + start + ") is after end of data (" + size + ").");
      }
      if (end > size) {
         throw new RuntimeException("End (" + end + ") is after end of data (" + size + ").");
      }
      // LOG.debug("Size: {}", j);
      for (int i = start; i < size; i++) {
         final MeasuredValue value = result.getFulldata().getValues().get(i);
         final Fulldata fulldata = resultShort.getFulldata();
         fulldata.getValues().add(value);
         statistics.addValue(value.getValue());
      }
      resultShort.setValue(statistics.getMean());
      resultShort.setDeviation(statistics.getStandardDeviation());
      resultShort.setIterations(end - start);
      resultShort.setWarmup(start);
      resultShort.setRepetitions(result.getRepetitions());
      resultShort.setParameters(result.getParameters());
      return resultShort;
   }

   private static VMResult copyResultBasics(final VMResult result) {
      final VMResult resultShort = new VMResult();
      resultShort.setCpu(result.getCpu());
      resultShort.setDate(result.getDate());
      resultShort.setMemory(result.getMemory());
      resultShort.setFulldata(new Fulldata());
      return resultShort;
   }

   public static VMResult shortenResult(final VMResult result) {
      final int start = result.getFulldata().getValues().size() / 2;
      final int end = result.getFulldata().getValues().size();
      final VMResult resultShort = shortenResult(result, start, end);
      return resultShort;
   }

   public static List<VMResult> shortenValues(final List<VMResult> values, final int start, final int end) {
      final List<VMResult> shortenedValues = new ArrayList<>(values.size());
      int index = 0;
      for (final VMResult result : values) {
         index++;
         try {
            final VMResult resultShort = shortenResult(result, start, end);
            shortenedValues.add(resultShort);
         } catch (RuntimeException e) {
            throw new RuntimeException("Error in result " + index, e);
         }
      }
      return shortenedValues;
   }

   public static List<VMResult> shortenValues(final List<VMResult> values) {
      final List<VMResult> shortenedValues = new ArrayList<>(values.size());
      for (final VMResult result : values) {
         final VMResult resultShort = StatisticUtil.shortenResult(result);
         shortenedValues.add(resultShort);
      }
      return shortenedValues;
   }

   public static Relation getTTestRelation(final CompareData cd, final double type1error) {
      final boolean tchange = new TTest().homoscedasticTTest(cd.getPredecessor(), cd.getCurrent(), type1error);
      if (tchange) {
         return cd.getAvgPredecessor() < cd.getAvgCurrent() ? Relation.LESS_THAN : Relation.GREATER_THAN;
      } else {
         return Relation.EQUAL;
      }
   }

   public static Relation getMannWhitneyRelation(final CompareData cd, final double type1error) {
      final double statistic = new MannWhitneyUTest().mannWhitneyUTest(cd.getPredecessor(), cd.getCurrent());
      LOG.trace("Mann-Whitney-Statistic: {}", statistic);
      final boolean mannchange = statistic < type1error;
      if (mannchange) {
         return cd.getAvgPredecessor() < cd.getAvgCurrent() ? Relation.LESS_THAN : Relation.GREATER_THAN;
      } else {
         return Relation.EQUAL;
      }
   }

   public static Relation isDifferent(final List<VMResult> valuesPredecessor, final List<VMResult> valuesCommit, final StatisticsConfig statisticsConfig) {
      CompareData data = new CompareData(valuesPredecessor, valuesCommit);
      return isDifferent(data, statisticsConfig);
   }

   public static Relation isDifferent(final CompareData cd, final StatisticsConfig statisticsConfig) {
      switch (statisticsConfig.getStatisticTest()) {
      case AGNOSTIC_T_TEST:
         return agnosticTTest(cd.getPredecessorStat(), cd.getCurrentStat(), statisticsConfig);
      case BIMODAL_T_TEST:
         return bimodalTTest(cd, statisticsConfig.getType1error());
      case T_TEST:
         return getTTestRelation(cd, statisticsConfig.getType1error());
      case MANN_WHITNEY_TEST:
         return getMannWhitneyRelation(cd, statisticsConfig.getType1error());
      case CONFIDENCE_INTERVAL:
         return ConfidenceIntervalInterpretion.compare(cd, statisticsConfig.getType1error());
      case ANY:
         boolean isChange = agnosticTTest(cd.getPredecessorStat(), cd.getCurrentStat(), statisticsConfig) != Relation.EQUAL
               || bimodalTTest(cd, statisticsConfig.getType1error()) != Relation.EQUAL
               || getTTestRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL
               || getMannWhitneyRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL
               || ConfidenceIntervalInterpretion.compare(cd) != Relation.EQUAL;
         LOG.info("Test results ");
         LOG.info("Agnostic t: {}", agnosticTTest(cd.getPredecessorStat(), cd.getCurrentStat(), statisticsConfig) != Relation.EQUAL);
         LOG.info("Bimodal T: {}", bimodalTTest(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("T Test: {}", getTTestRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("Mann-Whitney: {}", getMannWhitneyRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("Confidence interval: {}", ConfidenceIntervalInterpretion.compare(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("isChange: {}", isChange);
         if (isChange) {
            return cd.getAvgPredecessor() < cd.getAvgCurrent() ? Relation.LESS_THAN : Relation.GREATER_THAN;
         } else {
            return Relation.EQUAL;
         }
      case ANY_NO_AGNOSTIC:
         boolean isChangeNoAgnostic = bimodalTTest(cd, statisticsConfig.getType1error()) != Relation.EQUAL
               || getTTestRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL
               || getMannWhitneyRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL
               || ConfidenceIntervalInterpretion.compare(cd) != Relation.EQUAL;
         LOG.info("Test results ");
         LOG.info("Bimodal T: {}", bimodalTTest(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("T Test: {}", getTTestRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("Mann-Whitney: {}", getMannWhitneyRelation(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("Confidence interval: {}", ConfidenceIntervalInterpretion.compare(cd, statisticsConfig.getType1error()) != Relation.EQUAL);
         LOG.info("isChange: {}", isChangeNoAgnostic);
         if (isChangeNoAgnostic) {
            return cd.getAvgPredecessor() < cd.getAvgCurrent() ? Relation.LESS_THAN : Relation.GREATER_THAN;
         } else {
            return Relation.EQUAL;
         }
      default:
         throw new RuntimeException("Test " + statisticsConfig.getStatisticTest() + " currently not implemented");
      }
   }
   
   public static void removeWarmup(int remainingWarmup, final List<StatisticalSummary> statistic) {
      if (remainingWarmup > 0) {
         for (ListIterator<StatisticalSummary> it = statistic.listIterator(); it.hasNext();) {
            StatisticalSummary chunk = it.next();
            long countOfExecutions = chunk.getN();
            if (remainingWarmup >= countOfExecutions) {
               remainingWarmup -= countOfExecutions;
               LOG.debug("Reducing warmup by {}, remaining warmup {}", countOfExecutions, remainingWarmup);
               it.remove();
            } else if (remainingWarmup > 0) {
               final long reducedN = countOfExecutions - remainingWarmup;
               remainingWarmup = 0;
               final StatisticalSummary replacement = new StatisticalSummaryValues(chunk.getMean(), chunk.getVariance(), reducedN,
                     chunk.getMax(), chunk.getMin(), chunk.getMean() * reducedN);
               it.set(replacement);
            } else {
               final StatisticalSummary replacement = new StatisticalSummaryValues(chunk.getMean(), chunk.getVariance(), countOfExecutions,
                     chunk.getMax(), chunk.getMin(), chunk.getMean() * countOfExecutions);
               it.set(replacement);
            }
         }
         if (remainingWarmup > 0) {
            LOG.warn("Warning! Reading aggregated data which contain less executions than the warmup " + remainingWarmup);
         }
         for (StatisticalSummary summary : statistic) {
            LOG.trace("After removing: {} {} Sum: {}", summary.getMean(), summary.getN(), summary.getSum());
            if (summary.getN() == 0) {
               throw new RuntimeException("Final statistics with 0 entries do not make sense!");
            }
         }
         LOG.trace("Overall mean: {}", StatisticUtil.getMean(statistic));
      }
   }

   public static double getMannWhitneyUStatistic(double[] dataOld, double[] dataCurrent) {
      return new MannWhitneyUTest().mannWhitneyUTest(dataOld, dataCurrent);
   }

   public static Double getMannWhitneyUStatistic(DescriptiveStatistics statisticsOld, DescriptiveStatistics statisticsCurrent) {
      return getMannWhitneyUStatistic(statisticsOld.getValues(), statisticsCurrent.getValues());
   }
}
