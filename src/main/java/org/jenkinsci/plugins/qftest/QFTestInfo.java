package org.jenkinsci.plugins.qftest;

import hudson.model.Result;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import java.io.Serializable;

/**
 * Class that bundles all information provided by an QF-Test step.
 * It's intended to be used within the groovy pipeline script.
 * ATM, only getJenkinsResult is implemented.
 */
public class QFTestInfo implements Serializable {

	private static final long serialVersionUID = 1843071790051519624L;
	private Result jenkinsResult;

    @Whitelisted
    public Result getJenkinsResult() {
        return jenkinsResult;
    }

    public QFTestInfo(Result jenkinsResult) {
        this.jenkinsResult = jenkinsResult;
    }
}
