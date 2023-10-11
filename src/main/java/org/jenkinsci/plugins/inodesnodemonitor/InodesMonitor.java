package org.jenkinsci.plugins.inodesnodemonitor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.node_monitors.AbstractDiskSpaceMonitor;
import hudson.node_monitors.MonitorOfflineCause;
import java.io.IOException;
import java.io.Serializable;
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
import org.kohsuke.stapler.export.ExportedBean;

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
		Inodes inodes = new Inodes(currentValueStr, inodesPercentThreshold);
		if (currentValueStr == null || currentValueStr.contains(Messages.inodesmonitor_notapplicable())) {
			return inodes;
		}
		try {
			int currentValue = parse(currentValueStr);
			String currentState = "current=" + currentValue + ",threshold=" + inodesPercentThreshold;
			String computerName = computer.getName();
			// master has no nodeName
			if ("".equals(computer.getName())) {
				computerName = "built-in";
			}

			if (currentValue >= parse(inodesPercentThreshold)) {
				inodes.setTriggered(this.getClass(), true);
				if (((InodesUseInPercentMonitorDescriptor) getDescriptor()).markOffline(computer, inodes)) {
					String inodesmonitor_markedOffline = Messages.inodesmonitor_markedOffline(computerName, currentState);
					LOGGER.warning(inodesmonitor_markedOffline);
				}
			}
			else {
				if (computer.getOfflineCause() instanceof Inodes &&
								((InodesUseInPercentMonitorDescriptor) getDescriptor()).markOnline(computer)) {
					LOGGER.info(Messages.inodesmonitor_markedOnline(computerName, currentState));
				}
			}
		}
		catch (ParseException e) {
			// Shouldn't happen since received value is the one already provided by internal GetInodesUseInPercent
			throw new IllegalStateException("WTF? Can't parse " + currentValueStr + " as integer percentage", e);
		}
		return inodes;
	}

	@Override
	public final String getColumnCaption() {
		// Hide to non-admins
		return Jenkins.get().hasPermission(Jenkins.ADMINISTER) ? super.getColumnCaption() : null;
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
	}

	private static class GetInodesUseInPercent extends MasterToSlaveCallable<String, IOException> {
		private static final long serialVersionUID = 1L;
		@Override
		public String call() {
			return new DfRunner().getUsedInodesPercentage();
		}
	}

	@ExportedBean
	public static final class Inodes extends MonitorOfflineCause implements Serializable {

		private Class<? extends NodeMonitor> trigger;

		private final String usage;

		private final String threshold;

		private boolean triggered;

		public Inodes(String usage, String threshold) {
			this.usage = usage;
			this.threshold = threshold;
		}

		void setTriggered(Class<? extends NodeMonitor> trigger, boolean triggered) {
			this.trigger = trigger;
			this.triggered = triggered;
		}

		public boolean isTriggered() {
			return triggered;
		}

		@NonNull
		@Override
		public Class<? extends NodeMonitor> getTrigger() {
			return trigger;
		}

		public String getUsage() {
			return usage;
		}

		public String toString() {
			return Messages.inodesmonitor_FreeInodesTooLow(usage, threshold);
		}
	}
}
