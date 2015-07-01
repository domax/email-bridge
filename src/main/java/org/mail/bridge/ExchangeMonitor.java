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

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl;
import microsoft.exchange.webservices.data.autodiscover.exception.AutodiscoverLocalException;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.WebProxy;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.notification.EventType;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.service.DeleteMode;
import microsoft.exchange.webservices.data.core.response.GetItemResponse;
import microsoft.exchange.webservices.data.core.response.ServiceResponseCollection;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.credential.WebProxyCredentials;
import microsoft.exchange.webservices.data.notification.*;
import microsoft.exchange.webservices.data.property.complex.*;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.mail.bridge.util.EncryptUtil;
import org.mail.bridge.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class ExchangeMonitor extends AbstractMonitor implements
		StreamingSubscriptionConnection.INotificationEventDelegate,
		StreamingSubscriptionConnection.ISubscriptionErrorDelegate {

	private static final Logger LOG = LoggerFactory.getLogger(ExchangeMonitor.class);
	private static final Pattern RE_ZIP_VOL =
			Pattern.compile("^([0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12})_(\\d+)\\.z\\d{2}$", Pattern.CASE_INSENSITIVE);
	private static final String ZIP_EXT = ".z00";

	public static class NewMailMessage extends Message<List<ItemId>> {
		public NewMailMessage(List<ItemId> emails) {
			super(emails);
		}
	}

	public static class ReopenMonitorMessage extends Message<Void> {
		public ReopenMonitorMessage() {
			super(null);
		}
	}

	public static class NewIncomingFilesMessage extends Message<List<File>> {
		public NewIncomingFilesMessage(List<File> files) {
			super(files);
		}
	}

	private final Config config;
	private ExchangeService service;

	public ExchangeMonitor(Config config) {
		this.config = config;
		LOG.debug("Instantiated");
	}

	private void openConnection() {
		if (service != null) {
			LOG.debug("Connection to Exchange server was already established");
			return;
		}
		service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
//		service.setTraceEnabled(true);
		if (!config.getProxyHost().isEmpty())
			service.setWebProxy(new WebProxy(config.getProxyHost(), config.getProxyPort(),
					config.getProxyDomain().isEmpty() ? null : new WebProxyCredentials(
							config.getProxyUsername(), config.getProxyPassword(), config.getProxyDomain())));
		service.setCredentials(
				new WebCredentials(config.getEwsUsername(), config.getEwsPassword(), config.getEwsDomain()));
		try {
			if (config.getEwsServer().isEmpty()) {
				service.autodiscoverUrl(config.getEwsEmail(), new IAutodiscoverRedirectionUrl() {
					public boolean autodiscoverRedirectionUrlValidationCallback(String redirectionUrl)
							throws AutodiscoverLocalException {
						return redirectionUrl.toLowerCase().startsWith("https://");
					}
				});
			} else service.setUrl(new URI(config.getEwsServer()));
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			throw new IllegalArgumentException(ex);
		}
	}

	public ExchangeMonitor addStopCallback(MonitorCallback<String> callback) {
		return (ExchangeMonitor) addCallback(Main.StopMessage.class, callback);
	}

	public ExchangeMonitor addNewMailCallback(MonitorCallback<List<ItemId>> callback) {
		return (ExchangeMonitor) addCallback(NewMailMessage.class, callback);
	}

	public ExchangeMonitor addReopenMonitorCallback(MonitorCallback<Void> callback) {
		return (ExchangeMonitor) addCallback(ReopenMonitorMessage.class, callback);
	}

	public ExchangeMonitor addIncomingFilesReadyCallback(MonitorCallback<List<File>> callback) {
		return (ExchangeMonitor) addCallback(NewIncomingFilesMessage.class, callback);
	}

	private List<File> processEmail(EmailMessage emailMessage) {
		List<File> attachFiles = new LinkedList<>();
		try {
			String subject = emailMessage.getSubject();
			LOG.info("Processing email message with subject '{}'", subject);
			emailMessage = EmailMessage.bind(service, emailMessage.getId(), new PropertySet(ItemSchema.Attachments));
			for (Attachment a : emailMessage.getAttachments())
				if (a instanceof FileAttachment) {
					File file = downloadAttachment((FileAttachment) a);
					final Matcher matcher = RE_ZIP_VOL.matcher(file.getName());
					if (matcher.matches()) {
						LOG.debug("New volume detected: '{}'", file.getName());
						int expectedCount = Integer.parseInt(matcher.group(3));
						File dir = file.getParentFile();
						File[] parts = Utils.ensureEmpty(dir.listFiles(new FilenameFilter() {
							@Override
							public boolean accept(File dir, String name) {
								return name.startsWith(matcher.group(1) + "_" + matcher.group(3) + ".z");
							}
						}));
						if (parts.length == expectedCount) {
							LOG.info("All {} volumes received, extracting files", expectedCount);
							File unzipDir = Files.createTempDirectory("eb-unzip-").toFile();
							LOG.debug("Created temporary folder '{}' to unZIP volumes", unzipDir.getAbsolutePath());

							final ZipFile zip = new ZipFile(new File(dir, matcher.group(1) + "_" + matcher.group(3) + ZIP_EXT));
							zip.extractAll(unzipDir.getAbsolutePath());
							for (File part : parts)
								if (part.delete()) LOG.debug("Part file '{}' was successfully removed", part.getAbsolutePath());
								else LOG.warn("Cannot remove part file '{}'", part.getAbsolutePath());

							attachFiles.addAll(extractAttachmentFiles(unzipDir));
							removeTempDir(unzipDir);
						} else LOG.debug("Only {} volume(s) of {} received, waiting for the rest", parts.length, expectedCount);
					} else attachFiles.add(file);
				}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return attachFiles;
	}

	private List<File> extractAttachmentFiles(File dir) throws IOException {
		final List<File> result = new LinkedList<>();
		if (dir == null || !dir.exists() || !dir.isDirectory()) return result;

		String extGz = config.getEmailAttachExtGzip();
		String extEnc = config.getEmailAttachExtEnc();

		for (File file : Utils.ensureEmpty(dir.listFiles())) {
			LOG.debug("Extracting file '{}'", file.getAbsolutePath());
			String fileName = file.getName();
			final boolean isEncrypted = fileName.endsWith(extEnc);
			if (isEncrypted) fileName = fileName.substring(0, fileName.length() - extEnc.length());
			final boolean isGzipped = fileName.endsWith(extGz);
			if (isGzipped) fileName = fileName.substring(0, fileName.length() - extGz.length());
			File extractFile = new File(config.getInboxFolder(), fileName);
			try (final InputStream is = new BufferedInputStream(new FileInputStream(file));
					 final OutputStream os = new BufferedOutputStream(new FileOutputStream(extractFile))) {
				if (isEncrypted && isGzipped) EncryptUtil.decryptGunzip(config.getEmailAttachPassword(), is, os);
				else if (isEncrypted) EncryptUtil.decrypt(config.getEmailAttachPassword(), is, os);
				else if (isGzipped) EncryptUtil.gunzip(is, os);
				else EncryptUtil.copy(is, os);
			}
			result.add(extractFile);
			LOG.info("A file '{}' was extracted", extractFile.getName());
		}
		return result;
	}

	private File downloadAttachment(final FileAttachment attach) throws Exception {
		String extGz = config.getEmailAttachExtGzip();
		String extEnc = config.getEmailAttachExtEnc();

		String fileName = attach.getName();
		LOG.debug("Found file attachment with name '{}'", fileName);
		final boolean isEncrypted = fileName.endsWith(extEnc);
		if (isEncrypted) fileName = fileName.substring(0, fileName.length() - extEnc.length());
		final boolean isGzipped = fileName.endsWith(extGz);
		if (isGzipped) fileName = fileName.substring(0, fileName.length() - extGz.length());
		File attachFile = new File(config.getInboxFolder(), fileName);

		final PipedInputStream input = new PipedInputStream();
		final PipedOutputStream output = new PipedOutputStream(input);
		try (final OutputStream os = new BufferedOutputStream(new FileOutputStream(attachFile))) {
			final Thread threadEnc = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						if (isEncrypted && isGzipped) EncryptUtil.decryptGunzip(config.getEmailAttachPassword(), input, os);
						else if (isEncrypted) EncryptUtil.decrypt(config.getEmailAttachPassword(), input, os);
						else if (isGzipped) EncryptUtil.gunzip(input, os);
						else EncryptUtil.copy(input, os);
					} catch (IOException e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}, "downloadAttachment-threadEnc");
			final Thread threadLoad = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						attach.load(output);
						output.close();
					} catch (Exception e) {
						LOG.error(e.getMessage(), e);
					}
				}
			}, "downloadAttachment-threadLoad");
			threadEnc.start();
			threadLoad.start();
			threadEnc.join();
		}
		LOG.info("Attachment was written into file '{}'", attachFile.getAbsolutePath());
		return attachFile;
	}

	private boolean isSubjectMatched(String subject) {
		try {
			Object[] params = config.getEmailSubjectFormat().parse(subject);
			if (!Utils.isEmpty(params) && config.getEmailTagIncoming().equals(params[0])) {
				LOG.debug("Subject '{}' is matched for processing", subject);
				return true;
			} else LOG.debug("Subject '{}' doesn't match to email tag", subject);
		} catch (ParseException ignored) {
			LOG.trace("Subject '{}' doesn't match to subject pattern", subject);
		}
		return false;
	}

	private void removeEmails(List<EmailMessage> emails) throws Exception {
		if (Utils.isEmpty(emails) || !config.isEmailInboxCleanup()) return;
		LOG.info("Removing {} processed messages", emails.size());
		for (EmailMessage emailMessage : emails) {
			LOG.debug("Removing email message with subject '{}'", emailMessage.getSubject());
			emailMessage.delete(DeleteMode.HardDelete);
		}
	}

	@Override
	public synchronized ExchangeMonitor scan() {
		LOG.info("Start scanning '{}' mail folder", WellKnownFolderName.Inbox);
		openConnection();
		try {
			final ItemView view = new ItemView(config.getEwsViewSize());
			final List<File> inboxFiles = new LinkedList<>();
			for (FindItemsResults<Item> findResults = null;
					 findResults == null || findResults.isMoreAvailable();
					 view.setOffset(view.getOffset() + view.getPageSize())) {
				findResults = service.findItems(WellKnownFolderName.Inbox, view);
				final List<EmailMessage> processedEmails = new LinkedList<>();
				for (Item item : findResults.getItems())
					if (isSubjectMatched(item.getSubject())) {
						inboxFiles.addAll(processEmail((EmailMessage) item));
						processedEmails.add((EmailMessage) item);
					}
				removeEmails(processedEmails);
			}
			if (!inboxFiles.isEmpty())
				postMessage(new NewIncomingFilesMessage(inboxFiles));
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return this;
	}

	@Override
	public synchronized ExchangeMonitor monitor() {
		LOG.info("Start monitoring '{}' mail folder", WellKnownFolderName.Inbox);
		openConnection();
			try {
				StreamingSubscription subscription = service.subscribeToStreamingNotifications(
						Collections.singletonList(new FolderId(WellKnownFolderName.Inbox)), EventType.NewMail);
				LOG.debug("Setup streaming connection");
				StreamingSubscriptionConnection subscriptionConn =
						new StreamingSubscriptionConnection(service, config.getEwsSubscriptionLifetime());
				subscriptionConn.addSubscription(subscription);
				subscriptionConn.addOnNotificationEvent(this);
				subscriptionConn.addOnDisconnect(this);
				subscriptionConn.open();
				LOG.debug("Streaming connection opened");
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
				postMessage(new Main.StopMessage(
						"Streaming Subscription cannot be setup. Please verify settings and re-run application."));
			}
		return this;
	}

	@Override
	public ExchangeMonitor stop() {
		LOG.info("Stop connection to EWS server");
		service.close();
		service = null;
		return this;
	}

	@Override
	public void notificationEventDelegate(Object sender, NotificationEventArgs args) {
		LOG.debug("Streaming subscription received notification");
		List<ItemId> newMailsIds = new ArrayList<>();
		for (NotificationEvent itemEvent : args.getEvents())
			if (itemEvent instanceof ItemEvent)
				newMailsIds.add(((ItemEvent) itemEvent).getItemId());
		if (newMailsIds.isEmpty()) {
			LOG.debug("There was nothing interesting");
		} else postMessage(new NewMailMessage(newMailsIds));
	}

	@Override
	public void subscriptionErrorDelegate(Object sender, SubscriptionErrorEventArgs args) {
		LOG.warn("Streaming subscription is disconnected", args.getException());
		if (service != null)
			postMessage(new ReopenMonitorMessage());
	}

	public synchronized void sendFiles(List<File> files) {
		if (Utils.isEmpty(files)) return;
		LOG.info("Sending files '{}'", files);

		File tempDir = null;
		File zipDir = null;
		long attachSize = 0;
		long maxSize = config.getEmailAttachMaxSize() * 1024 * 1024;

		// Prepare all whole attachment files that are ready for sending in temp dir
		try {
			tempDir = Files.createTempDirectory("eb-attach-").toFile();
			LOG.debug("Created temporary folder '{}' for file attachments", tempDir.getAbsolutePath());
			for (File file : files) {
				File attachFile = prepareFileAttachment(tempDir, file);
				attachSize += attachFile.length();
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		if (tempDir == null) return;

		if (attachSize > maxSize) {
			// Pack attachment files into ZIP archive divided by volumes
			try {
				zipDir = Files.createTempDirectory("eb-zip-").toFile();
				LOG.debug("Created temporary folder '{}' for ZIP volumes", zipDir.getAbsolutePath());
				ZipFile zip = new ZipFile(new File(zipDir, UUID.randomUUID().toString() + ZIP_EXT));
				ZipParameters parameters = new ZipParameters();
				parameters.setCompressionMethod(Zip4jConstants.COMP_STORE);
				parameters.setIncludeRootFolder(false);
				zip.createZipFileFromFolder(tempDir, parameters, true, maxSize);
				LOG.debug("ZIP volumes created: {}", zip.getSplitZipFiles());
			} catch (ZipException | IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}

		int messages = 0;
		try {
			openConnection();
			if (zipDir != null) messages += sendFilesFromDirOnePerEmail(zipDir);
			else messages += sendFilesFromDirAsOneEmail(tempDir);
		} finally {
			// Remove all temporary files
			removeTempDir(tempDir);
			removeTempDir(zipDir);
		}

		if (config.isOutboxCleanup()) {
			LOG.debug("Outbox is configured to auto-cleanup: {} file(s) to remove.", files.size());
			for (File file : files) {
				if (file.delete()) LOG.debug("File '{}' was successfully removed", file.getAbsolutePath());
				else LOG.warn("Cannot remove file '{}'", file.getAbsolutePath());
			}
		}

		LOG.info("Sent {} message(s)", messages);
	}

	private EmailMessage createEmailMessage() throws Exception {
		final EmailMessage msg = new EmailMessage(service);
		for (String email : config.getEmailRecipientsTo())
			msg.getToRecipients().add(email);
		for (String email : config.getEmailRecipientsCc())
			msg.getCcRecipients().add(email);
		for (String email : config.getEmailRecipientsBcc())
			msg.getBccRecipients().add(email);
		return msg;
	}

	private int sendFilesFromDirAsOneEmail(File dir) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
		File[] files = dir.listFiles();
		if (Utils.isEmpty(files)) return 0;
		try {
			final EmailMessage msg = createEmailMessage();
			final StringBuilder bodyBuilder = new StringBuilder();
			final StringBuilder subjectBuilder = new StringBuilder();
			for (File file : files) {
				final Object[] params = {config.getEmailTagOutgoing(), new Date(), file.getName()};
				if (subjectBuilder.length() > 0) subjectBuilder.append(" ");
				subjectBuilder.append(config.getEmailSubjectFormat().format(params));
				if (bodyBuilder.length() > 0) bodyBuilder.append("\n");
				bodyBuilder.append(config.getEmailBodyFormat().format(params));
				msg.getAttachments().addFileAttachment(file.getAbsolutePath());
			}
			msg.setSubject(Utils.makeTeaser(subjectBuilder.toString(), 78, "..."));
			msg.setBody(MessageBody.getMessageBodyFromText(bodyBuilder.toString()));
			msg.send();
			LOG.debug("Email with subject '{}' was successfully sent", msg.getSubject());
			return 1;
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			return 0;
		}
	}

	private int sendFilesFromDirOnePerEmail(File dir) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
		File[] files = dir.listFiles();
		if (Utils.isEmpty(files)) return 0;
		int messages = 0;
		try {
			for (File file : files) {
				final EmailMessage msg = createEmailMessage();
				String fileName = file.getName().replaceFirst("\\.", "_" + files.length + ".");
				final Object[] params = {config.getEmailTagOutgoing(), new Date(), fileName};
				msg.setSubject(Utils.makeTeaser(config.getEmailSubjectFormat().format(params), 78, "..."));
				msg.setBody(MessageBody.getMessageBodyFromText(config.getEmailBodyFormat().format(params)));
				msg.getAttachments().addFileAttachment(fileName, file.getAbsolutePath());
				msg.send();
				LOG.debug("Email with subject '{}' was successfully sent", msg.getSubject());
				++messages;
				sleep(100);
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return messages;
	}

	private File prepareFileAttachment(File folder, File file) throws IOException {
		String fileName = file.getName();
		if (config.isEmailAttachGzip()) fileName += config.getEmailAttachExtGzip();
		if (!config.getEmailAttachPassword().isEmpty()) fileName += config.getEmailAttachExtEnc();
		LOG.debug("Preparing file attachment with name '{}'", fileName);
		File attachFile = new File(folder, fileName);
		try (InputStream is = new BufferedInputStream(new FileInputStream(file));
				 OutputStream os = new BufferedOutputStream(new FileOutputStream(attachFile))) {
			if (config.isEmailAttachGzip() && !config.getEmailAttachPassword().isEmpty())
				EncryptUtil.gzipEncrypt(config.getEmailAttachPassword(), is, os);
			else if (!config.getEmailAttachPassword().isEmpty())
				EncryptUtil.encrypt(config.getEmailAttachPassword(), is, os);
			else if (config.isEmailAttachGzip())
				EncryptUtil.gzip(is, os);
			else
				EncryptUtil.copy(is, os);
		}
		return attachFile;
	}

	private void removeTempDir(File dir) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) return;
		File[] files = Utils.ensureEmpty(dir.listFiles());
		for (File file : files)
			if (file.delete()) LOG.debug("Temporary file '{}' was successfully removed", file.getAbsolutePath());
			else LOG.warn("Cannot remove temporary file '{}'", file.getAbsolutePath());
		// Let's give a chance to FS to remove files before removing temp folder itself
		try {
			sleep(100);
		} catch (InterruptedException e) {
			LOG.error(e.getMessage(), e);
		}
		if (dir.delete()) LOG.debug("Temporary folder '{}' was successfully removed", dir.getAbsolutePath());
		else LOG.error("Cannot remove temporary folder '{}'", dir.getAbsolutePath());
	}

	public synchronized void processNewMail(List<ItemId> newMailsIds) {
		LOG.info("Start new mail processing - {} message(s)", newMailsIds.size());
		try {
			ServiceResponseCollection<GetItemResponse> responses =
					service.bindToItems(newMailsIds, new PropertySet(ItemSchema.Subject));
			final List<File> inboxFiles = new LinkedList<>();
			final List<EmailMessage> processedEmails = new LinkedList<>();
			for (GetItemResponse response : responses) {
				Item item = response.getItem();
				if (item instanceof EmailMessage && isSubjectMatched(item.getSubject())) {
					inboxFiles.addAll(processEmail((EmailMessage) item));
					processedEmails.add((EmailMessage) item);
				}
			}
			removeEmails(processedEmails);
			if (!inboxFiles.isEmpty())
				postMessage(new NewIncomingFilesMessage(inboxFiles));
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
