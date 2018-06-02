package org.jenkinsci.plugins.inodesnodemonitor;

import hudson.Functions;
import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;

public class DfRunnerTest {
	@Test
	public void get_percentage() throws Exception {
		assumeFalse(Functions.isWindows());

		final String usedInodesPercentageStr = new DfRunner().getUsedInodesPercentage();
		assertThat(usedInodesPercentageStr).endsWith("%");

		final int value = InodesMonitor.parse(usedInodesPercentageStr);

		assertThat(value).isBetween(0, 100);
	}
}
