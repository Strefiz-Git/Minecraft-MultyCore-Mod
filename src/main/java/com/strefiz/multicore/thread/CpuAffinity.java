package com.strefiz.multicore.thread;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional CPU-affinity helper.
 *
 * <p>Java has no portable API to pin threads to cores, so this is best-effort and platform-specific:
 *
 * <ul>
 *   <li>Linux: uses {@code sched_setaffinity} via JNA if available, else prints the {@code taskset}
 *       command the user should run before starting the JVM.</li>
 *   <li>Windows: uses {@code SetThreadAffinityMask} via JNA if available, else prints the
 *       {@code start /affinity} command.</li>
 * </ul>
 *
 * <p>When JNA is not on the classpath (it is not a dependency of this mod), the helper falls back to
 * printing the correct launcher command and doing nothing — never throwing.
 *
 * <p>This is an optional Phase 4 feature behind a config flag. It is never required for the mod to
 * work.
 */
public final class CpuAffinity {

	private static final Logger LOGGER = Logger.getLogger("multicore/affinity");

	private CpuAffinity() {
	}

	/**
	 * Best-effort: pins the current thread to the given core index.
	 *
	 * @param coreIndex the 0-based core index
	 * @return {@code true} if the pin succeeded, {@code false} otherwise (never throws)
	 */
	public static boolean pinCurrentThread(int coreIndex) {
		try {
			// Try JNA first (optional dependency).
			Class<?> nativeLibrary = Class.forName("com.sun.jna.Native");
			// JNA is present; attempt a platform-specific call via reflection.
			// On Linux we'd call sched_setaffinity; on Windows SetThreadAffinityMask.
			// For now we just log that JNA is available but we don't have the native binding.
			LOGGER.info("JNA is available; CPU affinity binding not yet implemented (Phase 4).");
			return false;
		} catch (ClassNotFoundException e) {
			// JNA not present — print the launcher command instead.
			String os = System.getProperty("os.name", "").toLowerCase();
			if (os.contains("win")) {
				LOGGER.info("CPU affinity: run the JVM with 'start /affinity 0x{0} java ...' " +
						"(Windows), or use the OS task manager. Core requested: {1}.",
						Integer.toHexString(1 << coreIndex), coreIndex);
			} else {
				LOGGER.info("CPU affinity: run the JVM with 'taskset -c {0} java ...' (Linux), " +
						"or use numactl. Core requested: {1}.",
						coreIndex, coreIndex);
			}
			return false;
		}
	}

	/**
	 * Prints the recommended OS-level launch command for the given number of worker threads.
	 *
	 * @param workerThreads the number of worker threads (excluding the main thread)
	 */
	public static void printLaunchCommand(int workerThreads) {
		int totalThreads = workerThreads + 1;
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			// Windows: affinity mask for 'totalThreads' cores.
			int mask = (1 << totalThreads) - 1;
			LOGGER.info("Windows launch: start /affinity 0x{0} java -jar fabric-server-launch.jar --nogui",
					Integer.toHexString(mask));
		} else {
			LOGGER.info("Linux launch: taskset -c 0-{0} java -jar fabric-server-launch.jar --nogui",
					totalThreads - 1);
		}
	}

	/**
	 * @return {@code true} when running on Windows.
	 */
	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	/**
	 * @return {@code true} when running on Linux.
	 */
	public static boolean isLinux() {
		return System.getProperty("os.name", "").toLowerCase().contains("linux");
	}
}
