package org.sunbird.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.RemoteEndpointAwareJdkSSLOptions;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

/**
 * Adds credentials and TLS to a Cassandra {@link Cluster.Builder}. Kept separate from
 * {@link CassandraConnector} so it can be unit tested without triggering that class's
 * static session initialiser.
 */
final class CassandraSecurity {

	private CassandraSecurity() {
	}

	/**
	 * Both settings are off by default, so existing deployments are unaffected.
	 * Credentials are added only when username and password are both non-empty.
	 * TLS trusts only the CA(s) in {@code rootCertPath} (PEM, may hold several certificates).
	 * ponytail: chain and dates are verified, hostname is not (driver 3.x default). Add
	 * endpoint identification if a hostname-pinned check is required.
	 */
	static void apply(Cluster.Builder builder, String username, String password,
			boolean sslEnabled, String rootCertPath) {
		if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
			builder.withCredentials(username, password);
		}
		if (sslEnabled) {
			builder.withSSL(RemoteEndpointAwareJdkSSLOptions.builder()
					.withSSLContext(buildSslContext(rootCertPath))
					.build());
		}
	}

	/**
	 * Builds an {@link SSLContext} that trusts exactly the certificates in the given PEM file.
	 *
	 * @throws IllegalStateException if the path is blank, unreadable or holds no certificate
	 */
	static SSLContext buildSslContext(String rootCertPath) {
		if (StringUtils.isBlank(rootCertPath)) {
			throw new IllegalStateException(
					"cassandra.ssl.rootcert must be set when cassandra.ssl.enabled is true");
		}
		try (InputStream in = new FileInputStream(rootCertPath)) {
			Collection<? extends Certificate> certs =
					CertificateFactory.getInstance("X.509").generateCertificates(in);
			if (certs.isEmpty()) {
				throw new IllegalStateException("No certificate found in " + rootCertPath);
			}
			KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			trustStore.load(null, null);
			int i = 0;
			for (Certificate cert : certs) {
				trustStore.setCertificateEntry("cassandra-ca-" + i++, cert);
			}
			TrustManagerFactory tmf =
					TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
			return context;
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(
					"Failed to build Cassandra SSL context from " + rootCertPath + ": " + e.getMessage(), e);
		}
	}
}
