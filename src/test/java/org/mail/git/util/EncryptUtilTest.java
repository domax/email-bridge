package org.mail.git.util;

import org.junit.Before;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class EncryptUtilTest {

	private static final int SZ = 100 * 1023;
	private static final String PWD = "secret";

	private byte[] source;

	@Before
	public void before() {
		source = new byte[SZ];
		for (int i = 0; i < source.length; ++i)
			source[i] = (byte) Math.round(Math.sin(0.02 * i) * 128);
	}

	@Test
	public void testGzipGunzip() throws IOException {
		byte[] encrypted;
		try (InputStream is = new ByteArrayInputStream(source);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.gzip(is, os);
			encrypted = os.toByteArray();
		}
		assertNotNull(encrypted);
		assertTrue(encrypted.length < source.length);

		byte[] decrypted;
		try (InputStream is = new ByteArrayInputStream(encrypted);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.gunzip(is, os);
			decrypted = os.toByteArray();
		}
		assertNotNull(decrypted);
		assertEquals(SZ, decrypted.length);

		assertArrayEquals(source, decrypted);
	}

	@Test
	public void testEncryptDecrypt() throws IOException {
		byte[] encrypted;
		try (InputStream is = new ByteArrayInputStream(source);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.encrypt(PWD, is, os);
			os.flush();
			encrypted = os.toByteArray();
		}
		assertNotNull(encrypted);

		byte[] decrypted;
		try (InputStream is = new ByteArrayInputStream(encrypted);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.decrypt(PWD, is, os);
			os.flush();
			decrypted = os.toByteArray();
		}
		assertNotNull(decrypted);

		assertArrayEquals(source, decrypted);
	}

	@Test
	public void testGzipEncryptDecryptGunzip() throws IOException {
		byte[] encrypted;
		try (InputStream is = new ByteArrayInputStream(source);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.gzipEncrypt(PWD, is, os);
			encrypted = os.toByteArray();
		}
		assertNotNull(encrypted);

		byte[] decrypted;
		try (InputStream is = new ByteArrayInputStream(encrypted);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.decryptGunzip(PWD, is, os);
			decrypted = os.toByteArray();
		}
		assertNotNull(decrypted);
		assertEquals(SZ, decrypted.length);

		assertArrayEquals(source, decrypted);
	}
}
