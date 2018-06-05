package de.peran.dependencyprocessors;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.kopeme.datacollection.DataCollectorList;
import de.dagere.kopeme.datastorage.XMLDataLoader;
import de.dagere.kopeme.datastorage.XMLDataStorer;
import de.dagere.kopeme.generated.Kopemedata;
import de.dagere.kopeme.generated.TestcaseType;
import de.peran.dependency.PeASSFolders;
import de.peran.dependency.analysis.data.ChangedEntity;
import de.peran.dependency.analysis.data.TestCase;
import de.peran.dependency.analysis.data.TestSet;
import de.peran.dependency.execution.MavenKiekerTestExecutor;
import de.peran.generated.Versiondependencies;
import de.peran.generated.Versiondependencies.Initialversion.Initialdependency;
import de.peran.generated.Versiondependencies.Versions.Version;
import de.peran.generated.Versiondependencies.Versions.Version.Dependency;
import de.peran.generated.Versiondependencies.Versions.Version.Dependency.Testcase;
import de.peran.measurement.analysis.MultipleVMTestUtil;
import de.peran.testtransformation.JUnitTestTransformer;
import de.peran.testtransformation.TimeBasedTestTransformer;
import de.peran.vcs.GitUtils;
import de.peran.vcs.SVNUtils;
import de.peran.vcs.VersionControlSystem;

/**
 * Runs a PeASS with only running the tests where a changed class is present.
 * 
 * @author reichelt
 *
 */
public class DependencyTester {

	private static final Logger LOG = LogManager.getLogger(DependencyTester.class);

	private final PeASSFolders folders;
	private final int warmup, iterations;
	private final int vms;
	private final FileWriter resultFileWriter;
	private final boolean runInitial;

	private final String url;
	private final VersionControlSystem vcs;

	final JUnitTestTransformer testgenerator;
	final MavenKiekerTestExecutor mavenKiekerExecutor;

	public DependencyTester(final PeASSFolders folders, final int duration, final int vms,
			final boolean runInitial, final int repetitions, boolean useKieker) throws IOException {
		super();
		this.folders = folders;
		this.warmup = 0;
		this.iterations = 0;
		this.vms = vms;
		this.runInitial = runInitial;
		resultFileWriter = new FileWriter(new File("fertig.txt"));

		vcs = VersionControlSystem.getVersionControlSystem(folders.getProjectFolder());
		if (vcs.equals(VersionControlSystem.SVN)) {
			url = SVNUtils.getInstance().getWCURL(folders.getProjectFolder());
			SVNUtils.getInstance().revert(folders.getProjectFolder());
		} else {
			url = null;
		}

		testgenerator = new TimeBasedTestTransformer(folders.getProjectFolder());
		((TimeBasedTestTransformer) testgenerator).setDuration(duration);
		if (repetitions != 1) {
			testgenerator.setRepetitions(150);
		}
		testgenerator.setDatacollectorlist(DataCollectorList.ONLYTIME);
		testgenerator.setIterations(iterations);
		testgenerator.setWarmupExecutions(warmup);
		testgenerator.setUseKieker(useKieker);
		testgenerator.setLogFullData(true);
		mavenKiekerExecutor = new MavenKiekerTestExecutor(folders.getProjectFolder(), folders.getTempMeasurementFolder(), testgenerator);
	}

	public DependencyTester(final PeASSFolders folders, final int warmup, final int iterations, final int vms,
			final boolean runInitial, final int repetitions, boolean useKieker) throws IOException {
		super();
		this.folders = folders;
		this.warmup = warmup;
		this.iterations = iterations;
		this.vms = vms;
		this.runInitial = runInitial;
		resultFileWriter = new FileWriter(new File("fertig.txt"));

		vcs = VersionControlSystem.getVersionControlSystem(folders.getProjectFolder());
		if (vcs.equals(VersionControlSystem.SVN)) {
			url = SVNUtils.getInstance().getWCURL(folders.getProjectFolder());
			SVNUtils.getInstance().revert(folders.getProjectFolder());
		} else {
			url = null;
		}

		testgenerator = new JUnitTestTransformer(folders.getProjectFolder());
		if (repetitions != 1) {
			testgenerator.setRepetitions(repetitions);
		}
		testgenerator.setDatacollectorlist(DataCollectorList.ONLYTIME);
		testgenerator.setIterations(iterations);
		testgenerator.setWarmupExecutions(warmup);
		testgenerator.setUseKieker(useKieker);
		testgenerator.setLogFullData(true);
		mavenKiekerExecutor = new MavenKiekerTestExecutor(folders.getProjectFolder(), folders.getTempMeasurementFolder(), testgenerator);
	}

	/**
	 * Runs the test for the given versions of the project
	 * 
	 * @param versions
	 * @param changed
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws JAXBException
	 */
	public void testProject(final Versiondependencies versions)
			throws IOException, InterruptedException, JAXBException {
		LOG.debug("Geändert: {} Versionen: {}", versions.getVersions().getVersion().size());

		if (runInitial) {
			LOG.info("Teste initiale Version");
			final TestSet initialTestSet = new TestSet();
			for (final Initialdependency dependency : versions.getInitialversion().getInitialdependency()) {
				final String testclass = dependency.getTestclass();
				final String classname = testclass.substring(0, testclass.lastIndexOf("."));
				final String methodname = testclass.substring(testclass.lastIndexOf(".") + 1);
				final ChangedEntity entity = new ChangedEntity(classname, dependency.getModule());
				initialTestSet.addTest(entity, methodname);
			}
			evaluateTestset(versions.getInitialversion().getVersion(), initialTestSet);
		} else {
			LOG.info("Überspringe initiale Version");
		}

		for (final Version version : versions.getVersions().getVersion()) {
			if (version.getDependency().size() > 0) {
				final TestSet testset = new TestSet();
				readDependencyToMap(version, testset);

				evaluateTestset(version.getVersion(), testset);
			}
		}
		resultFileWriter.close();
	}

	/**
	 * Runs the test the planned amount of counts for the given TestSet
	 * 
	 * @param testgenerator
	 * @param mavenKiekerExecutor
	 * @param version
	 * @param testset
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws JAXBException
	 */
	private void evaluateTestset(final String version, final TestSet testset)
			throws IOException, InterruptedException, JAXBException {
		File logFile = new File(folders.getLogFolder(), version);
		if (logFile.exists()) {
			logFile = new File(folders.getLogFolder(), version + "_new");
		}
		logFile.mkdir();
		for (int run = 0; run < vms; run++) {
			evaluateOnce(testset, version, run, logFile);
		}

		resultFileWriter.write(version + "\n");
		for (final Map.Entry<ChangedEntity, List<String>> entry : testset.entrySet()) {
			resultFileWriter.write(entry.getKey().getJavaClazzName() + " ");
		}
		resultFileWriter.write("\n");
		resultFileWriter.flush();
	}

	public void compareVersions(final String start, final Version end)
			throws IOException, InterruptedException, JAXBException {
		final TestSet testset = new TestSet();

		readDependencyToMap(end, testset);

		File logFile = new File(folders.getLogFolder(), start);
		if (logFile.exists()) {
			logFile = new File(folders.getLogFolder(), end + "_new");
		}
		logFile.mkdir();
		LOG.info("Teste: " + testset);
		for (int run = 0; run < vms; run++) {
			evaluateOnce(testset, start, run, logFile);
			evaluateOnce(testset, end.getVersion(), run, logFile);
		}
	}

	private static void readDependencyToMap(final Version version, final TestSet testset) {
		int count = 0;
		for (final Dependency dependency : version.getDependency()) {
			for (final Testcase testcase : dependency.getTestcase()) {
				count += testcase.getMethod().size();
				LOG.debug("Testklasse: {}", testcase.getClazz());
			}

		}
		LOG.info("Zu testende Methoden: {}", count);
		for (final Dependency dependency : version.getDependency()) {
			for (final Testcase testcase : dependency.getTestcase()) {
				for (final String method : testcase.getMethod()) {
					final ChangedEntity clazz = new ChangedEntity(testcase.getClazz(), testcase.getModule());
					testset.addTest(clazz, method);
				}
			}
		}
	}
	
	/**
    * Compares the given testcase for the given versions.
    * 
    * @param version Current version to test
    * @param versionOld Old version to test
    * @param testcase Testcase to test
    */
   public void evaluate(final String version, final String versionOld, final TestCase testcase) throws IOException, InterruptedException, JAXBException {
      LOG.info("Executing test " + testcase.getClazz() + " " + testcase.getMethod() + " in versions {} and {}", versionOld, version);

      File logFile = new File(folders.getLogFolder(), version);
      if (logFile.exists()) {
         logFile = new File(folders.getLogFolder(), version + "_new");
      }
      logFile.mkdir();

      final TestSet testset = new TestSet();
      testset.addTest(testcase);
      for (int vmid = 0; vmid < vms; vmid++) {
         evaluateOnce(testset, versionOld, vmid, logFile);
         evaluateOnce(testset, version, vmid, logFile);
      }
   }

	public void evaluateOnce(final TestSet testset, final String version, final int vmid, final File logFolder)
			throws IOException, InterruptedException, JAXBException {
		if (vcs.equals(VersionControlSystem.SVN)) {
			SVNUtils.getInstance().checkout(url, folders.getProjectFolder(), Long.parseLong(version));
		} else {
			GitUtils.goToTag(version, folders.getProjectFolder());
		}

		File vmidFolder = new File(logFolder, "vm_" + vmid);
		if (vmidFolder.exists()) {
			vmidFolder = new File(logFolder, "vm_" + vmid + "_new");
		}
		vmidFolder.mkdir();

		LOG.info("Initialer Checkout beendet");

		// testgenerator.setLogFullData(true);

		// if (!projectFolder.equals(moduleFolder)) {
		// final String[] args = new String[] { "mvn", "clean", "install", "-fn", "--no-snapshot-updates",
		// "-Dcheckstyle.skip=true", "-Dmaven.compiler.source=1.7", "-Dmaven.compiler.target=1.7",
		// "-Dmaven.javadoc.skip=true", "-Denforcer.skip=true", "-Drat.skip=true", "-DskipTests=true", "--pl",
		// "jetty-servlet", "--am", "-Dpmd.skip=true", "-Dlicense.skip=true", "-X" };
		// final File logFile = new File(vmidFolder, "compilation.txt");
		// // TODO Fix multimodule
		// // pim.executeTests(testset, logFolder);
		// // Process compileProcess = pim.executeProcess(logFile, args, projectFolder);
		// // compileProcess.waitFor();
		// }

		mavenKiekerExecutor.executeTests(testset, vmidFolder);

		LOG.info("Ändere " + testset.entrySet().size() + " Klassen durch Ergänzung des Gitversion-Elements.");
		saveResultFiles(testset, version, vmid);
		System.gc();
		Thread.sleep(1);
	}

	private void saveResultFiles(final TestSet testset, final String version, final int vmid)
			throws JAXBException, IOException {
		for (final Map.Entry<ChangedEntity, List<String>> testcaseEntry : testset.entrySet()) {
			LOG.info("Teste Methoden: {}", testcaseEntry.getValue().size());
			final String expectedFolderName = "*" + testcaseEntry.getKey().getJavaClazzName();
			final Collection<File> folderCandidates = findFolder(folders.getTempMeasurementFolder(), new WildcardFileFilter(expectedFolderName));
			if (folderCandidates.size() != 1) {
				LOG.error("Ordner {} ist {} mal vorhanden.", expectedFolderName, folderCandidates.size());
			} else {
				final File folder = folderCandidates.iterator().next();
				for (final String methodname : testcaseEntry.getValue()) {
					final File oneResultFile = new File(folder, methodname + ".xml");
					if (!oneResultFile.exists()) {
						LOG.debug("Datei {} existiert nicht.", oneResultFile.getAbsolutePath());
					} else {
						LOG.debug("Lese: {}", oneResultFile);
						final XMLDataLoader xdl = new XMLDataLoader(oneResultFile);
						final Kopemedata oneResultData = xdl.getFullData();
						final List<TestcaseType> testcaseList = oneResultData.getTestcases().getTestcase();
						final String clazz = oneResultData.getTestcases().getClazz();
						if (testcaseList.size() > 0) {
							saveResults(version, vmid, methodname, oneResultFile, oneResultData, testcaseList, clazz);
						} else {
							LOG.error("Keine Daten vorhanden - Messung fehlgeschlagen?");
						}
					}
					if (testgenerator.isUseKieker()) {
						final File methodFolder = new File(folders.getKiekerResultFolder(),
								testcaseEntry.getKey() + "." + methodname);
						if (!methodFolder.exists()) {
							methodFolder.mkdir();
						}
						final File versionFolder = new File(methodFolder, version);
						if (!versionFolder.exists()) {
							versionFolder.mkdir();
						}

						final File dest = new File(versionFolder, vmid + ".tar.gz");

						try {
							final Process process = new ProcessBuilder("tar", "-czf", dest.getAbsolutePath(),
									folder.getAbsolutePath()).start();
							process.waitFor();
							FileUtils.deleteDirectory(folder);
						} catch (final InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			for (final File file : folders.getTempMeasurementFolder().listFiles()) {
				FileUtils.forceDelete(file);
			}
		}
	}

	private void saveResults(final String version, final int vmid, final String methodname, final File oneResultFile,
			final Kopemedata oneResultData, final List<TestcaseType> testcaseList, final String clazz)
			throws JAXBException, IOException {
		// Update testname, in case it has been set to
		// testRepetition
		testcaseList.get(0).setName(methodname);
		XMLDataStorer.storeData(oneResultFile, oneResultData);

		final TestcaseType oneRundata = testcaseList.get(0);
		final File fullResultFile = new File(folders.getFullMeasurementFolder(), methodname + ".xml");
		LOG.info("Schreibe in Ergebnisdatei: {}", fullResultFile);
		MultipleVMTestUtil.fillOtherData(fullResultFile, oneRundata, clazz, methodname, version);
		final File destFolder = new File(folders.getDetailResultFolder(), clazz);
		final File destFile = new File(destFolder, methodname + "_" + vmid + "_" + version + ".xml");
		LOG.info("Verschiebe nach: {}", destFile);
		if (!destFile.exists()) {
			FileUtils.moveFile(oneResultFile, destFile);
		} else {
			final File destFileAlternative = new File(destFolder, methodname + "_" + vmid + "_" + version + "_new.xml");
			if (!destFileAlternative.exists()) {
				FileUtils.moveFile(oneResultFile, destFileAlternative);
			} else {
				throw new RuntimeException(
						"Moving failed: " + destFile + " and " + destFileAlternative + " already exist.");
			}
		}

		// oneResultFile.delete();
	}

	private static List<File> findFolder(final File baseFolder, final FileFilter folderFilter) {
		final List<File> files = new LinkedList<>();
		for (final File f : baseFolder.listFiles()) {
			if (f.isDirectory()) {
				if (folderFilter.accept(f)) {
					files.add(f);
				} else {
					files.addAll(findFolder(f, folderFilter));
				}
			}
		}
		return files;
	}

	public int getVMCount() {
		return vms;
	}

}
