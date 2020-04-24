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


   /*
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
    */
}

