package org.mail.git;

import org.junit.*;

import java.io.*;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:max@dominichenko.com">Maksym Dominichenko</a>
 */
public class EncryptUtilTest {

	private static final int SZ = 100 * 1024;
	private static final int SZ_ENC = 6232;
	private static final String PWD = "secret";

	@Test
	public void testEncryptDecrypt() throws IOException {
		byte[] source = new byte[SZ];
		for (int i = 0; i < source.length; ++i)
			source[i] = (byte) Math.round(Math.sin(0.02 * i) * 128);

		byte[] encrypted;
		try (InputStream is = new ByteArrayInputStream(source);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.gzipEncrypt(PWD, is, os);
			encrypted = os.toByteArray();
		}
		assertNotNull(encrypted);
		assertEquals(SZ_ENC, encrypted.length);

		byte[] decrypted;
		try (InputStream is = new ByteArrayInputStream(encrypted);
				 ByteArrayOutputStream os = new ByteArrayOutputStream(SZ)) {
			EncryptUtil.gzipDecrypt(PWD, is, os);
			decrypted = os.toByteArray();
		}
		assertNotNull(decrypted);
		assertEquals(SZ, decrypted.length);

		assertArrayEquals(source, decrypted);
	}
}
