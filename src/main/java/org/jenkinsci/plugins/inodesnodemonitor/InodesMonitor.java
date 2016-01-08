package org.jenkinsci.plugins.inodesnodemonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.Computer;
import hudson.node_monitors.AbstractAsyncNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;

public class InodesMonitor extends NodeMonitor {

	@Override
	public final String getColumnCaption() {
		// Hide to non-admins
		return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER) ? super.getColumnCaption() : null;
	}

	@Extension
	public static final InodesUseInPercentMonitorDescriptor DESCRIPTOR = new InodesUseInPercentMonitorDescriptor();

	static class InodesUseInPercentMonitorDescriptor extends AbstractAsyncNodeMonitorDescriptor<String> {

		@Override
		public InodesMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return new InodesMonitor();
		}

		@Override
		public String getDisplayName() {
			return Messages.inodesmonitor_useinpercent();
		}

		@Override
		protected Callable<String, IOException> createCallable(Computer c) {
			return new GetInodesUseInPercent();
		}

	};

	private static class GetInodesUseInPercent extends MasterToSlaveCallable<String, IOException> {
		private static final long serialVersionUID = 1L;
		private static final Logger LOGGER = Logger.getLogger(GetInodesUseInPercent.class.getSimpleName());

		@Override
		public String call() {
			if (System.getProperty("os.name").contains("windows")) {
				return Messages.inodesmonitor_notapplicable();
			}
			return getUsedInodes();
		}

		/**
		 * Sample output:
		 *
		 * <pre>
		 * $ df --inodes .
		 * Filesystem                       Inodes  IUsed   IFree IUse% Mounted on
		 * /dev/mapper/fedora_nhuitre-home 8527872 624603 7903269    8% /home
		 * </pre>
		 *
		 * @return the percentage usage (second line, 5th column)
		 */
		private String getUsedInodes() {
			try {
				Process process = Runtime.getRuntime().exec("df --portability --inodes .");
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				bufferedReader.readLine(); // Evacuate first line with headers
				String values = bufferedReader.readLine();
				String[] split = values.split(" +");
				return split[4];
			}
			catch (IOException e) {
				LOGGER.fine("Erreur while running 'df'");
				return Messages.inodesmonitor_notapplicable_onerror();
			}
		}
	}
}
