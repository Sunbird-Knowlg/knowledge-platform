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
        String storageType = config.hasPath("cloud_storage_type")
                ? config.getString("cloud_storage_type") : "azure";
        String authTypeStr = config.hasPath("cloud_storage_auth_type")
                ? config.getString("cloud_storage_auth_type").toUpperCase() : "ACCESS_KEY";

        // StorageType and AuthType are inner classes of StorageConfig in v2.0.0
        StorageConfig.StorageType storageTypeEnum = StorageConfig.StorageType.valueOf(storageType.toUpperCase());
        StorageConfig.AuthType authType = StorageConfig.AuthType.valueOf(authTypeStr);

        StorageConfig.Builder builder = StorageConfig.builder(storageTypeEnum).authType(authType);

        // The 'storageKey' (Azure Account Name) is required even for OIDC
        // to construct the correct service URL (e.g. https://<account_name>.blob.core.windows.net)
        String storageKey = config.hasPath("cloud_storage_key")
                ? config.getString("cloud_storage_key") : "";
        builder.storageKey(storageKey);

        if (authType == StorageConfig.AuthType.ACCESS_KEY) {
            // Developer / local environment: use static secret from config
            String storageSecret = config.hasPath("cloud_storage_secret")
                    ? config.getString("cloud_storage_secret") : "";
            builder.storageSecret(storageSecret);
        }
        // For OIDC / IAM: the Azure SDK resolves credentials automatically via Workload Identity
        // or Managed Identity — no static secret needed.

        return StorageServiceFactory.getStorageService(builder.build());
    }
}
