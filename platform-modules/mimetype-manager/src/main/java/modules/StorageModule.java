package modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.sunbird.cloud.storage.IStorageService;
import org.sunbird.cloud.storage.StorageConfig;
import org.sunbird.cloud.storage.StorageServiceFactory;
import play.Environment;

/**
 * Guice module that provides a singleton {@link BaseStorageService} for the configured
 * Cloud Storage Provider (CSP).
 *
 * <p>Register in each Play service's {@code conf/application.conf}:
 * <pre>
 * play.modules.enabled += "modules.StorageModule"
 * </pre>
 *
 * <h3>Auth-type switching</h3>
 * <ul>
 *   <li><b>Local / Developer</b>: set {@code cloud_storage_auth_type = "ACCESS_KEY"} (default)
 *       and supply {@code cloud_storage_key} / {@code cloud_storage_secret} via env-vars or
 *       application.conf overrides.</li>
 *   <li><b>Production (Kubernetes)</b>: set {@code cloud_storage_auth_type = "OIDC"}.
 *       The Azure SDK resolves credentials automatically via Workload Identity
 *       ({@code AZURE_CLIENT_ID}, {@code AZURE_TENANT_ID}, {@code AZURE_FEDERATED_TOKEN_FILE})
 *       injected by the pod's service-account annotations. No static credentials are needed.</li>
 * </ul>
 */
public class StorageModule extends AbstractModule {

    private final Environment environment;
    private final Config config;

    /**
     * Play's two-arg constructor convention — called automatically when the module is
     * registered in {@code play.modules.enabled}.
     */
    public StorageModule(Environment environment, Config config) {
        this.environment = environment;
        this.config = config;
    }

    @Override
    protected void configure() {
        // All bindings provided via @Provides method below.
    }

    /**
     * Provides the singleton {@link IStorageService}.
     *
     * <p>The concrete CSP implementation (e.g. {@code cloud-storage-sdk-azure}) is discovered
     * at runtime via Java {@link java.util.ServiceLoader} from the {@code META-INF/services/}
     * entry bundled in the runtime-scoped CSP jar.
     */
    @Provides
    @Singleton
    public IStorageService provideStorageService() {
        return StorageServiceFactory.getStorageService(buildStorageConfig());
    }

    /**
     * Builds the {@link StorageConfig} from Play config (with env-var fallback).
     * Kept separate from {@link #provideStorageService()} so it can be tested without a
     * CSP runtime jar on the classpath.
     *
     * @return the storage config for the configured CSP
     */
    StorageConfig buildStorageConfig() {
        String storageType = getConfigOrEnv("cloud_storage_type", "azure");
        String authTypeStr = getConfigOrEnv("cloud_storage_auth_type", "ACCESS_KEY").toUpperCase();

        // StorageType and AuthType are inner classes of StorageConfig in v2.0.0
        StorageConfig.StorageType storageTypeEnum = StorageConfig.StorageType.valueOf(storageType.toUpperCase());
        StorageConfig.AuthType authType = StorageConfig.AuthType.valueOf(authTypeStr);

        StorageConfig.Builder builder = StorageConfig.builder(storageTypeEnum).authType(authType);

        // Region: required for non-us-east-1 buckets (e.g. AWS S3). When set, the SDK uses it
        // explicitly; when empty, the CSP falls back to its default (AWS -> us-east-1).
        String region = getConfigOrEnv("cloud_storage_region", "");
        if (!region.isEmpty()) {
            builder.region(region);
        }

        // Endpoint: when set, the SDK addresses the bucket path-style and returns URLs as
        // <endpoint>/<container>/<key> (e.g. https://s3.ap-south-1.amazonaws.com/<bucket>/...).
        // Without it, AWS URLs are https://<bucket>.s3.amazonaws.com/<key>, which
        // cloudstorage.write_base_path can never match, so relative-path storage is skipped.
        String endPoint = getConfigOrEnv("cloud_storage_endpoint", "");
        if (!endPoint.isEmpty()) {
            builder.endPoint(endPoint);
        }

        // The 'storageKey' (Azure Account Name) is required even for OIDC
        // to construct the correct service URL (e.g. https://<account_name>.blob.core.windows.net)
        String storageKey = getConfigOrEnv("cloud_storage_key", "");
        builder.storageKey(storageKey);

        if (authType == StorageConfig.AuthType.ACCESS_KEY) {
            // Developer / local environment: use static secret from config
            String storageSecret = getConfigOrEnv("cloud_storage_secret", "");
            builder.storageSecret(storageSecret);
        }
        // For OIDC / IAM: the Azure SDK resolves credentials automatically via Workload Identity
        // or Managed Identity — no static secret needed.

        return builder.build();
    }

    /**
     * Reads a value from Play config, falling back to an environment variable of the same name
     * if the config value is empty. This allows {@code export cloud_storage_key=...} to work
     * even when the packaged application.conf has an empty default.
     */
    private String getConfigOrEnv(String key, String defaultValue) {
        String value = config.hasPath(key) ? config.getString(key) : "";
        if (value != null && !value.isEmpty()) {
            return value;
        }
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return defaultValue;
    }
}
