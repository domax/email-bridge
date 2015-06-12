/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2015 Maksym Dominichenko
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.mail.bridge;

import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.mail.bridge.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class FolderMonitor extends AbstractMonitor implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(FolderMonitor.class);
	private static final long NEW_FILES_PROCESS_DELAY = 1000;
	private static final int SCRIPT_TIMEOUT = 60000;

	public static class SendFileMessage extends Message<List<File>> {
		public SendFileMessage(List<File> data) {
			super(data);
		}
	}

	private class ProcessFilesTimerTask extends TimerTask {
		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			timer.cancel();
			timer = null;
			try {
				processFiles((List<File>) files.clone());
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
			files.clear();
		}
	}

	private final Config config;
	private final File outboxFolder;
	private final WatchService watcher;
	private final ArrayList<File> files = new ArrayList<>();
	private Timer timer;

	private final FileFilter fileFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			return file.isFile()
					&& file.canRead()
					&& (config.getOutboxFileRegexp().isEmpty() || file.getName().matches(config.getOutboxFileRegexp()));
		}
	};
	private final Comparator<File> outboxFileComparator = new Comparator<File>() {
		@Override
		public int compare(File o1, File o2) {
			return (int) (o1.lastModified() - o2.lastModified());
		}
	};

	public FolderMonitor(Config config) throws IOException {
		this.config = config;
		outboxFolder = new File(config.getOutboxFolder());
		watcher = FileSystems.getDefault().newWatchService();
		if (outboxFolder.exists()) {
			if (!outboxFolder.isDirectory())
				throw new IOException("Specified path '" + outboxFolder.getAbsolutePath() + "' is a file, but folder expected");
			else if (!outboxFolder.canRead() || !outboxFolder.canWrite())
				throw new IOException("Specified folder '" + outboxFolder.getAbsolutePath() + "' has insufficient permissions");
		} else if (!outboxFolder.mkdirs())
			throw new IOException("Cannot prepare folder '" + outboxFolder.getAbsolutePath() + "' for work");
		LOG.debug("Instantiated");
	}

	public FolderMonitor addStopCallback(MonitorCallback<String> callback) {
		return (FolderMonitor) addCallback(Main.StopMessage.class, callback);
	}

	public FolderMonitor addSendFileCallback(MonitorCallback<List<File>> callback) {
		return (FolderMonitor) addCallback(SendFileMessage.class, callback);
	}

	private void processFiles(List<File> files) throws IOException {
		LOG.debug("Process files '{}'", files);
		if (Utils.isEmpty(files)) return;
		postMessage(new SendFileMessage(files));
	}

	public synchronized void runScriptAgainstReceivedFiles(List<File> inboxFiles) {
		if (config.getInboxScript().isEmpty() || Utils.isEmpty(inboxFiles)) return;
		LOG.debug("Run script '{}' against files '{}'", config.getInboxScript(), inboxFiles);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			CommandLine cmd = CommandLine.parse(config.getInboxScript());
			for (File file : inboxFiles)
				cmd.addArgument(file.getName(), true);
			DefaultExecutor executor = new DefaultExecutor();
			executor.setStreamHandler(new PumpStreamHandler(out));
			executor.setWatchdog(new ExecuteWatchdog(SCRIPT_TIMEOUT));
			Map<String, String> environment = EnvironmentUtils.getProcEnvironment();
			environment.putAll(config.asEnvironmentMap());
			executor.execute(cmd, environment);
			LOG.info("Script {} successfully finished", config.getInboxScript());
			LOG.debug("Script output:\n{}", out.toString());
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
			LOG.error("\nScript '{}' output:\n{}", config.getInboxScript(), out.toString());
		}
	}

	@Override
	public synchronized FolderMonitor scan() {
		LOG.info("Start scanning '{}' folder", outboxFolder.getAbsolutePath());
		File[] files = Utils.ensureEmpty(outboxFolder.listFiles(fileFilter));
		Arrays.sort(files, outboxFileComparator);
		LOG.debug("Discovered {} file(s)", files.length);
		if (!Utils.isEmpty(files))
			try {
				processFiles(Arrays.asList(files));
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		return this;
	}

	@Override
	public FolderMonitor monitor() {
		new Thread(this, FolderMonitor.class.getSimpleName()).start();
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		LOG.info("Start monitoring '{}' folder", outboxFolder.getAbsolutePath());
		Path outboxPath = outboxFolder.toPath();
		try {
			outboxPath.register(watcher, ENTRY_CREATE);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
			throw new IllegalStateException(e);
		}
		while (true) {
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException e) {
				LOG.error(e.getMessage(), e);
				throw new IllegalStateException(e);
			}
			if (timer != null) {
				timer.cancel();
				timer = null;
			}
			LOG.info("Folder '{}' content changed", outboxFolder.getAbsolutePath());
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == OVERFLOW) continue;
				File patch = outboxPath.resolve(((WatchEvent<Path>) event).context()).toFile();
				if (fileFilter.accept(patch))
					files.add(patch);
			}
			if (!files.isEmpty()) {
				timer = new Timer();
				timer.schedule(new ProcessFilesTimerTask(), NEW_FILES_PROCESS_DELAY);
				LOG.debug("File processing timer is (re-)scheduled");
			}
			boolean valid = key.reset();
			if (!valid) {
				LOG.error("Path '{}' isn't valid anymore", outboxPath);
				postMessage(new Main.StopMessage(
						String.format("Please verify validity of folder '%s' and re-run application", outboxPath)));
				break;
			}
		}
	}
}
