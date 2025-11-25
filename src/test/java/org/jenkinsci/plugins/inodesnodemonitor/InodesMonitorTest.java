package org.jenkinsci.plugins.inodesnodemonitor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;

import org.junit.jupiter.api.Test;

class InodesMonitorTest {

	@Test
	void check_threshold_exception() {
		assertThrows(ParseException.class, () -> {
			new InodesMonitor("110%");
		});
	}

	@Test
	void check_threshold_exception2() {
		assertThrows(ParseException.class, () -> {
			new InodesMonitor(",5%");
		});
	}

	@Test
	void threshold_ok() throws Exception {
		new InodesMonitor("5%");
		new InodesMonitor("50%");
		new InodesMonitor("99%");
	}

	@Test
	void parse() throws Exception {
		assertEquals(8, InodesMonitor.parse("8%"));
	}
}
