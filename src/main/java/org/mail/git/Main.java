package org.mail.git;

import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.enumeration.WellKnownFolderName;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		Config config = new Config(args[0]);
		ExchangeConnector exchangeConnector = new ExchangeConnector(config);

		Date d = exchangeConnector.getService().getPasswordExpirationDate(config.getEwsEmail());
		System.out.println("Password Expiration Date: " + d);
		System.exit(0);

		ItemView view = new ItemView(500);
		FindItemsResults<Item> findResults = null;
		for (int i = 0;
				 findResults == null || findResults.isMoreAvailable();
				 view.setOffset(view.getOffset() + view.getPageSize())) {
			findResults = exchangeConnector.getService().findItems(WellKnownFolderName.Inbox, view);
			for (Item item : findResults.getItems())
				System.out.println((++i) + ": " + item.getSubject());
		}
	}
}
