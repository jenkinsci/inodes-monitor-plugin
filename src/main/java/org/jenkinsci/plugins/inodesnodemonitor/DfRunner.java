package org.jenkinsci.plugins.inodesnodemonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

class DfRunner {
	private static final Logger LOGGER = Logger.getLogger(DfRunner.class.getSimpleName());

	private static Map<String, DfCommand> IMPLEMENTATIONS = new LinkedHashMap<>();

	static {
		IMPLEMENTATIONS.put("windows", new WindowsDfCommand());
		IMPLEMENTATIONS.put("linux", new LinuxDfCommand());
		IMPLEMENTATIONS.put("mac os", new MacOsDfCommand());
		IMPLEMENTATIONS.put("freebsd", new MacOsDfCommand()); // Same as Mac
		IMPLEMENTATIONS.put("aix", new AixDfCommand());
	}

	public String getUsedInodesPercentage() {
		return findImplementation().get();
	}

	private DfCommand findImplementation() {
		String osName = System.getProperty("os.name");

        for (Map.Entry<String, DfCommand> impl : IMPLEMENTATIONS.entrySet()) {
            final String key = impl.getKey();
            if(osName.toLowerCase().startsWith(key)) {
				LOGGER.info("DfRunner implementation key selected: " + key);
				return impl.getValue();
            }
        }
		return new DefaultDfCommand();
	}

	private static abstract class DfCommand {
		private String command;
		public final int line, column;

		DfCommand(String command, int line, int column) {
			this.command = command;
			this.line = line;
			this.column = column;
		}

		public String get() {
			try {
				LOGGER.fine("Inodes monitoring: running '" + command + "' command in " + System.getProperty("user.dir"));
				Process process = Runtime.getRuntime().exec(command);
				// Encoding used below could be many ones, as anyway the charset expect for df output is encoded the same in US_ASCII or UTF8 for instance
				// /me sighs at that confusion between charsets and [character] encoding[s] schemes.
				try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {
					for (int i = 1; i < line; ++i) {
						bufferedReader.readLine(); // Evacuating first lines (header...)
					}
                    String values = bufferedReader.readLine();
					if (values == null) {
						return Messages.inodesmonitor_notapplicable_onerror();
					}
					LOGGER.warning("df values output: " + values);
                    String[] split = values.split(" +");
                    return split[column - 1];
                }
			}
			catch (IOException e) {
				LOGGER.fine("Error while running '" + command + "'");
				return Messages.inodesmonitor_notapplicable_onerror();
			}
		}
	}

	private static class WindowsDfCommand extends DfCommand {
		WindowsDfCommand() {
			super(null, -1, -1);
		}

		@Override
		public String get() {
			return Messages.inodesmonitor_notapplicable();
		}
	}

	private static class LinuxDfCommand extends DfCommand {
		LinuxDfCommand() {
			// The -P can help *not* output the values on two lines when the FS has a long name
			// But beware the other platform where -P with -i will either
			// Disable -i (Mac OS) or just fail (AIX)
			super("df -P -i .", 2, 5);
		}
	}

	private static class AixDfCommand extends DfCommand {
		AixDfCommand() {
			super("df -i .", 2, 6);
		}
	}

	private static class MacOsDfCommand extends DfCommand {
		MacOsDfCommand() {
			super("df -i .", 2, 8);
		}
	}

	/**
	 * Tries to run df anyway. Fallback. Will return N/A anyway if an error occurs. Or should we just return N/A directly?
	 */
	private static class DefaultDfCommand extends DfCommand {
		DefaultDfCommand() {
			super("df -i .", 2, 5);
		}
	}
}
