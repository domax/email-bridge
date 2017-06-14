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

import org.mail.bridge.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class AbstractMonitor {

	private final Map<Class<? extends Message<?>>, List<MonitorCallback<?>>> callbacks = new HashMap<>();

	<T> AbstractMonitor addCallback(Class<? extends Message<T>> messageClass, MonitorCallback<T> callback) {
		if (callback != null) {
			List<MonitorCallback<?>> callbackList = callbacks.get(messageClass);
			if (callbackList == null) {
				callbackList = new ArrayList<>();
				callbacks.put(messageClass, callbackList);
			}
			callbackList.add(callback);
		}
		return this;
	}

	@SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
	<T> void postMessage(Message<T> message) {
		List callbackList = Utils.ensureEmpty(callbacks.get(message.getClass()));
		for (MonitorCallback<T> callback : (List<MonitorCallback<T>>) callbackList)
			callback.onMessage(message);
	}

	public abstract AbstractMonitor scan();

	public abstract AbstractMonitor monitor();

	public abstract AbstractMonitor stop();
}
