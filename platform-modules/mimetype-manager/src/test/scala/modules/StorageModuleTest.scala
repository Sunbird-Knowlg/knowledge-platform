package modules

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cloud.storage.StorageConfig

class StorageModuleTest extends FlatSpec with Matchers {

	private def configFor(conf: String): StorageConfig =
		new StorageModule(null, ConfigFactory.parseString(conf)).buildStorageConfig()

	"buildStorageConfig with cloud_storage_endpoint" should "pass the endpoint to the SDK so URLs are path-style" in {
		val storageConfig = configFor(
			"""cloud_storage_type="aws"
			  |cloud_storage_auth_type="IAM_ROLE"
			  |cloud_storage_region="ap-south-1"
			  |cloud_storage_endpoint="https://s3.ap-south-1.amazonaws.com"
			  |""".stripMargin)
		storageConfig.getType should be(StorageConfig.StorageType.AWS)
		storageConfig.getAuthType should be(StorageConfig.AuthType.IAM_ROLE)
		storageConfig.getRegion should be("ap-south-1")
		storageConfig.getEndPoint should be("https://s3.ap-south-1.amazonaws.com")
	}

	"buildStorageConfig without cloud_storage_endpoint" should "leave the endpoint unset" in {
		assume(System.getenv("cloud_storage_endpoint") == null)
		val storageConfig = configFor(
			"""cloud_storage_type="aws"
			  |cloud_storage_auth_type="IAM_ROLE"
			  |cloud_storage_region="ap-south-1"
			  |""".stripMargin)
		storageConfig.getEndPoint should be(null)
		storageConfig.getRegion should be("ap-south-1")
	}

	"buildStorageConfig with empty cloud_storage_endpoint" should "leave the endpoint unset" in {
		assume(System.getenv("cloud_storage_endpoint") == null)
		val storageConfig = configFor(
			"""cloud_storage_type="aws"
			  |cloud_storage_auth_type="IAM_ROLE"
			  |cloud_storage_endpoint=""
			  |""".stripMargin)
		storageConfig.getEndPoint should be(null)
	}
}
