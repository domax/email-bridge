package org.mail.git;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class Config {

	private final String ewsEmail;
	private final String ewsDomain;
	private final String ewsUsername;
	private final String ewsPassword;
	private final String ewsServer;

	private final String proxyHost;
	private final int proxyPort;
	private final String proxyUsername;
	private final String proxyPassword;
	private final String proxyDomain;

	private final String attachPassword;
	private final boolean attachGzip;

	public Config(String propertiesfileName) throws IOException {
		Properties config = new Properties();
		config.load(new FileInputStream(propertiesfileName));

		ewsEmail = config.getProperty("ews.email", "");
		ewsDomain = config.getProperty("ews.domain", "");
		ewsUsername = config.getProperty("ews.username", "");
		ewsPassword = config.getProperty("ews.password", "");
		ewsServer = config.getProperty("ews.server", "");

		proxyHost = config.getProperty("proxy.host", "");
		proxyPort = Integer.parseInt(config.getProperty("proxy.port", "80"));
		proxyUsername = config.getProperty("proxy.username", "");
		proxyPassword = config.getProperty("proxy.password", "");
		proxyDomain = config.getProperty("proxy.domain", "");

		attachPassword = config.getProperty("attach.password", "");
		attachGzip = Boolean.parseBoolean(config.getProperty("attach.gzip", "true"));
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

	public String getAttachPassword() {
		return attachPassword;
	}

	public boolean isAttachGzip() {
		return attachGzip;
	}

	@Override
	public String toString() {
		return "Config {" +
				"ewsEmail='" + ewsEmail + '\'' +
				", ewsDomain='" + ewsDomain + '\'' +
				", ewsUsername='" + ewsUsername + '\'' +
				", ewsPassword='" + ewsPassword + '\'' +
				", ewsServer='" + ewsServer + '\'' +
				", proxyHost='" + proxyHost + '\'' +
				", proxyPort=" + proxyPort +
				", proxyUsername='" + proxyUsername + '\'' +
				", proxyPassword='" + proxyPassword + '\'' +
				", proxyDomain='" + proxyDomain + '\'' +
				", attachPassword='" + attachPassword + '\'' +
				", attachGzip=" + attachGzip +
				'}';
	}
}
