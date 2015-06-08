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
import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class Config {

	private static final Logger LOG = LoggerFactory.getLogger(Config.class);
	private static final String DEF_SUBJ = "[{0}]@{1,date,yyyy-MM-dd'T'HH:mm:ssZ}/{2}";
	private static final String DEF_BODY = "Transporting file \"{2}\"<br>";
	private static final String[] NO_ADDR = new String[0];

	private final String ewsEmail;
	private final String ewsDomain;
	private final String ewsUsername;
	private final String ewsPassword;
	private final String ewsServer;
	private final int ewsViewSize;
	private final int ewsSubscriptionLifetime;

	private final String proxyHost;
	private final int proxyPort;
	private final String proxyUsername;
	private final String proxyPassword;
	private final String proxyDomain;

	private final String outboxFolder;
	private final boolean outboxCleanup;
	private final String outboxFileRegexp;
	private final String inboxFolder;

	private final String emailTag;
	private final MessageFormat emailSubjectFormat;
	private final MessageFormat emailBodyFormat;
	private final String[] emailRecipientsTo;
	private final String[] emailRecipientsCc;
	private final String[] emailRecipientsBcc;
	private final boolean emailInboxCleanup;
	private final String emailAttachPassword;
	private final boolean emailAttachGzip;
	private final String emailAttachExtGzip;
	private final String emailAttachExtEnc;

	public Config(String propertiesFileName) throws IOException {
		Properties config = new Properties();
		config.load(new FileInputStream(propertiesFileName));
		String s;
		int i;
		MessageFormat mf;

		ewsEmail = config.getProperty("ews.email", "");
		ewsDomain = config.getProperty("ews.domain", "");
		ewsUsername = config.getProperty("ews.username", "");
		ewsPassword = config.getProperty("ews.password", "");
		ewsServer = config.getProperty("ews.server", "");
		s = config.getProperty("ews.view.size", "");
		ewsViewSize = s.isEmpty() ? 500 : Integer.parseInt(s);
		s = config.getProperty("ews.subscription.lifetime", "");
		i = s.isEmpty() ? 10 : Integer.parseInt(s);
		if (i < 1 || i > 30) i = 10;
		ewsSubscriptionLifetime = i;

		proxyHost = config.getProperty("proxy.host", "");
		s = config.getProperty("proxy.port", "");
		proxyPort = s.isEmpty() ? 80 : Integer.parseInt(s);
		proxyUsername = config.getProperty("proxy.username", "");
		proxyPassword = config.getProperty("proxy.password", "");
		proxyDomain = config.getProperty("proxy.domain", "");

		s = config.getProperty("outbox.folder", "");
		outboxFolder = s.isEmpty() ? System.getProperty("java.io.tmpdir") + File.separator + "outbox" : s;
		s = config.getProperty("outbox.cleanup", "");
		outboxCleanup = s.isEmpty() || Boolean.parseBoolean(s);
		outboxFileRegexp = config.getProperty("outbox.file.regexp", "");
		s = config.getProperty("inbox.folder", "");
		inboxFolder = s.isEmpty() ? System.getProperty("java.io.tmpdir") + File.separator + "inbox" : s;

		s = config.getProperty("email.tag", "");
		emailTag = s.isEmpty() ? "git-ews" : s;

		s = config.getProperty("email.subject.format", "");
		if (s.isEmpty()) mf = new MessageFormat(DEF_SUBJ);
		else try {
			mf = new MessageFormat(s);
		} catch (IllegalArgumentException ex) {
			LOG.warn("Fallback to default subject format, b/c of error: " + ex.getMessage());
			mf = new MessageFormat(DEF_SUBJ);
		}
		emailSubjectFormat = mf;

		s = config.getProperty("email.body.format", "");
		if (s.isEmpty()) mf = new MessageFormat(DEF_BODY);
		else try {
			mf = new MessageFormat(s);
		} catch (IllegalArgumentException ex) {
			LOG.warn("Fallback to default body format, b/c of error: " + ex.getMessage());
			new MessageFormat(DEF_BODY);
		}
		emailBodyFormat = mf;

		s = config.getProperty("email.inbox.cleanup", "");
		emailInboxCleanup = s.isEmpty() || Boolean.parseBoolean(s);

		s = config.getProperty("email.recipients.to", "");
		emailRecipientsTo = s.isEmpty() ? NO_ADDR : Utils.ensureEmpty(s.split("\\s*,\\s*"));
		s = config.getProperty("email.recipients.cc", "");
		emailRecipientsCc = s.isEmpty() ? NO_ADDR : Utils.ensureEmpty(s.split("\\s*,\\s*"));
		s = config.getProperty("email.recipients.bcc", "");
		emailRecipientsBcc = s.isEmpty() ? NO_ADDR : Utils.ensureEmpty(s.split("\\s*,\\s*"));

		emailAttachPassword = config.getProperty("email.attach.password", "");
		s = config.getProperty("email.attach.gzip", "");
		emailAttachGzip = !s.isEmpty() && Boolean.parseBoolean(s);

		s = config.getProperty("email.attach.ext.gzip", "");
		emailAttachExtGzip = s.isEmpty() ? ".gz" : s;
		s = config.getProperty("email.attach.ext.enc", "");
		emailAttachExtEnc = s.isEmpty() ? ".enc" : s;
	}

	public String getEwsEmail() {
		return ewsEmail;
	}

	public String getEwsDomain() {
		return ewsDomain;
	}

	public String getEwsUsername() {
		return ewsUsername;
	}

	public String getEwsPassword() {
		return ewsPassword;
	}

	public String getEwsServer() {
		return ewsServer;
	}

	public int getEwsViewSize() {
		return ewsViewSize;
	}

	public int getEwsSubscriptionLifetime() {
		return ewsSubscriptionLifetime;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public String getProxyUsername() {
		return proxyUsername;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public String getProxyDomain() {
		return proxyDomain;
	}

	public String getOutboxFolder() {
		return outboxFolder;
	}

	public boolean isOutboxCleanup() {
		return outboxCleanup;
	}

	public String getOutboxFileRegexp() {
		return outboxFileRegexp;
	}

	public String getInboxFolder() {
		return inboxFolder;
	}

	public boolean isEmailInboxCleanup() {
		return emailInboxCleanup;
	}

	public String getEmailTag() {
		return emailTag;
	}

	public MessageFormat getEmailSubjectFormat() {
		return emailSubjectFormat;
	}

	public MessageFormat getEmailBodyFormat() {
		return emailBodyFormat;
	}

	public String[] getEmailRecipientsTo() {
		return emailRecipientsTo;
	}

	public String[] getEmailRecipientsCc() {
		return emailRecipientsCc;
	}

	public String[] getEmailRecipientsBcc() {
		return emailRecipientsBcc;
	}

	public String getEmailAttachPassword() {
		return emailAttachPassword;
	}

	public boolean isEmailAttachGzip() {
		return emailAttachGzip;
	}

	public String getEmailAttachExtGzip() {
		return emailAttachExtGzip;
	}

	public String getEmailAttachExtEnc() {
		return emailAttachExtEnc;
	}

	@Override
	public String toString() {
		return "Config {" +
				"\n\tewsEmail='" + ewsEmail + '\'' +
				",\n\tewsDomain='" + ewsDomain + '\'' +
				",\n\tewsUsername='" + ewsUsername + '\'' +
				",\n\tewsPassword='********'" +
				",\n\tewsServer='" + ewsServer + '\'' +
				",\n\tewsViewSize=" + ewsViewSize +
				",\n\tewsSubscriptionLifetime=" + ewsSubscriptionLifetime +
				",\n\tproxyHost='" + proxyHost + '\'' +
				",\n\tproxyPort=" + proxyPort +
				",\n\tproxyUsername='" + proxyUsername + '\'' +
				",\n\tproxyPassword='" + proxyPassword + '\'' +
				",\n\tproxyDomain='" + proxyDomain + '\'' +
				",\n\toutboxFolder='" + outboxFolder + '\'' +
				",\n\toutboxCleanup=" + outboxCleanup +
				",\n\toutboxFileRegexp='" + outboxFileRegexp + '\'' +
				",\n\tinboxFolder='" + inboxFolder + '\'' +
				",\n\temailTag='" + emailTag + '\'' +
				",\n\temailSubjectFormat='" + emailSubjectFormat.toPattern() + '\'' +
				",\n\temailBodyFormat='" + emailBodyFormat.toPattern() + '\'' +
				",\n\temailInboxCleanup=" + emailInboxCleanup +
				",\n\temailRecipientsTo=" + Arrays.toString(emailRecipientsTo) +
				",\n\temailRecipientsCc=" + Arrays.toString(emailRecipientsCc) +
				",\n\temailRecipientsBcc=" + Arrays.toString(emailRecipientsBcc) +
				",\n\temailAttachPassword='" + Utils.repeat("*", emailAttachPassword.length()) + '\'' +
				",\n\temailAttachGzip=" + emailAttachGzip +
				",\n\temailAttachExtGzip='" + emailAttachExtGzip + '\'' +
				",\n\temailAttachExtEnc='" + emailAttachExtEnc + '\'' +
				'}';
	}
}
