package org.mail.git;

import microsoft.exchange.webservices.data.autodiscover.IAutodiscoverRedirectionUrl;
import microsoft.exchange.webservices.data.autodiscover.exception.AutodiscoverLocalException;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.WebProxy;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.credential.WebProxyCredentials;
import microsoft.exchange.webservices.data.enumeration.ExchangeVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class ExchangeConnector implements MailConnector {

	private static final Logger LOG = LoggerFactory.getLogger(ExchangeConnector.class);

	private final ExchangeService service;

	public ExchangeConnector(Config config) {
		service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
//		service.setTraceEnabled(true);

		if (!config.getProxyHost().isEmpty())
			service.setWebProxy(new WebProxy(config.getProxyHost(), config.getProxyPort(),
					config.getProxyDomain().isEmpty() ? null : new WebProxyCredentials(
							config.getProxyUsername(), config.getProxyPassword(), config.getProxyDomain())));

		service.setCredentials(new WebCredentials(config.getEwsUsername(), config.getEwsPassword(), config.getEwsDomain()));
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
			throw new IllegalArgumentException(ex);
		}
		LOG.debug("instantiated");
	}

	public ExchangeService getService() {
		return service;
	}
}
