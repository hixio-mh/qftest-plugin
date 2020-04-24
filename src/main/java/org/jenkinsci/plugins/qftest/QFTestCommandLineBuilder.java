package org.jenkinsci.plugins.qftest;

import hudson.FilePath;
import hudson.Launcher;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class QFTestCommandLineBuilder extends ExtendedArgumentListBuilder {


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

    public QFTestCommandLineBuilder(String binary, RunMode aMode) {
        this.add(binary);
        for (RunMode mode : RunMode.values()) {
            if (mode == aMode) {
                this.presetArg(PresetType.ENFORCE, mode.toString());
            } else {
                this.presetArg(PresetType.DROP, mode.toString());
            }
        }
    }

    public QFTestCommandLineBuilder(String binary) {
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

    static public QFTestCommandLineBuilder newCommandLine(@Nullable String path, final boolean isUnix, final RunMode aMode) {

       //first choice: given path
        ;

        //next: try global config managed by builder descriptor
        if (path == null) {
            //TODO: Test this
            //TODO: Instead of exceptions: skip this step
            QFTestConfigBuilder.DescriptorImpl theDescriptor = Jenkins.get().getDescriptorByType(QFTestConfigBuilder.DescriptorImpl.class);
            assert(theDescriptor != null);

            path = isUnix ? theDescriptor.getQfPathUnix() : theDescriptor.getQfPath();
        }

        //next: rely on PATH variable
        if (path == null) {
            path = isUnix ? "qftest" : "qftestc.exe";
        }

        QFTestCommandLineBuilder command = new QFTestCommandLineBuilder(path, aMode);
        command.presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-batch");

        return command;
    };
}
