package org.sunbird.content.util

object CopyConstants {
    val CHILDREN: String = "children"
    val REQUIRED_KEYS: List[String] = List("createdBy", "createdFor", "organisation", "framework")
    val CONTENT_TYPE: String = "contentType"
    val CONTENT_TYPE_ASSET_CAN_NOT_COPY: String = "CONTENT_TYPE_ASSET_CAN_NOT_COPY"
    val STATUS: String = "status"
    val ERR_INVALID_REQUEST: String = "ERR_INVALID_REQUEST"
    val MIME_TYPE: String = "mimeType"
    val CONTENT_SCHEMA_NAME: String = "content"
    val SCHEMA_NAME: String = "schemaName"
    val SCHEMA_VERSION: String = "1.0"
    val ARTIFACT_URL: String = "artifactUrl"
    val IDENTIFIER: String = "identifier"
    val MODE: String = "mode"
    val COLLECTION_MIME_TYPE: String = "application/vnd.ekstep.content-collection"
    val ORIGIN: String = "origin"
    val ORIGIN_DATA: String = "originData"
    val ERR_INVALID_UPLOAD_FILE_URL: String = "ERR_INVALID_UPLOAD_FILE_URL"
    val ROOT_ID: String = "rootId"
    val HIERARCHY: String = "hierarchy"
    val ROOT: String = "root"
    val NODES_MODIFIED: String = "nodesModified"
    val VISIBILITY: String = "visibility"
    val METADATA: String = "metadata"
    val END_NODE_OBJECT_TYPES = List("Content", "ContentImage")
    val VERSION_KEY: String = "versionKey"
    val H5P_MIME_TYPE = "application/vnd.ekstep.h5p-archive"
    val COPY_TYPE: String = "copyType"
    val COPY_TYPE_SHALLOW: String = "shallow"
    val CONTENT: String = "content"
}
