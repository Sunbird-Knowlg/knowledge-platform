package utils

object ApiId {

	// Health APIs (Unified)
	final val APPLICATION_HEALTH = "api.knowlg.service.health"
	final val APPLICATION_SERVICE_HEALTH = "api.knowlg.service.health"

	// Content APIs
	val CREATE_CONTENT = "api.content.create"
	val READ_CONTENT = "api.content.read"
	val READ_PRIVATE_CONTENT = "api.content.private.read"
	val UPDATE_CONTENT = "api.content.update"
	val UPLOAD_CONTENT = "api.content.upload"
	val RETIRE_CONTENT = "api.content.retire"
	val COPY_CONTENT = "api.content.copy"
	val UPLOAD_PRE_SIGNED_CONTENT = "api.content.upload.url"
	val DISCARD_CONTENT = "api.content.discard"
	val FlAG_CONTENT = "api.content.flag"
	val ACCEPT_FLAG = "api.content.flag.accept"
	val LINK_DIAL_CONTENT = "api.content.dialcode.link"
	val IMPORT_CONTENT = "api.content.import"
	val SYSTEM_UPDATE_CONTENT = "api.content.system.update"
	val REVIEW_CONTENT = "api.content.review"
	val REJECT_CONTENT = "api.content.review.reject"
	val PUBLISH_CONTENT_PUBLIC = "api.content.publish.public"
	val PUBLISH_CONTENT_UNLSTED = "api.content.publish.unlisted"

	// Collection APIs
	val ADD_HIERARCHY = "api.content.hierarchy.add"
	val REMOVE_HIERARCHY = "api.content.hierarchy.remove"
	val UPDATE_HIERARCHY = "api.content.hierarchy.update"
	val GET_HIERARCHY = "api.content.hierarchy.get"
	val LINK_DIAL_COLLECTION = "api.collection.dialcode.link"
	val RESERVE_DIAL_CONTENT = "api.content.dialcode.reserve"
	val RELEASE_DIAL_CONTENT = "api.content.dialcode.release"

	//License APIs
	val CREATE_LICENSE = "api.license.create"
	val READ_LICENSE = "api.license.read"
	val UPDATE_LICENSE = "api.license.update"
	val RETIRE_LICENSE = "api.license.retire"

	//Channel APIs
	val CREATE_CHANNEL = "api.channel.create"
	val READ_CHANNEL = "api.channel.read"
	val UPDATE_CHANNEL = "api.channel.update"
	val LIST_CHANNEL = "api.channel.list"
	val RETIRE_CHANNEL = "api.channel.retire"

	//Asset V4 apis
	val CREATE_ASSET = "api.asset.create"
	val READ_ASSET = "api.asset.read"
	val UPDATE_ASSET = "api.asset.update"
	val UPLOAD_ASSET = "api.asset.upload"
	val UPLOAD_PRE_SIGNED_ASSET= "api.asset.upload.url"
	val COPY_ASSET = "api.asset.copy"
	val ASSET_LICENSE_VALIDATE = "asset.url.validate"

	//Collection V4 apis
	val CREATE_COLLECTION = "api.collection.create"
	val READ_COLLECTION = "api.collection.read"
	val READ_PRIVATE_COLLECTION = "api.collection.private.read"
	val UPDATE_COLLECTION = "api.collection.update"
	val RETIRE_COLLECTION = "api.collection.retire"
	val COPY_COLLECTION = "api.collection.copy"
	val DISCARD_COLLECTION = "api.collection.discard"
	val FlAG_COLLECTION = "api.collection.flag"
	val ACCEPT_FLAG_COLLECTION = "api.collection.flag.accept"
	val ADD_HIERARCHY_V4 = "api.collection.hierarchy.add"
	val REMOVE_HIERARCHY_V4 = "api.collection.hierarchy.remove"
	val UPDATE_HIERARCHY_V4 = "api.collection.hierarchy.update"
	val GET_HIERARCHY_V4 = "api.collection.hierarchy.get"
	val SYSTEM_UPDATE_COLLECTION = "api.collection.system.update"
	val REVIEW_COLLECTION = "api.collection.review"
	val REJECT_COLLECTION = "api.collection.review.reject"

	//App APIs
	val REGISTER_APP = "api.app.register"
	val READ_APP = "api.app.read"
	val UPDATE_APP = "api.app.update"
	val APPROVE_APP = "api.app.approve"
	val REJECT_APP = "api.app.reject"
	val RETIRE_APP = "api.app.retire"

	val CREATE_EVENT = "api.event.create"
	val UPDATE_EVENT = "api.event.update"

	val CREATE_EVENT_SET = "api.eventset.create"
	val UPDATE_EVENT_SET = "api.eventset.update"
	val PUBLISH_EVENT_SET = "api.eventset.publish"
	val PUBLISH_EVENT = "api.event.publish"

	//Object APIs
	val READ_OBJECT = "api.object.read"

	//Collection CSV APIs
	val IMPORT_CSV = "api.collection.import"
	val EXPORT_CSV = "api.collection.export"
	val RESERVE_DIAL_COLLECTION = "api.collection.dialcode.reserve"
	val RELEASE_DIAL_COLLECTION = "api.collection.dialcode.release"

	// Search APIs
	final val APPLICATION_SEARCH = "api.search-service.search"
	final val APPLICATION_PRIVATE_SEARCH = "api.search-service.private.search"
	final val APPLICATION_COUNT = "api.search-service.count"
	final val APPLICATION_AUDIT_HISTORY = "api.search-service.audit_history.read"

	// Taxonomy APIs
	final val CREATE_OBJECT_CATEGORY = "api.object.category.create"
	final val READ_OBJECT_CATEGORY = "api.object.category.read"
	final val UPDATE_OBJECT_CATEGORY = "api.object.category.update"
	final val CREATE_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.create"
	final val READ_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.read"
	final val UPDATE_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.update"
	final val CREATE_FRAMEWORK = "api.taxonomy.framework.create"
	final val READ_FRAMEWORK = "api.taxonomy.framework.read"
	final val UPDATE_FRAMEWORK = "api.taxonomy.framework.update"
	final val RETIRE_FRAMEWORK = "api.taxonomy.framework.retire"
	final val COPY_FRAMEWORK = "api.taxonomy.framework.copy"
	final val PUBLISH_FRAMEWORK = "api.taxonomy.framework.publish"

	final val CREATE_CATEGORY = "api.taxonomy.category.create"
	final val READ_CATEGORY = "api.taxonomy.category.read"
	final val UPDATE_CATEGORY = "api.taxonomy.category.update"
	final val RETIRE_CATEGORY = "api.taxonomy.category.retire"

	final val CREATE_CATEGORY_INSTANCE = "api.taxonomy.category.instance.create"
	final val READ_CATEGORY_INSTANCE = "api.taxonomy.category.instance.read"
	final val UPDATE_CATEGORY_INSTANCE = "api.taxonomy.category.instance.update"
	final val RETIRE_CATEGORY_INSTANCE = "api.taxonomy.category.instance.retire"

	final val CREATE_TERM = "api.taxonomy.term.create"
	final val READ_TERM = "api.taxonomy.term.read"
	final val UPDATE_TERM = "api.taxonomy.term.update"
	final val RETIRE_TERM = "api.taxonomy.term.retire"

	final val CREATE_LOCK = "api.taxonomy.lock.create"
	final val REFRESH_LOCK = "api.taxonomy.lock.refresh"
	final val RETIRE_LOCK = "api.taxonomy.lock.retire"
	final val LIST_LOCK = "api.taxonomy.lock.list"

}
