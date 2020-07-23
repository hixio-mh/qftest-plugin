package org.jenkinsci.plugins.qftest;

import htmlpublisher.HtmlPublisher;
import htmlpublisher.HtmlPublisherTarget;
import hudson.*;
import hudson.model.*;
import jenkins.model.Jenkins;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QFTestExecutor extends SynchronousNonBlockingStepExecution<QFTestInfo> {

	private static final long serialVersionUID = 4942008420613658114L;
	
	final QFTestParamProvider params;

    QFTestExecutor(QFTestParamProvider params, StepContext context) {
        super(context);
        this.params = params;
    }

    @Override
    protected QFTestInfo run() throws Exception {
        QFTestInfo res = Imp.run(
                getContext().get(Run.class),
                getContext().get(FilePath.class),
                getContext().get(Launcher.class),
                getContext().get(TaskListener.class),
                getContext().get(EnvVars.class),
                this.params
        );

        getContext().get(FlowNode.class).addOrReplaceAction(new WarningAction((res.getJenkinsResult())));
        return res;
    }

    public static class Imp {

        static private Character reduceReturnValues(@CheckForNull  Character previous, @CheckForNull Character ret) {

            if (ret == null) {
                return previous;
            } else if (previous  == null || ret > previous) {
                //only update to first non-negative return value ) {
                return ret;
            } else {
                return previous;
            }
        }

        static public QFTestInfo run(
                @Nonnull Run<?, ?> run,
                @Nonnull FilePath workspace,
                @Nonnull Launcher launcher,
                @Nonnull TaskListener listener,
                @Nonnull EnvVars env,
                QFTestParamProvider qftParams)
                throws InterruptedException, IOException
        {
            Result jenkinsResult;
            FilePath logdir = workspace.child(env.expand(qftParams.getReportDirectory()));

            listener.getLogger().println("(Creating and/or clearing " + logdir.getName() + " directory");
            logdir.mkdirs();
            logdir.deleteContents();

            FilePath htmldir = logdir.child("html");
            htmldir.mkdirs();

			FilePath junitdir = logdir.child("junit");
			junitdir.mkdirs();

            FilePath qrzdir = logdir.child("qrz");
            qrzdir.mkdirs();


            final String qfBinaryPath;
            if (qftParams.getCustomPath() == null && run instanceof AbstractBuild) {
                Computer comp = Computer.currentComputer();
                assert(comp != null);
                QFTestConfigBuilder.DescriptorImpl theDescriptor = Jenkins.get().getDescriptorByType(QFTestConfigBuilder.DescriptorImpl.class);

                Boolean isUnix = comp.isUnix();
                if (isUnix != null) {
                    String path =  isUnix ? theDescriptor.getQfPathUnix() : theDescriptor.getQfPath();
                    qfBinaryPath = env.expand(path);
                } else {
                    listener.error("Computer is offline. Unable to determine QF-Test binary path");
                    jenkinsResult = Result.fromString(qftParams.getOnTestFailure());
                    run.setResult(jenkinsResult);
                    return new QFTestInfo(jenkinsResult);
                }
            } else {
                qfBinaryPath = env.expand(qftParams.getCustomPath());
            }

            //RUN SUITES
            Character reducedQFTReturnValue = qftParams.getSuitefield().stream()
                    .map(sf -> new Suites(
                            env.expand(sf.getSuitename()), env.expand(sf.getCustomParam())
                    ))
                    .flatMap(sf -> { //expand to single suites
                        try {
                            return sf.expand(workspace);
                        } catch (FileNotFoundException ex) {
                            listener.getLogger().println(ex.getMessage());
                            return Stream.empty();
                        } catch (Exception ex) {
                            Functions.printStackTrace(
                                    ex, listener.fatalError(
                                            "During expansion of" + sf + "\n" + ex.getMessage()
                                    ));
                            return Stream.empty();
                        }
                    })
                    .peek(sf -> listener.getLogger().println(sf.toString())) //after path expansion
                    .map(sf -> {
                        try {

                            QFTestCommandLine args = QFTestCommandLine.newCommandLine(
                                    qfBinaryPath, workspace.toComputer().isUnix(), QFTestCommandLine.RunMode.RUN
                            );
                             args
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-report", "")
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-report.html", "")
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-report.junit", "")
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-report.xml", "")
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-gendoc")
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-testdoc")
                                .presetArg(QFTestCommandLine.PresetType.DROP, "-pkgdoc")
                                .presetArg(QFTestCommandLine.PresetType.ENFORCE, "-nomessagewindow")
                                .presetArg(QFTestCommandLine.PresetType.ENFORCE, "-runlogdir", qrzdir.getRemote());
                            int nSuites = args.addSuiteConfig(workspace, sf);

                            assert(nSuites == 1); //expansion already done by explicit call of Suite::expand above

                            int ret = args.start(launcher, listener, workspace, env).join();
                            List<String> alteredArgs = args.getAlteredArgs();

                            if (! alteredArgs.isEmpty()) {
                                listener.getLogger().println("The following arguments have been dropped or altered:\n\t" + String.join(" ", args.getAlteredArgs()));
                            }

                            listener.getLogger().println("  Finished with return value: " + ret);
                            return (char) ret;

                        } catch (Exception ex) {
                            listener.error(ex.getMessage());
                            Functions.printStackTrace(ex, listener.fatalError(ex.getMessage()));
                            return (char) 4; //Test exception
                        }
                    })
                    .reduce(null, Imp::reduceReturnValues);

            //DETEERMINE BUILD STATUS

            if (reducedQFTReturnValue != null ) {
                switch (reducedQFTReturnValue.charValue()) {
                    case (0):
                        jenkinsResult = Result.SUCCESS;
                        break;
                    case (1):
                        jenkinsResult = Result.fromString(qftParams.getOnTestWarning());
                        break;
                    case (2):
                        jenkinsResult = Result.fromString(qftParams.getOnTestError());
                        break;
                    case (3):
                        jenkinsResult = Result.fromString(qftParams.getOnTestException());
                        break;
                    default:
                        jenkinsResult = Result.fromString(qftParams.getOnTestFailure());
                        break;
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
                	Computer comp = workspace.toComputer();
                	assert(comp != null);
                    Boolean isUnix = comp.isUnix();
                    assert(isUnix != null);

                    QFTestCommandLine args = QFTestCommandLine.newCommandLine(
                            qfBinaryPath, isUnix, QFTestCommandLine.RunMode.GENREPORT
                    );

                    args.presetArg(QFTestCommandLine.PresetType.ENFORCE, "-runlogdir", qrzdir.getRemote())
                            .presetArg(QFTestCommandLine.PresetType.ENFORCE, "-report.html", htmldir.getRemote())
                            .presetArg(QFTestCommandLine.PresetType.DEFAULT, "-report.junit", junitdir.getRemote());

                    RunLogs rl = new RunLogs(qftParams.getReportGenArgs());

                    int nReports = args.addSuiteConfig(qrzdir, rl);
                    if (nReports > 0) {
                        args.start(launcher, listener, workspace, env).join();
                        List<String> alteredArgs = args.getAlteredArgs();

                        if (! alteredArgs.isEmpty()) {
                            listener.getLogger().println("The following arguments have been dropped or altered:\n\t" + String.join(" ", args.getAlteredArgs()));
                        }
                    } else {
                        listener.getLogger().println("No reports found. Marking run with `test failure'");
                        run.setResult(Result.fromString(qftParams.getOnTestFailure()));
                    }
                } catch (java.lang.Exception ex) {
                    jenkinsResult = Result.fromString(qftParams.getOnTestFailure());
                    Functions.printStackTrace(ex, listener.fatalError(ex.getMessage()));
                }

                //Publish HTML report
                HtmlPublisher.publishReports(
                        run, workspace, listener, Collections.singletonList(new HtmlPublisherTarget(
                                "QF-Test Report", htmldir.getRemote(), "report.html", true, false, false
                        )), qftParams.getClass() //TODO: this clazz ok?
                );

            } else { //never run
                listener.getLogger().println("No test suites were processed at all!");
                jenkinsResult = Result.fromString(qftParams.getOnTestFailure());
            }

            run.setResult(jenkinsResult);

            if (jenkinsResult == Result.ABORTED) {
                throw new AbortException("Aborted due to failure during QF-Test build step");
            }

            return new QFTestInfo(jenkinsResult);
        }
    }
}


