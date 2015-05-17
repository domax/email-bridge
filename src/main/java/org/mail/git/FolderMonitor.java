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
package org.mail.git;

import org.mail.git.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class FolderMonitor implements Runnable {

	public interface Callback {
		void onNewFile(File file) throws IOException;
	}

	private static final Logger LOG = LoggerFactory.getLogger(FolderMonitor.class);

	private final Config config;
	private final List<Callback> callbacks = new ArrayList<>();
	private final File outboxFolder;
	private final WatchService watcher;

	private final FileFilter fileFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			return file.isFile()
					&& file.canRead()
					&& (config.getOutboxFileRegexp().isEmpty() || file.getName().matches(config.getOutboxFileRegexp()));
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
		LOG.info("instantiated");
	}

	public FolderMonitor addCallback(Callback callback) {
		if (callback != null && !callbacks.contains(callback))
			callbacks.add(callback);
		return this;
	}

	private void processFile(File file) throws IOException {
		LOG.info("process file: " + file.getAbsolutePath());
		for (Callback callback : callbacks)
			callback.onNewFile(file);
		if (config.isOutboxCleanup() && !file.delete())
			LOG.warn("Cannot remove file '" + file.getAbsolutePath() + "'");
	}

	public FolderMonitor scanInbox() throws IOException {
		File[] files = Utils.ensureEmpty(outboxFolder.listFiles(fileFilter));
		Arrays.sort(files);
		LOG.debug("discovered files: " + Arrays.toString(files));
		for (File file : files)
			processFile(file);
		return this;
	}

	public FolderMonitor monitorInbox() {
		new Thread(this).start();
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		LOG.info("Start monitoring '" + outboxFolder.getAbsolutePath() + "'");
		Path inboxPath = outboxFolder.toPath();
		try {
			inboxPath.register(watcher, ENTRY_CREATE);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
			throw new IllegalStateException(ex);
		}
		while (true) {
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
				throw new IllegalStateException(ex);
			}
			LOG.info("'" + outboxFolder.getAbsolutePath() + "' content changed");
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == OVERFLOW) continue;
				File patch = inboxPath.resolve(((WatchEvent<Path>) event).context()).toFile();
				if (fileFilter.accept(patch))
					try {
						processFile(patch);
					} catch (IOException ex) {
						LOG.warn(ex.getMessage());
					}
			}
			boolean valid = key.reset();
			if (!valid) {
				LOG.error("Path '" + inboxPath + "' isn't valid anymore");
				break;
			}
		}
	}
}
