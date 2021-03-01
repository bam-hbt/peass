package de.peass.dependency.execution;

import de.peass.config.MeasurementStrategy;
import net.kieker.sourceinstrumentation.AllowedKiekerRecord;
import picocli.CommandLine.Option;

public class MeasurementConfigurationMixin {
   @Option(names = { "-vms", "--vms" }, description = "Number of VMs to start")
   int vms = 100;

   @Option(names = { "-duration", "--duration" }, description = "Which duration to use - if duration is specified, warmup and iterations are ignored")
   int duration = 0;

   @Option(names = { "-warmup", "--warmup" }, description = "Number of warmup iterations")
   int warmup = 10;

   @Option(names = { "-iterations", "--iterations" }, description = "Number of iterations")
   int iterations = 1000;

   @Option(names = { "-repetitions", "--repetitions" }, description = "Last version that should be analysed")
   int repetitions = 100;

   @Option(names = { "-useKieker", "--useKieker", "-usekieker", "--usekieker" }, description = "Whether Kieker should be used")
   boolean useKieker = false;

   @Option(names = { "-useGC", "--useGC" }, description = "Do execute GC before each iteration (default false)")
   public boolean useGC = false;

   @Option(names = { "-earlyStop", "--earlyStop" }, description = "Whether to stop early (i.e. execute VMs until type 1 and type 2 error are met)")
   protected boolean earlyStop = false;
   
   @Option(names = { "-removeSnapshots", "--removeSnapshots" }, description = "Activates removing SNAPSHOTS (if older versions should be analysed, this should be activated; for performance measurement in CI, this should not be activated)")
   protected boolean removeSnapshots = false;
   
   @Option(names = { "-type1error",
         "--type1error" }, description = "Type 1 error of agnostic-t-test, i.e. probability of considering measurements equal when they are unequal (requires earlyStop)")
   public double type1error = 0.05;

   @Option(names = { "-type2error",
         "--type2error" }, description = "Type 2 error of agnostic-t-test, i.e. probability of considering measurements unequal when they are equal (requires earlyStop)")
   protected double type2error = 0.01;

   @Option(names = { "-timeout", "--timeout" }, description = "Timeout in minutes for each VM start")
   protected int timeout = 5;

   @Option(names = { "-record", "--record" }, description = "Kieker Record type to use for monitoring ")
   protected AllowedKiekerRecord record;
   
   @Option(names = { "-testGoal", "--testGoal" }, description = "Test goal that should be used; default testRelease for Android projects and test for all others. "
         + "If you want to use test<VariantName> for Android, please specify a goal (i.e. task name) here."
         + "If you want to run integration tests in maven e.g. by calling failsafe, also specify it here. ")
   protected String testGoal;
   
   @Option(names = { "-measurementStrategy", "--measurementStrategy" }, description = "Measurement strategy (Default: SEQUENTIAL) ")
   protected MeasurementStrategy measurementStrategy = MeasurementStrategy.SEQUENTIAL;

   @Option(names = { "-includes", "--includes" }, description = "Testcases for inclusion (default: empty, includes all tests)")
   protected String[] includes;

   public int getVms() {
      return vms;
   }

   public int getDuration() {
      return duration;
   }

   public int getWarmup() {
      return warmup;
   }

   public int getIterations() {
      return iterations;
   }

   public int getRepetitions() {
      return repetitions;
   }

   public boolean isUseKieker() {
      return useKieker;
   }

   public boolean isUseGC() {
      return useGC;
   }

   public boolean isEarlyStop() {
      return earlyStop;
   }

   public double getType1error() {
      return type1error;
   }

   public double getType2error() {
      return type2error;
   }

   public int getTimeout() {
      return timeout;
   }

   public void setVms(final int vms) {
      this.vms = vms;
   }

   public void setDuration(final int duration) {
      this.duration = duration;
   }

   public void setWarmup(final int warmup) {
      this.warmup = warmup;
   }

   public void setIterations(final int iterations) {
      this.iterations = iterations;
   }

   public void setRepetitions(final int repetitions) {
      this.repetitions = repetitions;
   }

   public void setUseKieker(final boolean useKieker) {
      this.useKieker = useKieker;
   }

   public void setUseGC(final boolean useGC) {
      this.useGC = useGC;
   }

   public void setEarlyStop(final boolean earlyStop) {
      this.earlyStop = earlyStop;
   }

   public void setType1error(final double type1error) {
      this.type1error = type1error;
   }

   public void setType2error(final double type2error) {
      this.type2error = type2error;
   }

   public void setTimeout(final int timeout) {
      this.timeout = timeout;
   }

   public AllowedKiekerRecord getRecord() {
      return record;
   }

   public void setRecord(final AllowedKiekerRecord record) {
      this.record = record;
   }

   public void setTestGoal(final String testGoal) {
      this.testGoal = testGoal;
   }
   
   public String getTestGoal() {
      return testGoal;
   }
   
   public void setMeasurementStrategy(final MeasurementStrategy measurementStrategy) {
      this.measurementStrategy = measurementStrategy;
   }
   
   public MeasurementStrategy getMeasurementStrategy() {
      return measurementStrategy;
   }
   
   public void setIncludes(final String[] includes) {
      this.includes = includes;
   }
   
   public String[] getIncludes() {
      return includes;
   }
}
