package org.jenkinsci.plugins.qftest;

import hudson.model.Result;

class DefaultValues {
    public static final String reportDir = "_qftestRunLogs";
    public static final Result testWarning = Result.SUCCESS;
    public static final Result testError = Result.FAILURE;
    public static final Result testException = Result.FAILURE;
    public static final Result testFailure = Result.FAILURE;
}
