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
package org.mail.bridge.util;

import java.io.File;
import java.util.*;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
@SuppressWarnings({"unused", "WeakerAccess", "SameParameterValue"})
public class Utils {

	public static final Comparator<File> LAST_MODIFIED_COMPARATOR = new Comparator<File>() {
		@Override
		public int compare(File o1, File o2) {
			return (int) (o1.lastModified() - o2.lastModified());
		}
	};

	/**
	 * Helper to check if the String is {@code null} or empty.<br/>
	 * {@link String#isEmpty()} is not static and therefore require additional check for {@code null}.
	 *
	 * @param string
	 *          A string to be checked
	 * @return {@code true} if is not {@code null} and is not empty. {@code false} otherwise.
	 */
	public static boolean isEmpty(String string) {
		return string == null || string.length() == 0;
	}

	/**
	 * Helper to check if the array is {@code null} or empty.<br/>
	 *
	 * @param array
	 *          An array to be checked
	 * @return {@code true} if is not {@code null} and contains at least one element. {@code false} otherwise.
	 */
	public static boolean isEmpty(Object[] array) {
		return array == null || array.length == 0;
	}

	/**
	 * Helper to check if the collection is {@code null} or empty.<br/>
	 * {@link Collection#isEmpty()} is not static and therefore require additional check for {@code null}.
	 *
	 * @param collection
	 *          A collection to be checked
	 * @return {@code true} if is not {@code null} and contains at least one element. {@code false} otherwise.
	 */
	public static boolean isEmpty(Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	/**
	 * Helper to check if the map is {@code null} or empty.<br/>
	 * {@link Map#isEmpty()} is not static and therefore require additional check for {@code null}.
	 *
	 * @param map
	 *          A map to be checked
	 * @return {@code true} if is not {@code null} and contains at least one element. {@code false} otherwise.
	 */
	public static boolean isEmpty(Map<?, ?> map) {
		return map == null || map.isEmpty();
	}

	/**
	 * Helper to check if the String is {@code null} or empty or contains any kind of spaces ("\s" regexp pattern) only.
	 *
	 * @param string
	 *          A string to be checked
	 * @return {@code true} if is not {@code null} and contains anything but whitespaces. {@code false} otherwise.
	 */
	public static boolean isHollow(String string) {
		return isEmpty(string) || string.matches("^\\s+$");
	}

	/**
	 * Returns empty (not {@code null}) string in case if given one is {@code null}.
	 *
	 * @param string source string to be returned
	 * @return ensures given string is not {@code null}
	 */
	public static String ensureEmpty(String string) {
		return string == null ? "" : string;
	}

	/**
	 * Returns {@code null} string in case if given one is {@code null} or empty.
	 *
	 * @param string source string to be returned
	 * @return ensures given string is {@code null}
	 */
	public static String ensureNull(String string) {
		return isEmpty(string) ? null : string;
	}

	/**
	 * Returns empty (not {@code null}) array in case if given one is {@code null}.
	 *
	 * @param array source array to be returned
	 * @return ensures given array is not {@code null}
	 */
	@SuppressWarnings("unchecked")
	public static <T> T[] ensureEmpty(T[] array) {
		return array == null ? (T[]) new Object[0] : array;
	}

	/**
	 * Returns empty (not {@code null}) list in case if given one is {@code null}.
	 *
	 * @param list source list to be returned
	 * @return ensures given list is not {@code null}
	 */
	public static <T> List<T> ensureEmpty(List<T> list) {
		List<T> result = list;
		if (result == null) result = Collections.emptyList();
		return result;
	}

	/**
	 * Returns empty (not {@code null}) set in case if given one is {@code null}.
	 *
	 * @param set source set to be returned
	 * @return ensures given set is not {@code null}
	 */
	public static <T> Set<T> ensureEmpty(Set<T> set) {
		Set<T> result = set;
		if (result == null) result = Collections.emptySet();
		return result;
	}

	/**
	 * Returns empty (not {@code null}) map in case if given one is {@code null}.
	 *
	 * @param map source map to be returned
	 * @return ensures given map is not {@code null}
	 */
	public static <K, V> Map<K, V> ensureEmpty(Map<K, V> map) {
		Map<K, V> result = map;
		if (result == null) result = Collections.emptyMap();
		return result;
	}

	/**
	 * Simple implementation of MessageFormat.
	 *
	 * @param template
	 *          input string with parameters for replacing. {0}, {1}, etc
	 * @param arguments
	 *          array of Object values for replacing. {@link String#valueOf(Object)} method is used to obtain argument
	 *          string representation.
	 * @return template string with replaced parameters
	 */
	public static String simpleMessageFormat(String template, Object... arguments) {
		if (isEmpty(template) || arguments == null || arguments.length == 0) return template;
		for (int i = 0; i < arguments.length; i++)
			template = template.replaceAll("\\{" + i + "\\}", String.valueOf(arguments[i]));
		return template;
	}

	/**
	 * Creates string where given <code>item</code> string is repeated <code>count</code> times.
	 *
	 * @param item
	 *          string part to be repeated
	 * @param count
	 *          number of times to repeat <code>item</code>
	 *
	 * @return repeated <code>item</code> or empty string if <code>item</code> is <code>null</code> or empty or
	 *         <code>count</code> less than 1.
	 */
	public static String repeat(String item, int count) {
		StringBuilder result = new StringBuilder();
		if (!isEmpty(item))
			for (int i = 0; i < count; i++)
				result.append(item);
		return result.toString();
	}

	/**
	 * Cuts long source string by the word boundaries to make a result to be not larger that specified amount of chars.
	 * Removed piece of text is replaced by optional tail (e.g. by ellipsis).
	 *
	 * @param str
	 *          Source long string that should be cut
	 * @param maxLength
	 *          The max length of resulting string (not includes tail)
	 * @param tail
	 *          Optional replacement of removed text
	 *
	 * @return Resulting teaser string
	 */
	public static String makeTeaser(String str, int maxLength, String tail) {
		if (isEmpty(str)) return "";
		if (str.length() < maxLength) return str;
		String s1 = str.substring(0, maxLength);
		String s2 = s1.replaceFirst("^(.*\\w)\\W+\\w*$", "$1");
		return (isEmpty(s2) ? s1 : s2) + (!isEmpty(tail) ? tail : "");
	}

	/**
	 * Cuts long source string by the word boundaries to make a result to be not larger that specified amount of chars.
	 *
	 * @param str
	 *          Source long string that should be cut
	 * @param maxLength
	 *          The max length of resulting string (not includes tail)
	 *
	 * @return Resulting teaser string
	 */
	public static String makeTeaser(String str, int maxLength) {
		return makeTeaser(str, maxLength, null);
	}

	/**
	 * Gets minimal value between given.
	 *
	 * @param v1 First value to be compared
	 * @param v2 Second value to be compared
	 * @param v3 Array of optional more values to be compared
	 * @param <T> Desired comparable type
	 * @return A minimal value
	 */
	@SafeVarargs
	public static <T extends Comparable<T>> T min(T v1, T v2, T... v3) {
		List<T> values = new ArrayList<>();
		values.add(v1);
		values.add(v2);
		if (v3 != null && v3.length > 0) values.addAll(Arrays.asList(v3));
		T result = v1;
		for (T v : values)
			if (result != v) {
				if (result == null || v == null) result = null;
				else if (result.compareTo(v) > 0) result = v;
			}
		return result;
	}

	/**
	 * Gets maximal value between given.
	 *
	 * @param v1 First value to be compared
	 * @param v2 Second value to be compared
	 * @param v3 Array of optional more values to be compared
	 * @param <T> Desired comparable type
	 * @return A maximal value
	 */
	@SafeVarargs
	public static <T extends Comparable<T>> T max(T v1, T v2, T... v3) {
		List<T> values = new ArrayList<>();
		values.add(v1);
		values.add(v2);
		if (v3 != null && v3.length > 0) values.addAll(Arrays.asList(v3));
		T result = v1;
		for (T v : values)
			if (result != v && v != null) {
				if (result == null) result = v;
				else if (result.compareTo(v) < 0) result = v;
			}
		return result;
	}

	/**
	 * Joins string items into one string separated by delimiter.
	 *
	 * @param delimiter
	 *          separator that should be used as item delimiter. <code>null</code> or empty string mean no delimiter.
	 * @param items
	 *          array with items to be joined
	 * @return all items joined into one string and separated by delimiter.
	 */
	public static String join(String delimiter, String... items) {
		if (isEmpty(items)) return "";
		StringBuilder result = new StringBuilder();
		boolean delimOk = !isEmpty(delimiter);
		for (Object item : items) {
			if (delimOk && result.length() > 0)
				result.append(delimiter);
			result.append(item);
		}
		return result.toString();
	}

	/**
	 * Joins string items into one string separated by delimiter.
	 *
	 * @param delimiter
	 *          separator that should be used as item delimiter. <code>null</code> or empty string mean no delimiter.
	 * @param items
	 *          list with items to be joined
	 * @return all items joined into one string and separated by delimiter.
	 */
	public static String join(String delimiter, Collection<?> items) {
		if (isEmpty(items)) return "";
		StringBuilder result = new StringBuilder();
		boolean delimOk = !isEmpty(delimiter);
		for (Object item : items) {
			if (delimOk && result.length() > 0)
				result.append(delimiter);
			result.append(item);
		}
		return result.toString();
	}

	/**
	 * Null safe equals.
	 *
	 * @param obj0
	 * 		object to compare
	 * @param obj1
	 * 		object to compare
	 * @return true if both obj0 and obj1 are null, or if obj0.equals(obj1)
	 */
	public static boolean equalOrBothNull(Object obj0, Object obj1) {
		return (obj0 == obj1) || (obj0 != null && obj0.equals(obj1));
	}

	/**
	 * Replaces in source string all the chars: {@code '}, {@code "} or {@code \} with escaped sequence, accordingly:
	 * {@code \'}, {@code \"} or {@code \\}
	 *
	 * @param string
	 *          String to be processed
	 * @return new string with replacements if any
	 */
	public static String escapeQuotes(String string) {
		if (isHollow(string)) return string;
		return string.replaceAll("(['\"\\\\])", "\\\\$1");
	}

	/**
	 * Returns the given {@code time} as {@code int} array that has exactly 6 items:
	 * <ul>
	 * <li>{@code 0} - milliseconds;
	 * <li>{@code 1} - seconds;
	 * <li>{@code 2} - minutes;
	 * <li>{@code 3} - hours;
	 * <li>{@code 4} - days;
	 * <li>{@code 5} - weeks;
	 * </ul>
	 * 
	 * @param time
	 *          A time period in milliseconds to be represented as {@code int} array
	 * @return An {@code int} array with time parts
	 */
	public static int[] timestampToParts(long time) {
		int s = (int) Math.floor(time / 1000);
		int ms = (int) (time - s * 1000);
		int w = (int) Math.floor(s / 604800);
		s -= w * 604800;
		int d = (int) Math.floor(s / 86400);
		s -= d * 86400;
		int h = (int) Math.floor(s / 3600);
		s -= h * 3600;
		int m = (int) Math.floor(s / 60);
		s -= m * 60;
		return new int[] {ms, s, m, h, d, w};
	}

	/**
	 * Formats the given {@code time} into string like "7w 1d 2h 34m 56s".
	 *
	 * @param time
	 *          A time period in milliseconds to be represented as string
	 * @return A formatted string
	 */
	private static String timestampToString(long time) {
		int[] t = timestampToParts(time);
		StringBuilder result = new StringBuilder();
		if (t[5] > 0) result.append(t[5]).append("w ");
		if (t[4] > 0) result.append(t[4]).append("d ");
		if (t[3] > 0) result.append(t[3]).append("h ");
		if (t[2] > 0) result.append(t[2]).append("m ");
		if (t[1] > 0) result.append(t[1]).append("s ");
		if (t[5] == 0 && t[4] == 0 && t[3] == 0 && t[2] == 0 && t[1] < 10 && t[0] > 0)
			result.append(t[0]).append("ms");
		if (result.length() == 0) result.append("0ms");
		return result.toString().trim();
	}

	private Utils() {}
}
