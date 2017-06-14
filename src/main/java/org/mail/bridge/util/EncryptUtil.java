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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
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
			(byte) 0xc5, (byte) 0x8b, (byte) 0xd3, (byte) 0x8f,
			(byte) 0x83, (byte) 0x22, (byte) 0x2f, (byte) 0x9f,
			(byte) 0x9b, (byte) 0x4d, (byte) 0xe6, (byte) 0xdc
	};

	private static final int BUFFER_SIZE = 4096;

	private static Cipher getCipher(int mode, String password) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(mode, new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(
				new PBEKeySpec(password.toCharArray(), SALT, 2000, 128)).getEncoded(), "AES"));
		return cipher;
	}

	public static void copy(InputStream sourceData, OutputStream targetData) throws IOException {
		final byte[] buffer = new byte[BUFFER_SIZE];
		for (int c = 0; c >= 0; c = sourceData.read(buffer, 0, buffer.length))
			if (c > 0) targetData.write(buffer, 0, c);
		targetData.flush();
	}

	public static void gzip(InputStream unpackedData, OutputStream packedData) throws IOException {
		GZIPOutputStream gzipOut = new GZIPOutputStream(packedData);
		copy(unpackedData, gzipOut);
		gzipOut.finish();
		gzipOut.flush();
	}

	public static void gunzip(InputStream packedData, OutputStream unpackedData) throws IOException {
		copy(new GZIPInputStream(packedData), unpackedData);
	}

	public static void encrypt(String password, InputStream clearData, OutputStream cipherData) throws IOException {
		try {
			try (CipherInputStream cis = new CipherInputStream(clearData, getCipher(Cipher.ENCRYPT_MODE, password))) {
				copy(cis, cipherData);
			}
		} catch (GeneralSecurityException e) {
			LOG.error(e.getMessage(), e);
			throw new IOException(e);
		}
	}

	public static void decrypt(String password, InputStream cipherData, OutputStream clearData) throws IOException {
		try {
			try (CipherOutputStream cos = new CipherOutputStream(clearData, getCipher(Cipher.DECRYPT_MODE, password))) {
				copy(cipherData, cos);
			}
		} catch (GeneralSecurityException e) {
			LOG.error(e.getMessage(), e);
			throw new IOException(e);
		}
	}

	public static void gzipEncrypt(
			String password,
			final InputStream clearData,
			OutputStream cipherData) throws IOException {
		final PipedOutputStream output = new PipedOutputStream();
		final PipedInputStream input = new PipedInputStream(output);
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					gzip(clearData, output);
					output.close();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
		thread.start();
		encrypt(password, input, cipherData);
	}

	public static void decryptGunzip(
			final String password,
			final InputStream cipherData,
			OutputStream clearData) throws IOException {
		final PipedOutputStream output = new PipedOutputStream();
		final PipedInputStream input = new PipedInputStream(output);
		final Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					decrypt(password, cipherData, output);
					output.close();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
		thread.start();
		gunzip(input, clearData);
	}

	private EncryptUtil() {}
}
