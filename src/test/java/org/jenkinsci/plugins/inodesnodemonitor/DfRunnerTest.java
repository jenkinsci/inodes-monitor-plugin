package org.jenkinsci.plugins.inodesnodemonitor;

import org.junit.Test;

public class DfRunnerTest {
	@Test
	public void get_percentage() throws Exception {
		new DfRunner().getUsedInodesPercentage();
	}
}
