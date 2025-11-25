package org.jenkinsci.plugins.inodesnodemonitor;

import hudson.Functions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class DfRunnerTest {

	@Test
	void get_percentage() throws Exception {
		assumeFalse(Functions.isWindows());

		final String usedInodesPercentageStr = new DfRunner().getUsedInodesPercentage();
		assertThat(usedInodesPercentageStr).endsWith("%");

		final int value = InodesMonitor.parse(usedInodesPercentageStr);

		assertThat(value).isBetween(0, 100);
	}
}
