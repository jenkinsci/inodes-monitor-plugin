package org.jenkinsci.plugins.inodesnodemonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.model.Computer;
import hudson.node_monitors.AbstractAsyncNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Callable;
import hudson.slaves.OfflineCause;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

/**
 * Checks the used percentage of inodes on the FS (Linux/Unix only).
 *
 * Uses the <code>df</code> system command.
 */
public class InodesMonitor extends NodeMonitor {

	private static final Logger LOGGER = Logger.getLogger(InodesMonitor.class.getSimpleName());

	private static final String DEFAULT_OFFLINE_THRESHOLD = "95%";

	public final String inodesPercentThreshold;

	/**
	 * @param inodesPercentThreshold
	 *            threshod expected to be a percentage between 0% and 99% (% required). "5%" is correct. "5" is not.
	 *
	 * @throws ParseException
	 *             if unable to parse.
	 */
	@DataBoundConstructor
	public InodesMonitor(String inodesPercentThreshold) throws ParseException {
		if (inodesPercentThreshold == null) {
			inodesPercentThreshold = DEFAULT_OFFLINE_THRESHOLD;
		}
		parse(inodesPercentThreshold); // checks it parses
		this.inodesPercentThreshold = inodesPercentThreshold;
	}

	public InodesMonitor() {
		inodesPercentThreshold = DEFAULT_OFFLINE_THRESHOLD;
	}

	@VisibleForTesting
	static int parse(String threshold) throws ParseException {
		if (!threshold.matches("\\d?\\d%")) {
			throw new ParseException(threshold, 0);
		}
		return Integer.parseInt(threshold.substring(0, threshold.length() - 1));
	}

	@Override
	public Object data(Computer computer) {
		String currentValueStr = (String) super.data(computer);
		if (currentValueStr == null || currentValueStr.contains(Messages.inodesmonitor_notapplicable())) {
			return currentValueStr;
		}
		try {
			int currentValue = parse(currentValueStr);
			String currentState = "current=" + currentValue + ",threshold=" + inodesPercentThreshold;
			String computerName = computer.getName();
			// master has no nodeName
			if ("".equals(computer.getName())) {
				computerName = "master";
			}

			if (currentValue >= parse(inodesPercentThreshold)) {
				OfflineCause offlineCause = OfflineCause.create(Messages._inodesmonitor_markedOffline(computerName, currentState));
				if (((InodesUseInPercentMonitorDescriptor) getDescriptor()).markOffline(computer, offlineCause)) {
					String inodesmonitor_markedOffline = Messages.inodesmonitor_markedOffline(computerName, currentState);
					LOGGER.warning(inodesmonitor_markedOffline);
				}
			}
			else {
				if (((InodesUseInPercentMonitorDescriptor) getDescriptor()).markOnline(computer)) {
					LOGGER.warning(Messages.inodesmonitor_markedOnline(computerName, currentState));
				}
			}
		}
		catch (ParseException e) {
			LOGGER.warning("Can't parse " + currentValueStr + " as integer percentage", e);
			return "N/A";
		}
		return currentValueStr;
	}

	@Override
	public final String getColumnCaption() {
		// Hide to non-admins
		return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER) ? super.getColumnCaption() : null;
	}

	@Extension
	public static final InodesUseInPercentMonitorDescriptor DESCRIPTOR = new InodesUseInPercentMonitorDescriptor();

	static class InodesUseInPercentMonitorDescriptor extends AbstractAsyncNodeMonitorDescriptor<String> {

		@Override
		public String getDisplayName() {
			return Messages.inodesmonitor_useinpercent();
		}

		@Override
		protected Callable<String, IOException> createCallable(Computer c) {
			return new GetInodesUseInPercent();
		}

		// Only augmenting visibility...
		@Override
		public boolean markOffline(Computer c, OfflineCause oc) {
			return super.markOffline(c, oc);
		}

		@Override
		public boolean markOnline(Computer c) {
			return super.markOnline(c);
		}
	};

	private static class GetInodesUseInPercent extends MasterToSlaveCallable<String, IOException> {
		private static final long serialVersionUID = 1L;
		@Override
		public String call() {
			return new DfRunner().getUsedInodesPercentage();
		}
	}
}
