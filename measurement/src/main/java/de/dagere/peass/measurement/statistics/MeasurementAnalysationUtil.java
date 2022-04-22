package de.dagere.peass.measurement.statistics;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import jakarta.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.descriptive.StorelessUnivariateStatistic;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.kopeme.datastorage.JSONDataLoader;
import de.dagere.kopeme.kopemedata.DatacollectorResult;
import de.dagere.kopeme.kopemedata.Kopemedata;
import de.dagere.kopeme.kopemedata.TestMethod;
import de.dagere.kopeme.kopemedata.VMResult;


public final class MeasurementAnalysationUtil {

   /**
    * DummyStatistic, which speeds up mean calculation, because it does nothing
    * @author reichelt
    *
    */
	private static final class DummyStatistic implements StorelessUnivariateStatistic {
      @Override
      public double evaluate(final double[] values, final int begin, final int length) throws MathIllegalArgumentException {
         return 0;
      }

      @Override
      public double evaluate(final double[] values) throws MathIllegalArgumentException {
         return 0;
      }

      @Override
      public void incrementAll(final double[] values, final int start, final int length) throws MathIllegalArgumentException {
         // TODO Auto-generated method stub
         
      }

      @Override
      public void incrementAll(final double[] values) throws MathIllegalArgumentException {
      }

      @Override
      public void increment(final double d) {
      }

      @Override
      public double getResult() {
         return 0;
      }

      @Override
      public long getN() {
         return 0;
      }

      @Override
      public StorelessUnivariateStatistic copy() {
         return null;
      }

      @Override
      public void clear() {
      }
   }

   private MeasurementAnalysationUtil() {

	}

	private static final Logger LOG = LogManager.getLogger(MeasurementAnalysationUtil.class);

	static long earliestVersion = Long.MAX_VALUE;
	static long lastVersion = Long.MIN_VALUE;

	public static final double MIN_NORMED_DISTANCE = 0.5;
	public static final double MIN_ABSOLUTE_PERCENTAGE_DISTANCE = 0.2;

	private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

	public static Map<File, Kopemedata> getData(final File file) throws JAXBException {
		final Map<File, Kopemedata> data = new HashMap<>();
		LOG.debug("Analysiere: {}", file);
		if (file.isDirectory()) {
			final Collection<File> fileList = FileUtils.listFiles(file, new WildcardFileFilter("*.json"), FalseFileFilter.INSTANCE);
			for (final File jsonFile : fileList) {
				LOG.trace("Datei: {}", jsonFile);
				final Kopemedata currentData = JSONDataLoader.loadData(jsonFile);
				data.put(jsonFile, currentData);
			}
		}
		return data;
	}

	public static List<PerformanceChange> analyzeKopemeData(final Kopemedata data) {
		final Map<String, List<VMResult>> results = new LinkedHashMap<>();
		int maxResultSize = 0;
		final TestMethod currentTestcase = data.getFirstMethodResult();
		final String clazz = data.getClazz();
		final String method = currentTestcase.getMethod();
		final List<DatacollectorResult> datacollectors = currentTestcase.getDatacollectorResults();
		if (datacollectors.size() != 1) {
			LOG.warn("Mehr als ein DataCollector bei: {}", method);
		}
		for (final VMResult result : datacollectors.get(0).getResults()) {
			final String gitversion = result.getCommit();
			if (!results.containsKey(gitversion)) {
				results.put(gitversion, new LinkedList<>());
			}
			results.get(gitversion).add(result);
			if (results.get(gitversion).size() > maxResultSize) {
				maxResultSize = results.get(gitversion).size();
			}
		}

		ConfidenceInterval previous = null;
		String previousVersion = null;
		final List<PerformanceChange> changes = new LinkedList<>();

		final ExecutorService service = Executors.newFixedThreadPool(4);

		for (final Map.Entry<String, List<VMResult>> entry : results.entrySet()) {
			final double[] values = getAveragesArrayFromResults(entry.getValue());
			final ConfidenceInterval interval = getBootstrapConfidenceInterval(values, 20, 1000, 96);
			LOG.trace("{}-Konfidenzintervall: {} - {}", interval.getPercentage(), interval.getMin(), interval.getMax());
			if (previous != null) {
				final ConfidenceInterval previousConfidenceInterval = previous;
				final String previousVersion2 = previousVersion;
				LOG.trace("Start " + previousVersion2);
				service.execute(new Runnable() {
					@Override
					public void run() {
						final String currentVersion = entry.getValue().get(0).getCommit();
						final PerformanceChange change = new PerformanceChange(previousConfidenceInterval, interval, clazz, method, previousVersion2, currentVersion);
						final boolean isChange = analysePotentialChange(change, previousConfidenceInterval, entry, interval);
						if (isChange) {
							changes.add(change);
						}
					}
				});
			}
			previous = interval;
			previousVersion = entry.getKey();
		}
		try {
			service.shutdown();
			service.awaitTermination(1, TimeUnit.DAYS);
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		return changes;
	}

	public static double[] getAveragesArrayFromResults(final List<VMResult> results) {
		final double[] values = new double[results.size()];
		int i = 0;
		for (final VMResult result : results) {
			final double value = result.getValue();
			values[i++] = value;
		}
		return values;
	}

	private static boolean analysePotentialChange(final PerformanceChange change, final ConfidenceInterval previous, final Map.Entry<String, List<VMResult>> entry, final ConfidenceInterval interval) {
		LOG.trace("Vergleiche: {} {} Version: {}", change.getTestClass(), change.getTestMethod(), change.getRevisionOld());
		boolean isChange = false;
		final double diff = change.getDifference();
		LOG.debug("Teste: {}:{} - {} vs. vorher {}", change.getRevision(), change.getRevisionOld(), interval, previous);
		if (interval.getMax() < previous.getMin()) {
			if (change.getNormedDifference() > MIN_NORMED_DISTANCE && diff > MIN_ABSOLUTE_PERCENTAGE_DISTANCE * previous.getMax()) {
				LOG.debug("Änderung: {} {} Diff: {}", change.getRevisionOld(), change.getTestMethod(), diff);
				LOG.debug("Ist kleiner geworden: {} vs. vorher {}", interval, previous);
				LOG.trace("Abstand: {} Versionen: {}:{}", diff, change.getRevisionOld(), entry.getKey());
				isChange = true;
			}
		}
		if (interval.getMin() > previous.getMax()) {
			if (change.getNormedDifference() > MIN_NORMED_DISTANCE && diff > MIN_ABSOLUTE_PERCENTAGE_DISTANCE * previous.getMax()) {
				LOG.debug("Änderung: {} {} Diff: {}", change.getRevisionOld(), change.getTestMethod(), diff);
				LOG.debug("Ist größer geworden: {} vs. vorher {}", interval, previous);
				LOG.trace("Abstand: {} Versionen: {}:{}", diff, change.getRevisionOld(), entry.getKey());
				isChange = true;
			}
		}
		return isChange;
	}

	public static ConfidenceInterval getBootstrapConfidenceInterval(final double[] values, final int count, final double[] repetitionValues, final int intervalPercentage) {
      LOG.trace("Werte: {}", values);
      final double[] means = getMeanStatistics(values, count, repetitionValues);

//    LOG.trace("Mean: {}", statistics.getMean());

      final double upperBound = new Percentile(intervalPercentage).evaluate(means);
      final double lowerBound = new Percentile(100 - intervalPercentage).evaluate(means);;
      return new ConfidenceInterval(lowerBound, upperBound, intervalPercentage);
   } 
	
	public static ConfidenceInterval getBootstrapConfidenceInterval(final double[] values, final int count, final int repetitions, final int intervalPercentage) {
		LOG.trace("Werte: {}", values);
		final double[] means = getMeanStatistics(values, count, new double[repetitions]);

//		LOG.trace("Mean: {}", statistics.getMean());

		final double upperBound = new Percentile(intervalPercentage).evaluate(means);
		final double lowerBound = new Percentile(100 - intervalPercentage).evaluate(means);;
		return new ConfidenceInterval(lowerBound, upperBound, intervalPercentage);
	}

   private static double[] getMeanStatistics(final double[] values, final int count, final double[] meanValues) {
		for (int i = 0; i < meanValues.length; i++) {
			final double bootstrapMean = getBootstrappedStatistics(values, count);
			meanValues[i] = bootstrapMean;
		}
      return meanValues;
   }
   
   private static double getBootstrappedStatistics(final double[] values, final int count) {
      final SummaryStatistics st = new SummaryStatistics();
      st.setSumLogImpl(new DummyStatistic());
      for (int i = 0; i < count; i++) {
         final int nextInt = RANDOM.nextInt(values.length);
         // System.out.println(nextInt);
         st.addValue(values[nextInt]);
      }
      return st.getMean();
   } 

//   private static double getBootstrappedStatistics(final double[] values, final int count) {
//      final double[] tempValues = getBootstrapValues(values, count);
//      final SummaryStatistics st = new SummaryStatistics();
//      st.setSumLogImpl(new DummyStatistic());
//      for (double value : tempValues) {
//         st.addValue(value);
//      }
//      return st.getMean();
//   }

//	public static double[] getBootstrapValues(final double[] values, final int count) {
//		final double[] result = new double[count];
//		for (int i = 0; i < count; i++) {
//			final int nextInt = RANDOM.nextInt(values.length);
//			// System.out.println(nextInt);
//			result[i] = values[nextInt];
//		}
//		return result;
//	}
}
