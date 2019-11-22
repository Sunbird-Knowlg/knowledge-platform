package org.sunbird.cloudstore;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.Model;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.Platform;
import org.sunbird.common.Slug;
import org.sunbird.common.exception.ServerException;
import scala.Option;
import scala.collection.immutable.List;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
/**
 * Utility Class for Cloud Storage Operations.
 */
import java.io.File;

public class CloudStore {

	private static BaseStorageService storageService = null;
	private static String cloudStoreType = Platform.config.getString("cloud_storage_type");

	static {

		if(StringUtils.equalsIgnoreCase(cloudStoreType, "azure")) {
			String storageKey = Platform.config.getString("azure_storage_key");
			String storageSecret = Platform.config.getString("azure_storage_secret");
			storageService = StorageServiceFactory.getStorageService(new StorageConfig(cloudStoreType, storageKey, storageSecret));
		}else if(StringUtils.equalsIgnoreCase(cloudStoreType, "aws")) {
			String storageKey = Platform.config.getString("aws_storage_key");
			String storageSecret = Platform.config.getString("aws_storage_secret");
			storageService = StorageServiceFactory.getStorageService(new StorageConfig(cloudStoreType, storageKey, storageSecret));
		}else {
			throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Error while initialising cloud storage");
		}
	}

	/**
	 * This Method Returns Storage Service Instance
	 * @return BaseStorageService
	 */
	public static BaseStorageService getCloudStoreService() {
		return storageService;
	}

	/**
	 * This Method Returns Storage Container Name.
	 * @return String
	 */
	public static String getContainerName() {
		if(StringUtils.equalsIgnoreCase(cloudStoreType, "azure")) {
			return Platform.config.getString("azure_storage_container");
		}else if(StringUtils.equalsIgnoreCase(cloudStoreType, "aws")) {
			return Platform.config.getString("aws_storage_container");
		}else {
			throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Error while getting container name");
		}
	}

	/**
	 * This Method Upload File To Given Folder Within Cloud Storage Container
	 * @param folderName
	 * @param file
	 * @param slugFile
	 * @return String[]
	 * @throws Exception
	 */
	public static String[] uploadFile(String folderName, File file, boolean slugFile) throws Exception {
		if (BooleanUtils.isTrue(slugFile))
			file = Slug.createSlugFile(file);
		String objectKey = folderName + "/" + file.getName();
		String container = getContainerName();
		String url = storageService.upload(container, file.getAbsolutePath(), objectKey, Option.apply(false), Option
				.apply(1), Option.apply(5), Option.empty());
		return new String[] { objectKey, url};
	}

	/**
	 * This Method Upload Folder To Given Folder Within Cloud Storage Container
	 * @param folderName
	 * @param directory
	 * @param slugFile
	 * @return String[]
	 */
	public static String[] uploadDirectory(String folderName, File directory, boolean slugFile) {
		File file = directory;
		if (BooleanUtils.isTrue(slugFile))
			file = Slug.createSlugFile(file);
		String container = getContainerName();
		String objectKey = folderName + File.separator;
		String url = storageService.upload(container, file.getAbsolutePath(), objectKey, Option.apply(true), Option
				.apply(1), Option.apply(5), Option.empty());
		return new String[] { objectKey, url };
	}

	/**
	 *
	 * @param folderName
	 * @param directory
	 * @param slugFile
	 * @param context
	 * @return Future<List<String>>
	 */
	public static Future<List<String>> uploadH5pDirectory(String folderName, File directory, boolean slugFile,
	                                                      ExecutionContext context) {
		File file = directory;
		if (BooleanUtils.isTrue(slugFile))
			file = Slug.createSlugFile(file);
		String container = getContainerName();
		String objectKey = folderName + File.separator;
		return (Future<List<String>>) storageService.uploadFolder(container, file.getAbsolutePath(), objectKey, Option.apply(false), Option
				.empty(), Option.empty(), 1, context);
	}

	/**
	 * This Method Returns Object Size
	 * @param key
	 * @return double
	 * @throws Exception
	 */
	public static double getObjectSize(String key) throws Exception {
		String container = getContainerName();
		Model.Blob blob = null;
		blob = (Model.Blob) storageService.getObject(container, key, Option.apply(false));
		return blob.contentLength();
	}

	/**
	 * This Method Copy Data From One Location To Another Location
	 * @param sourcePrefix
	 * @param destinationPrefix
	 */
	public static void copyObjectsByPrefix(String sourcePrefix, String destinationPrefix) {
		String container = getContainerName();
		storageService.copyObjects(container, sourcePrefix, container, destinationPrefix, Option.apply(true));
	}

	/**
	 *
	 * @param prefix
	 * @param isDirectory
	 * @return String
	 */
	public static String getURI(String prefix, Option<Object> isDirectory) {
		String container = getContainerName();
		return storageService.getUri(container, prefix, isDirectory);
	}

	/**
	 *
	 * @param key
	 * @param isDirectory
	 * @throws Exception
	 */
	public static void deleteFile(String key, boolean isDirectory) throws Exception {
		String container = getContainerName();
		storageService.deleteObject(container, key, Option.apply(isDirectory));
	}
}
