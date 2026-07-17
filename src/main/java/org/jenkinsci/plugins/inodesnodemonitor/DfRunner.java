package org.jenkinsci.plugins.inodesnodemonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class DfRunner {
	private static final Logger LOGGER = Logger.getLogger(DfRunner.class.getSimpleName());

	private static Map<String, DfCommand> IMPLEMENTATIONS = new LinkedHashMap<>();

	static {
		IMPLEMENTATIONS.put("windows", new WindowsDfCommand());
		IMPLEMENTATIONS.put("linux", new LinuxDfCommand());
		IMPLEMENTATIONS.put("sunos", new SunosGnuDfCommand());
		IMPLEMENTATIONS.put("mac os", new MacOsDfCommand());
		IMPLEMENTATIONS.put("freebsd", new MacOsDfCommand()); // Same as Mac
		IMPLEMENTATIONS.put("netbsd", new MacOsDfCommand());  // Same as Mac
		IMPLEMENTATIONS.put("openbsd", new MacOsDfCommand()); // Same as Mac
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
				LOGGER.fine(() -> "DfRunner implementation key selected: " + key);
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
				LOGGER.finer(() -> "Inodes monitoring: running '" + command + "' command in " + System.getProperty("user.dir"));
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
					LOGGER.fine(() -> "df values output: " + values);
					String[] split = values.split(" +");
					return split[column - 1];
				}
			}
			catch (IOException e) {
				LOGGER.log(Level.WARNING, e, () -> "Error while running '" + command + "'");
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
			// imac$ df -i
			// Filesystem     512-blocks     Used Available Capacity iused     ifree %iused  Mounted on
			// /dev/disk1s1s1  200916912 42273056 110870656    28%  502393 554353280    0%   /
			// devfs                 369      369         0   100%     639         0  100%   /dev
			//
			// Also FreeBSD, NetBSD, OpenBSD:
			// netbsd$ df -i
			// Filesystem     512-blocks         Used        Avail %Cap      iUsed     iAvail %iCap Mounted on
			// /dev/dk0         17216604      9872076      6483700  61%      94950     981912    9% /
			//
			// openbsd$ df -i
			// Filesystem  512-blocks      Used     Avail Capacity iused   ifree  %iused  Mounted on
			// /dev/sd0a       922972    291216    585608    33%    2564   71034     3%   /
			//
			// freebsd$ df -i
			// Filesystem                                  1K-blocks    Used    Avail Capacity iused      ifree %iused  Mounted on
			// zroot/ROOT/freebsd-15.0.p9-20260525T100000Z  20215296 9862488 10352808    49%  430313   20705616    2%   /
			// devfs                                               1       0        1     0%       0          0     -   /dev
			// fdescfs                                             1       0        1     0%       4     234753    0%   /dev/fd
			// procfs                                              8       0        8     0%       0          0     -   /proc
			// tmpfs                                         7028872      40  7028832     0%      26 2147483621    0%   /mnt/tmpfs
			super("df -i .", 2, 8);
		}
	}

	private static class SunosGnuDfCommand extends DfCommand {
		SunosGnuDfCommand() {
			// Recent SunOS and illumos (OmniOS, OpenIndiana) systems do not actually
			// return any information with stock `df -o i` query, claiming it is not
			// applicable for any FSType:
			// oi$ $ df -F zfs -o i
			// df: operation not applicable for FSType zfs
			//
			// However GNU df (if installed into PATH) works:
			// $ ls -la `which gdf`
			// lrwxrwxrwx   1 root     root          13 Mar 16  2021 /usr/bin/gdf -> ../gnu/bin/df
			// $ gdf -i
			// Filesystem                                                               Inodes   IUsed    IFree IUse% Mounted on
			// rpool/ROOT/hipster_2024.04-20250906T223202Z                            80494672  218816 80275856    1% /
			//
			// FIXME: Ideally we could try several options (df, gdf, /usr/gnu/bin/df etc.)
			super("gdf -i .", 2, 5);
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
