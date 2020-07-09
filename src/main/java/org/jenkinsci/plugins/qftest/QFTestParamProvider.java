package org.jenkinsci.plugins.qftest;

/*!
This interface boundles all relevant functions  which both arms,
a (pipeline) step and a (classical) builder have to offer
 */

import java.io.Serializable;
import java.util.List;

public interface QFTestParamProvider extends Serializable {
    List<Suites> getSuitefield();
    String getReportDirectory();

    String getCustomPath();

    String getOnTestFailure();

    String getOnTestWarning();

    String getOnTestError();

    String getOnTestException();

    String getReportGenArgs();
}

