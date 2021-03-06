/**
 * Copyright (c) MuleSoft, Inc. All rights reserved. http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.md file.
 */

package org.mulesoft.demo;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

public class RegexIncludePathFilter implements PathFilter{

private final String regex;
	
	public RegexIncludePathFilter(String regex){
		this.regex = regex;
	}
	@Override
	public boolean accept(Path path) {
		return path.toString().matches(regex);
	}
}
