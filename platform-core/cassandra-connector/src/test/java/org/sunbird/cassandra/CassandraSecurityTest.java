package org.sunbird.cassandra;

import com.datastax.driver.core.AuthProvider;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PlainTextAuthProvider;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CassandraSecurityTest {

	private static String resource(String name) throws Exception {
		URL url = CassandraSecurityTest.class.getClassLoader().getResource(name);
		return new File(url.toURI()).getAbsolutePath();
	}

	private static Cluster.Builder builder() {
		return Cluster.builder().addContactPoint("127.0.0.1");
	}

	@Test
	public void apply_defaultsLeaveBuilderUntouched() {
		Cluster.Builder b = builder();
		CassandraSecurity.apply(b, "", "", false, "");

		assertNull(b.getConfiguration().getProtocolOptions().getSSLOptions());
		assertSame(AuthProvider.NONE, b.getConfiguration().getProtocolOptions().getAuthProvider());
	}

	@Test
	public void apply_usernameWithoutPasswordSkipsCredentials() {
		Cluster.Builder b = builder();
		CassandraSecurity.apply(b, "admin", "", false, "");

		assertSame(AuthProvider.NONE, b.getConfiguration().getProtocolOptions().getAuthProvider());
	}

	@Test
	public void apply_setsCredentials() {
		Cluster.Builder b = builder();
		CassandraSecurity.apply(b, "admin", "secret", false, "");

		assertTrue(b.getConfiguration().getProtocolOptions().getAuthProvider()
				instanceof PlainTextAuthProvider);
	}

	@Test
	public void apply_setsSslOptions() throws Exception {
		Cluster.Builder b = builder();
		CassandraSecurity.apply(b, "", "", true, resource("test-root.crt"));

		assertNotNull(b.getConfiguration().getProtocolOptions().getSSLOptions());
	}

	@Test(expected = IllegalStateException.class)
	public void apply_sslWithoutCertFails() {
		CassandraSecurity.apply(builder(), "", "", true, "");
	}

	@Test
	public void buildSslContext_loadsSingleCertificate() throws Exception {
		SSLContext ctx = CassandraSecurity.buildSslContext(resource("test-root.crt"));
		assertNotNull(ctx);
	}

	@Test
	public void buildSslContext_loadsCertificateBundle() throws Exception {
		SSLContext ctx = CassandraSecurity.buildSslContext(resource("test-bundle.crt"));
		assertNotNull(ctx);
	}

	@Test(expected = IllegalStateException.class)
	public void buildSslContext_missingFileFails() {
		CassandraSecurity.buildSslContext("/does/not/exist.crt");
	}

	@Test(expected = IllegalStateException.class)
	public void buildSslContext_blankPathFails() {
		CassandraSecurity.buildSslContext(null);
	}
}
