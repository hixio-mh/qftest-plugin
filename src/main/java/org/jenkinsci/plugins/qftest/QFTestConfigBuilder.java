/*
 * The MIT License
 *
 * Copyright (c) 2017 Quality First Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.qftest;

import java.lang.String;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import com.pivovarit.function.ThrowingFunction;
import htmlpublisher.HtmlPublisherTarget;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import org.apache.commons.lang.NotImplementedException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;
import javax.annotation.CheckForNull;
import org.jenkinsci.Symbol;
import javax.annotation.Nonnull;

import htmlpublisher.HtmlPublisher;

/**
 *
 * This class saves all the configurations made by the user and executes a
 * script created by the ScriptCreator Class when a build is performed
 *
 * @author QFS, Sebastian Kleber
 * @author QFS, Philipp Mahlberg
 */

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
	value="UUF_UNUSED_FIELD",
        justification="Need unused transient values for backward compatibility"
)
public class QFTestConfigBuilder extends Builder implements SimpleBuildStep
{

	/* deprecated members */
	private transient boolean customReportTempDirectory;
	private transient boolean specificQFTestVersion;
	private transient boolean suitesEmpty;
	private transient boolean daemonSelected;
	private transient String daemonhost;
	private transient String daemonport;

	private final ArrayList<Suites> suitefield;

	@CheckForNull
	private String customPath;

	@CheckForNull
	private String customReports;

	@CheckForNull
	private Character reducedQFTReturnValue;

	private Result onTestWarning;
	private Result onTestError;
	private Result onTestException;
	private Result onTestFailure;

	// Constructor gets called when the user saves the job configuration.
	// config.jelly sends the parameters

	/**
	 * CTOR
	 *
	 * @param suitefield Contains name of testsuites and their command line arguments
	 */
	@DataBoundConstructor
	public QFTestConfigBuilder(List<Suites> suitefield) {
		this.suitefield = new ArrayList<>(suitefield);
	}

	@DataBoundSetter
	public void setCustomPath(String customPath) {
	    if (customPath != null) {
			this.customPath = customPath.isEmpty() ? null : customPath;
		}
	}

	public @CheckForNull
	String getCustomPath() {
		return customPath;
	}


	@DataBoundSetter
	public void setReportDirectory(String customReports) {
		if (!customReports.equals(DescriptorImpl.defaultReportDir)) {
			this.customReports = customReports;
		} else {
			this.customReports = null;
		}
	}

	public @Nonnull
	String getReportDirectory() {
		return (customReports != null ? customReports : DescriptorImpl.defaultReportDir);
	}


	public ArrayList<Suites> getSuitefield() {
		return suitefield;
	}


	/** Called by XStream when deserializing object
	 */
	protected Object readResolve() {
	    this.setCustomPath(customPath);
	    if (customReports!=null && customReports.isEmpty()) {
	    	customReports = null;
		}

	    return this;
	}


	private char addToReducedReturnValue(char ret) {
		if (	(reducedQFTReturnValue  == null) ||
				(reducedQFTReturnValue <= 3 && ret > reducedQFTReturnValue) //only update to first non-negative return value
		) {
			reducedQFTReturnValue = ret;
		}
		return reducedQFTReturnValue.charValue();
	}

	@Override
	public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

		reducedQFTReturnValue = null;

		EnvVars env = run.getEnvironment(listener);

		FilePath logdir = workspace.child(getReportDirectory());

		listener.getLogger().println("(Creating and/or clearing " + logdir.getName() + " directory");
		logdir.mkdirs();
		logdir.deleteContents();

		FilePath htmldir = logdir.child("html");
		htmldir.mkdirs();

		FilePath junitdir = logdir.child("junit");
		junitdir.mkdirs();

		FilePath qrzdir = logdir.child("qrz");
		qrzdir.mkdirs();


		ThrowingFunction<QFTestCommandLineBuilder.RunMode, QFTestCommandLineBuilder, ?> newQFTCommandLine = (QFTestCommandLineBuilder.RunMode aMode) -> {

			String path;

			if (this.getCustomPath() != null) {
				path = this.customPath;
			} else if (launcher.isUnix() && getDescriptor().getQfPathUnix() != null) {
				path = getDescriptor().qfPathUnix;
			} else if (!launcher.isUnix() && getDescriptor().getQfPath() != null) {
				path = this.getDescriptor().qfPath;
			} else {
				if (launcher.isUnix()) {
					path = "qftest";
				} else {
					path = "qftestc.exe";
				}
			}

			QFTestCommandLineBuilder command = new QFTestCommandLineBuilder(path, aMode);
			command.presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-batch");

			return command;

		};

		 ThrowingFunction<QFTestCommandLineBuilder, Proc, ?> startQFTestProc = (QFTestCommandLineBuilder args) -> {

			 return launcher.new ProcStarter()
					 .cmds(args)
					 .stdout(listener)
					 .pwd(workspace)
					 .envs(env)
					 .start();
		 };

		Consumer<String> resultSetter = (String resAsString) -> {
			run.setResult(Result.fromString(resAsString));
		};

		 //RUN SUITES
		 suitefield.stream()
				 .peek(sf -> listener.getLogger().println(sf.toString()))
				 .map(sf -> new Suites(
				 		env.expand(sf.getSuitename()), env.expand(sf.getCustomParam())
				 ))
				 .peek(sf -> listener.getLogger().println(sf.toString()))
				 .flatMap(sf -> {
					try {
						return sf.expand(workspace);
					} catch (java.lang.Exception ex) {
						Functions.printStackTrace(
							ex, listener.fatalError(
								new StringBuilder("During expansion of").append(sf).append("\n").append(ex.getMessage()).toString()
						));
						return Stream.<Suites>empty();
					}
				 })
				 .forEach(sf -> {
					 try {
						 QFTestCommandLineBuilder args = newQFTCommandLine.apply(QFTestCommandLineBuilder.RunMode.RUN);

						 args.presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-run")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.html")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.html")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.junit")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.xml")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-gendoc")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-testdoc")
								 .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-pkgdoc")
								 .presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-nomessagewindow")
								 .presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-runlogdir", qrzdir.getRemote());
						 args.addSuiteConfig(workspace, sf);

						 int ret = startQFTestProc.apply(args).join();

						 addToReducedReturnValue((char) ret);
						 listener.getLogger().println("  Finished with return value: " + ret);


					 } catch (java.lang.Exception ex) {
						 listener.error(ex.getMessage());
						 resultSetter.accept(this.getOnTestFailure());
						 Functions.printStackTrace(ex, listener.fatalError(ex.getMessage()));
					 }
				 });

		//DETEERMINE BUILD STATUS

        if (reducedQFTReturnValue != null ) {
			switch (reducedQFTReturnValue.charValue()) {
				case (0):
					//run.setResult(run.getResult().combine(Result.SUCCESS));
					resultSetter.accept(Result.SUCCESS.toString());
					break;
				case (1):
					//run.setResult(run.getResult().combine(onTestWarning));
					resultSetter.accept(this.getOnTestWarning());
					break;
				case (2):
					//run.setResult(run.getResult().combine(onTestError));
					resultSetter.accept(this.getOnTestError());
					break;
				case (3):
					//run.setResult(run.getResult().combine(onTestException));
					resultSetter.accept(this.getOnTestException());
					break;
				default:
					//run.setResult(run.getResult().combine(onTestFailure));
					resultSetter.accept(this.getOnTestFailure());
					break;
			}
		} else {
			resultSetter.accept(this.getOnTestFailure());
		}

		 //PICKUP ARTIFACTS
		java.util.function.Function<FilePath, String> fp_names = (fp -> fp.getName());
		run.pickArtifactManager().archive(
				qrzdir, launcher, new BuildListenerAdapter(listener),
				Arrays.stream(qrzdir.list("*.q*"))
						.collect(Collectors.toMap(fp_names, fp_names))
		);

		//CREATE REPORTS
		listener.getLogger().println("Creating reports");

		try {

			QFTestCommandLineBuilder args = newQFTCommandLine.apply(QFTestCommandLineBuilder.RunMode.GENREPORT);
			args.presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-runlogdir", qrzdir.getRemote());

			RunLogs rl = new RunLogs(
					new ArgumentListBuilder(
							"-report.html", htmldir.getRemote(), "-report.junit", junitdir.getRemote()
					).toStringWithQuote()
			);

			int nReports = args.addSuiteConfig(qrzdir, rl);
			if (nReports > 0) {
				startQFTestProc.apply(args).join();
				htmldir.child("report.html").renameTo(htmldir.child("index.html"));
			} else {
				listener.getLogger().println("No reports found. Marking run with `test failure'");
				run.setResult(onTestFailure);
			}
		} catch (java.lang.Exception ex) {
			resultSetter.accept(this.getOnTestFailure());
			Functions.printStackTrace(ex, listener.fatalError(ex.getMessage()));
		}

		//Publish HTML report
		HtmlPublisher.publishReports(
				run, workspace, listener, Collections.singletonList(new HtmlPublisherTarget(
						"QF-Test Report", htmldir.getRemote(), "index.html", true, false, false
				)), this.getClass()
		);
	}


	@Override
	@SuppressWarnings("rawtypes")
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws java.io.IOException {

		try {
			this.perform(build, build.getWorkspace(), launcher, listener);
			//we have set the build result explicitly via setResult...
			return true;
		} catch(java.lang.InterruptedException ex) { //TODO: check this
			return false;
		} catch (NullPointerException ex) {
			return false;
		}
	}

	// Descriptor is needed to access global variables
	/*
	 * (non-Javadoc)
	 *
	 * @see hudson.tasks.Builder#getDescriptor()
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public String getOnTestWarning() {
	    return (onTestWarning != null ? onTestWarning : getDescriptor().defaultTestWarning).toString();
	}

	public String getOnTestError() {
		return (onTestError != null ? onTestError : getDescriptor().defaultTestError).toString();
	}

	public String getOnTestException() {
		return (onTestException != null ? onTestException : getDescriptor().defaultTestException).toString();
	}

	public String getOnTestFailure() {
		return (onTestFailure != null ? onTestFailure : getDescriptor().defaultTestFailure).toString();
	}


	@DataBoundSetter
	public void setOnTestWarning(String onTestWarning) {
		if (!onTestWarning.equals(getDescriptor().defaultTestWarning.toString())) {
			this.onTestWarning = Result.fromString(onTestWarning);
		}
	}

	@DataBoundSetter
	public void setOnTestError(String onTestError) {
	    if (!onTestError.equals(getDescriptor().defaultTestError.toString())) {
			this.onTestError = Result.fromString(onTestError);
		}
	}

	@DataBoundSetter
	public void setOnTestException(String onTestException) {
		if (!onTestException.equals(getDescriptor().defaultTestException.toString())) {
			this.onTestException = Result.fromString(onTestException);
		}
	}

	@DataBoundSetter
	public void setOnTestFailure(String onTestFailure) {
		if (!onTestFailure.equals(getDescriptor().defaultTestFailure.toString())) {
			this.onTestFailure = Result.fromString(onTestFailure);
		}
	}

	/**
	 * Implementation of descriptor
	 */
	@Symbol("QFTest")
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		public static final String defaultReportDir = "_qftestRunLogs";

		public final Result defaultTestWarning = Result.SUCCESS;
		public final Result defaultTestError = Result.FAILURE;
		public final Result defaultTestException = Result.FAILURE;
		public final Result defaultTestFailure = Result.FAILURE;

		@CheckForNull
		private String qfPath;

		@CheckForNull
		private String qfPathUnix;

		public DescriptorImpl() {

			load();

			//ensure qfPath is either null or non-empty string
			qfPath = this.getQfPath();
			qfPathUnix = this.getQfPathUnix();
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
		 */
		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return "Run QF-Test";
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see
		 * hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest,
		 * net.sf.json.JSONObject)
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

			qfPath = formData.getString("qfPath");
			qfPathUnix = formData.getString("qfPathUnix");

			save();
			return super.configure(req, formData);
		}

		/**
		 * Returns the defined QF-Test installation path (Windows) in the global
		 * setting.
		 *
		 * @return path to QF-Test installation (Windows)
		 */
		public String getQfPath() {
			if (qfPath != null && !qfPath.isEmpty()) {
				return qfPath;
			} else {
				return null;
			}
		}

		/**
		 * Returns the defined QF-Test installation path (Unix) in the global
		 * setting.
		 *
		 * @return path to QF-Test installation (Unix)
		 */
		public String getQfPathUnix() {
		    if (qfPathUnix != null && !qfPathUnix.isEmpty()) {
				return qfPathUnix;
			} else {
				return null;
			}
		}

		//TODO: change this
		public FormValidation doCheckDirectory(@QueryParameter String value) {

			if (value.contains(":") || value.contains("*")
					|| value.contains("?") || value.contains("<")
					|| value.contains("|") || value.contains(">")) {
				return FormValidation.error("Path contains forbidden characters");
			}
			return FormValidation.ok();
		}


		private ListBoxModel fillOnTestResult(Result defaultSelect) {
			ListBoxModel items = new ListBoxModel();
			Stream.of(Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.ABORTED, Result.NOT_BUILT)
					.forEach(res -> {
						items.add(res.toString());
						if (defaultSelect == res) { //mark this as selection
							items.get(items.size()-1).selected = true;
						}
					});

			return items;
		}


		public ListBoxModel doFillOnTestWarningItems() {
			return fillOnTestResult(defaultTestWarning);
		}

		public ListBoxModel doFillOnTestErrorItems() {
			return fillOnTestResult(defaultTestError);
		}

		public ListBoxModel doFillOnTestExceptionItems() {
			return fillOnTestResult(defaultTestException);
		}

		public ListBoxModel doFillOnTestFailureItems() {
			return fillOnTestResult(defaultTestFailure);
		}
	}
}
