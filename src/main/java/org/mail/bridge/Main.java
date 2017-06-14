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

import microsoft.exchange.webservices.data.property.complex.ItemId;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class Main implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	static class StopMessage extends Message<String> {
		StopMessage(String stopMessage) {
			super(stopMessage);
		}
	}

	private final FolderMonitor folderMonitor;
	private final ExchangeMonitor exchangeMonitor;
	private final ConcurrentLinkedDeque<Message<?>> messages = new ConcurrentLinkedDeque<>();
	private final File pidFile;

	public static void main(String[] args) throws Exception {
		ArgumentParser parser = ArgumentParsers.newArgumentParser(Main.class.getSimpleName());
		parser.addArgument("-f", "--config")
				.required(true)
				.help("path to file with configuration properties");
		Namespace res = parser.parseArgsOrFail(args);
		final Config config = new Config(res.getString("config"));
		LOG.debug("Config settings: {}", config);
		new Main(config);
	}

	private Main(Config config) throws IOException {
		folderMonitor = new FolderMonitor(config)
				.addStopCallback(new MonitorCallback<String>() {
					@Override
					public void onMessage(Message<String> message) {
						postMessage(message);
					}
				})
				.addSendFileCallback(new MonitorCallback<List<File>>() {
					@Override
					public void onMessage(Message<List<File>> message) {
						postMessage(message);
					}
				});
		exchangeMonitor = new ExchangeMonitor(config)
				.addStopCallback(new MonitorCallback<String>() {
					@Override
					public void onMessage(Message<String> message) {
						postMessage(message);
					}
				})
				.addNewMailCallback(new MonitorCallback<List<ItemId>>() {
					@Override
					public void onMessage(Message<List<ItemId>> message) {
						postMessage(message);
					}
				})
				.addIncomingFilesReadyCallback(new MonitorCallback<List<File>>() {
					@Override
					public void onMessage(Message<List<File>> message) {
						postMessage(message);
					}
				})
				.addReopenMonitorCallback(new MonitorCallback<Void>() {
					@Override
					public void onMessage(Message<Void> message) {
						postMessage(message);
					}
				});
		pidFile = config.getPidFile().isEmpty() ? null : new File(config.getPidFile());
		if (pidFile != null) {
			if (pidFile.exists() && pidFile.delete()) LOG.debug("Old PID file was removed");
			if (!config.isPidFileKeep()) pidFile.deleteOnExit();
		}
		new Thread(this, Main.class.getSimpleName()).start();
	}

	private synchronized void postMessage(Message<?> message) {
		messages.addLast(message);
	}

	@Override
	public void run() {
		if (pidFile != null) {
			long pid;
			try {
				pid = Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0]);
			} catch (Exception e) {
				LOG.error("Cannot retrieve the PID of current process", e);
				return;
			}
			try (FileOutputStream os = new FileOutputStream(pidFile)) {
				os.write(String.valueOf(pid).getBytes());
				os.flush();
			} catch (Exception e) {
				LOG.error("Cannot cannot write PID file", e);
				return;
			}
		}

		exchangeMonitor.scan().monitor();
		folderMonitor.scan().monitor();
		while (true) {
			try {
				while (!messages.isEmpty()) {
					Message<?> message = messages.removeFirst();
					LOG.debug("Message received: {}", message);
					if (message instanceof ExchangeMonitor.NewMailMessage)
						exchangeMonitor.processNewMail(((ExchangeMonitor.NewMailMessage) message).getData());
					else if (message instanceof ExchangeMonitor.NewIncomingFilesMessage)
						folderMonitor.runScriptAgainstReceivedFiles(((ExchangeMonitor.NewIncomingFilesMessage) message).getData());
					else if (message instanceof ExchangeMonitor.ReopenMonitorMessage)
						exchangeMonitor.scan().monitor();
					else if (message instanceof FolderMonitor.SendFileMessage)
						exchangeMonitor.sendFiles(((FolderMonitor.SendFileMessage) message).getData());
					else if (message instanceof StopMessage) {
						System.out.println(((StopMessage) message).getData());
						exchangeMonitor.stop();
						folderMonitor.stop();
						Thread.currentThread().interrupt();
						break;
					} else LOG.warn("Unsupported message {}", message);
					Thread.sleep(10);
				}
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				LOG.error(e.getMessage(), e);
				break;
			}
		}


	}
}
