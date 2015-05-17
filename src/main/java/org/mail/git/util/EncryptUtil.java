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
package org.mail.git.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class EncryptUtil {

	private static final Logger LOG = LoggerFactory.getLogger(EncryptUtil.class);

	private static final byte[] SALT = {
			(byte) 0x2d, (byte) 0x5f, (byte) 0xd1, (byte) 0x96,
			(byte) 0xc5, (byte) 0x8b, (byte) 0xd3, (byte) 0x8f
	};

	private static final String CIPHER_NAME = "PBEWithMD5AndDES";
	private static final int ITERATIONS = 20;
	private static final int BUFFER_SIZE = 4096;

	private static Cipher getCipher(int mode, String password) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(CIPHER_NAME);
		cipher.init(mode,
				SecretKeyFactory.getInstance(CIPHER_NAME).generateSecret(new PBEKeySpec(password.toCharArray())),
				new PBEParameterSpec(SALT, ITERATIONS));
		return cipher;
	}

	public static void gzip(InputStream unpackedData, OutputStream packedData) throws IOException {
		GZIPOutputStream gzipOut = new GZIPOutputStream(packedData);
		final byte[] buffer = new byte[BUFFER_SIZE];
		for (int c = 0; c >= 0; c = unpackedData.read(buffer, 0, buffer.length))
			if (c > 0) gzipOut.write(buffer, 0, c);
		gzipOut.finish();
	}

	public static void ungzip(InputStream packedData, OutputStream unpackedData) throws IOException {
		GZIPInputStream gzipIn = new GZIPInputStream(packedData);
		final byte[] buffer = new byte[BUFFER_SIZE];
		for (int c = 0; c >= 0; c = gzipIn.read(buffer, 0, buffer.length))
			if (c > 0) unpackedData.write(buffer, 0, c);
	}

	public static void encrypt(String password, InputStream clearData, OutputStream cypherData) throws IOException {
		try {
			Cipher pbeCipher = getCipher(Cipher.ENCRYPT_MODE, password);
			final byte[] buffer = new byte[BUFFER_SIZE];
			for (int c = 0; c >= 0; c = clearData.read(buffer, 0, buffer.length))
				if (c > 0) cypherData.write(pbeCipher.update(buffer, 0, c));
			cypherData.write(pbeCipher.doFinal());
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	public static void decrypt(String password, InputStream cypherData, OutputStream clearData) throws IOException {
		try {
			Cipher pbeCipher = getCipher(Cipher.DECRYPT_MODE, password);
			final byte[] buffer = new byte[BUFFER_SIZE];
			for (int c = 0; c >= 0; c = cypherData.read(buffer, 0, buffer.length))
				if (c > 0) clearData.write(pbeCipher.update(buffer, 0, c));
			clearData.write(pbeCipher.doFinal());
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	public static void gzipEncrypt(String password,
																 final InputStream clearData,
																 OutputStream cypherData) throws IOException {
		final PipedOutputStream output = new PipedOutputStream();
		final PipedInputStream input = new PipedInputStream(output);
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					gzip(clearData, output);
					output.flush();
					output.close();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
		thread.start();
		encrypt(password, input, cypherData);
	}

	public static void gzipDecrypt(final String password,
																 final InputStream cypherData,
																 OutputStream clearData) throws IOException {
		final PipedOutputStream output = new PipedOutputStream();
		final PipedInputStream input = new PipedInputStream(output);
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					decrypt(password, cypherData, output);
					output.flush();
					output.close();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
		thread.start();
		ungzip(input, clearData);
	}

	private EncryptUtil() {};
}
