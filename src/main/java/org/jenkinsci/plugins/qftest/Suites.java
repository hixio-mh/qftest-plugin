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


import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import static com.pivovarit.function.ThrowingFunction.unchecked;
import static com.pivovarit.function.ThrowingSupplier.unchecked;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Pattern;

public class Suites extends AbstractDescribableImpl<Suites> implements Serializable {

	private final String suitename;
	private final String customParam;
	private String fileending;


	//TODO ATTN: new method..tell xml serializer about it ..DONT
	//private final Pattern suiteReg = Pattern.compile("\\.qft$");

	/**
	 * CTOR
	 * 
	 * @param suitename
	 *            Name of the testsuite or folder containing suites
	 * @param customParam
	 *            Command Line Arguments
	 */
	@DataBoundConstructor
	public Suites(String suitename, String customParam) {
		this.suitename = suitename;
		this.customParam = customParam;

	}

	/**
	 * Returns the name of the testsuite or a folder containing suites.
	 * 
	 * @return name of the testsuite or folder containing suites
	 */
	public String getSuitename() {
		return suitename;
	}

	/**
	 * Returns the command line arguments.
	 * 
	 * @return command line arguments
	 */
	public String getCustomParam() {
		return customParam;
	}


	@Override
	public String toString() {
		return new StringBuilder("SUITE CONFIG ")
				.append("with custom params: `")
				.append(this.getCustomParam())
				.append("' and suites: `")
				.append(this.getSuitename())
				.append("'")
				.toString();
	}


	@Override
	public DescriptorImpl getDescriptor() { return (DescriptorImpl) super.getDescriptor(); }

	/**
	 * Implementation of descriptor
	 */
	@Extension
	public static class DescriptorImpl extends Descriptor<Suites> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		public String getDisplayName() {
			return "";
		}

		public FormValidation doCheckCustomParam(@QueryParameter String value) {
			for (String tok: Arrays.asList("-runlogdir")) {
				if (value.contains(tok)) {
					return FormValidation.warning(
							new StringBuilder("Setting a custom `").append(tok)
									.append("` parameter contradicts with the plugin behavior and will be dropped")
									.toString());
				}
			}
			return FormValidation.ok();
		}
	}

	//TODO: this should return a Stream of suites
	public Stream<FilePath> getExpandedPaths(FilePath base) throws IOException, InterruptedException {

		final FilePath childCandid = base.child(this.suitename);
		if (childCandid.exists()) {
			if (childCandid.isDirectory()) {
			    return Arrays.stream(childCandid.list(directorySearchString()));
			} else {
				return Stream.of(childCandid);
			}

		} else {
			FilePath[] filesFound = base.list(this.suitename);

			if (filesFound.length > 0) {
				return Arrays.stream(filesFound);
			} else {
				throw new FileNotFoundException("No suites could be found by (globbing) expression `" + this.suitename + "'");
			}
		}
	}

	protected String directorySearchString() {
	    return "**/*.qft";
	}

	/**
	 * Considers the -suitefiles parameter.
	 * As expansions down the line will relay on the existence of the file/directory `suitename',
	 * we make -suitesfile the last parameter in the customParam array and treat the suitesfile itself
	 * via the suitename variable
	 * @return
	 */
	protected Suites considerSuitesfile() {

		final String suite_arg = "-suitesfile";
		List args = new LinkedList(Arrays.asList(Util.tokenize(this.customParam)));


		int idx = args.indexOf(suite_arg);
		if (idx < 0) return this;

		args.remove(idx);
		final String file;
		if (idx < args.size()) {
		    assert(suitename.isEmpty());
			file = (String) args.remove(idx);
		} else {
		    assert(!suitename.isEmpty());
			file = suitename;
		} /* finally */ {
       		args.add(suite_arg);
		}

        return new Suites(file, String.join(" ", args));
	}

	public Stream<Suites> expand(FilePath base) throws IOException, InterruptedException {
			Suites ret = this.considerSuitesfile();
			return ret.getExpandedPaths(base)
					.map(unchecked( p -> new Suites(p.getRemote(), ret.getCustomParam())));
	}
}

