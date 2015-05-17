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

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl;
import microsoft.exchange.webservices.data.autodiscover.exception.AutodiscoverLocalException;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.WebProxy;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.credential.WebProxyCredentials;
import microsoft.exchange.webservices.data.enumeration.ExchangeVersion;
import microsoft.exchange.webservices.data.property.complex.MessageBody;
import org.mail.git.util.EncryptUtil;
import org.mail.git.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.Date;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class ExchangeConnector {

	private static final Logger LOG = LoggerFactory.getLogger(ExchangeConnector.class);

	private final Config config;
	private final ExchangeService service;

	public ExchangeConnector(Config config) {
		this.config = config;
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
		LOG.debug("instantiated");
	}

	public void sendFile(File file) {
		LOG.info("Sending file '" + file.getAbsolutePath() + "'");
		try {
			final EmailMessage msg = new EmailMessage(service);
			final Object[] params = {config.getEmailTag(), new Date(), file.getName()};
			msg.setSubject(Utils.makeTeaser(config.getEmailSubjectFormat().format(params), 78));
			msg.setBody(MessageBody.getMessageBodyFromText(config.getEmailBodyFormat().format(params) + "<br>"));
			for (String email : config.getEmailRecipientsTo())
				msg.getToRecipients().add(email);
			for (String email : config.getEmailRecipientsCc())
				msg.getCcRecipients().add(email);
			for (String email : config.getEmailRecipientsBcc())
				msg.getBccRecipients().add(email);
			String fileName = file.getName();
			if (config.isEmailAttachGzip()) fileName += ".gz";
			if (!config.getEmailAttachPassword().isEmpty()) fileName += ".enc";
			LOG.debug("Attachment file name: '" + fileName + "'");
			try (InputStream is = getAttachmentInputStream(file)) {
				msg.getAttachments().addFileAttachment(fileName, is);
			}
			msg.send();
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			throw new IllegalArgumentException(ex);
		}
	}

	private InputStream getAttachmentInputStream(final File file) throws IOException {
		if (!config.isEmailAttachGzip() && config.getEmailAttachPassword().isEmpty())
			return new BufferedInputStream(new FileInputStream(file));

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			if (config.isEmailAttachGzip() && !config.getEmailAttachPassword().isEmpty())
				EncryptUtil.gzipEncrypt(config.getEmailAttachPassword(), is, output);
			else if (!config.getEmailAttachPassword().isEmpty())
				EncryptUtil.encrypt(config.getEmailAttachPassword(), is, output);
			else EncryptUtil.gzip(is, output);
		}
		return new ByteArrayInputStream(output.toByteArray());
		//FIXME: use piped streams instead. Code below doesn't work properly
//
//		final PipedOutputStream output = new PipedOutputStream();
//		final PipedInputStream input = new PipedInputStream(output);
//		Thread thread = new Thread(new Runnable() {
//			@Override
//			public void run() {
//				try {
//					try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
//						if (config.isEmailAttachGzip() && !config.getEmailAttachPassword().isEmpty())
//							EncryptUtil.gzipEncrypt(config.getEmailAttachPassword(), is, output);
//						else if (!config.getEmailAttachPassword().isEmpty())
//							EncryptUtil.encrypt(config.getEmailAttachPassword(), is, output);
//						else EncryptUtil.gzip(is, output);
//						output.flush();
//						output.close();
//					}
//				} catch (IOException e) {
//					LOG.error(e.getMessage(), e);
//				}
//			}
//		});
//		thread.start();
//		return input;
	}
}
