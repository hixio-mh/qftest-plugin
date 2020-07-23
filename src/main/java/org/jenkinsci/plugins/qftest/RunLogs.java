//import org.jenkinsci.plugins.qftest.Suites;
package org.jenkinsci.plugins.qftest;

public class RunLogs extends Suites {

	private static final long serialVersionUID = 3782884897040482126L;

	public RunLogs(String customParam) {
        super("", customParam);
    }

    @Override
    protected String directorySearchString() {
        return "*.q??";
    }
}
