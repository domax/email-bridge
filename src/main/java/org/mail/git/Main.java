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

//import microsoft.exchange.webservices.data.core.service.item.Item;
//import microsoft.exchange.webservices.data.enumeration.WellKnownFolderName;
//import microsoft.exchange.webservices.data.search.FindItemsResults;
//import microsoft.exchange.webservices.data.search.ItemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		Config config = new Config(args[0]);
		final ExchangeConnector exchangeConnector = new ExchangeConnector(config);

//		Date d = exchangeConnector.getService().getPasswordExpirationDate(config.getEwsEmail());
//		System.out.println("Password Expiration Date: " + d);

//		ItemView view = new ItemView(500);
//		FindItemsResults<Item> findResults = null;
//		for (int i = 0;
//				 findResults == null || findResults.isMoreAvailable();
//				 view.setOffset(view.getOffset() + view.getPageSize())) {
//			findResults = exchangeConnector.getService().findItems(WellKnownFolderName.Inbox, view);
//			for (Item item : findResults.getItems())
//				System.out.println((++i) + ": " + item.getSubject());
//		}

		new FolderMonitor(config).addCallback(new FolderMonitor.Callback() {
			@Override
			public void onNewFile(File file) throws IOException {
				exchangeConnector.sendFile(file);
			}
		}).scanInbox().monitorInbox();
	}
}
