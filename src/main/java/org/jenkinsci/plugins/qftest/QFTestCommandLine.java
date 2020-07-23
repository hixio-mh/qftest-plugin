package org.jenkinsci.plugins.qftest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;

public class QFTestCommandLine extends ExtendedArgumentListBuilder {
	
	private static final long serialVersionUID = -8658681990707422575L;

	public static enum RunMode {
        RUN("-run"),
        GENREPORT("-genreport"),
        GENDOC("-gendoc");

        private final String str;

        RunMode(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public QFTestCommandLine(String binary, RunMode aMode) {
        this.add(binary);
        for (RunMode mode : RunMode.values()) {
            if (mode == aMode) {
                this.presetArg(PresetType.ENFORCE, mode.toString());
            } else {
                this.presetArg(PresetType.DROP, mode.toString());
            }
        }
    }

    public QFTestCommandLine(String binary) {
        this(binary, RunMode.RUN);
    }


    public int addSuiteConfig(FilePath workspace, Suites aSuite) throws IOException, InterruptedException {
        this.addTokenized(aSuite.getCustomParam());
        List<String> suites = aSuite.getExpandedPaths(workspace)
                //.peek(s -> listener.getLogger().println("HERE: " + s))
                .map(p -> p.getRemote())
                .collect(Collectors.toList());
        this.add(suites);
        return suites.size();
    }

    public Proc start(Launcher launcher, TaskListener listener, FilePath workspace, EnvVars env) throws IOException {
        return launcher.new ProcStarter()
                .cmds(this)
                .stdout(listener)
                .pwd(workspace)
                .envs(env)
                .start();
    }

    static public QFTestCommandLine newCommandLine(@Nullable String qftestExe, boolean isUnix, final RunMode aMode) throws java.lang.InterruptedException {

        if (qftestExe != null) {
            try {
               qftestExe = pathHeuristic(qftestExe, isUnix);
            } catch (java.io.IOException ex) {
                ; //go on ... but command will presumably fail later on
            }
        } else {
            qftestExe = isUnix ? "qftest" : "qftestc.exe";
        }

        QFTestCommandLine command = new QFTestCommandLine(qftestExe, aMode);
        command.presetArg(QFTestCommandLine.PresetType.ENFORCE, "-batch");
        return command;
    }


    private static String pathHeuristic(String stem, boolean isUnix) throws java.io.IOException, java.lang.InterruptedException {
        FilePath path = new FilePath(new File(stem));

        if (!path.exists()) {
            throw new java.io.FileNotFoundException("Could not resolve path: " + stem);
        }

        if (path.isDirectory()) {
            FilePath exe = path.child(isUnix ? "qftest" : "qftestc.exe");
            if (exe.exists()) {
                return exe.getRemote();
            }

            FilePath bin = path.child("bin");
            if (bin.exists()) {
                return pathHeuristic(bin.getRemote(), isUnix);
            }

            throw new java.io.FileNotFoundException("Could not executable in path: " + stem);

        } else if (!isUnix) {
           FilePath exec = path.sibling("qftestc.exe");
           if (exec.exists()) {
               return exec.getRemote();
           }
        }

        return stem;
    }
}
