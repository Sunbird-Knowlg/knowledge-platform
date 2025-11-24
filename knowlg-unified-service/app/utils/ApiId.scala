package utils

object ApiId {

	final val APPLICATION_HEALTH = "api.assessment.health"
	final val APPLICATION_SERVICE_HEALTH = "api.assessment.service.health"

	//ItemSet APIs
	val CREATE_ITEM_SET = "api.itemset.create"
	val READ_ITEM_SET = "api.itemset.read"
	val UPDATE_ITEM_SET = "api.itemset.update"
	val REVIEW_ITEM_SET = "api.itemset.review"
	val RETIRE_ITEM_SET = "api.itemset.retire"

	//Assessment Item APIs
	val CREATE_ASSESSMENT_ITEM = "api.assessment.item.create"
	val READ_ASSESSMENT_ITEM = "api.assessment.item.read"
	val UPDATE_ASSESSMENT_ITEM = "api.assessment.item.update"
	val RETIRE_ASSESSMENT_ITEM = "api.assessment.item.retire"
	val SEARCH_ASSESSMENT_ITEM = "api.assessment.item.search"

	//Question APIs
	val CREATE_QUESTION = "api.question.create"
	val READ_QUESTION = "api.question.read"
	val READ_PRIVATE_QUESTION = "api.question.private.read"
	val UPDATE_QUESTION = "api.question.update"
	val REVIEW_QUESTION = "api.question.review"
	val PUBLISH_QUESTION = "api.question.publish"
	val RETIRE_QUESTION = "api.question.retire"
	val IMPORT_QUESTION = "api.question.import"
	val SYSTEM_UPDATE_QUESTION = "api.question.system.update"
	val LIST_QUESTIONS = "api.questions.list"
	val REJECT_QUESTION = "api.question.reject"
	val COPY_QUESTION = "api.question.copy"

	//QuestionSet APIs
	val CREATE_QUESTION_SET = "api.questionset.create"
	val READ_QUESTION_SET = "api.questionset.read"
	val READ_PRIVATE_QUESTION_SET = "api.questionset.private.read"
	val UPDATE_QUESTION_SET = "api.questionset.update"
	val REVIEW_QUESTION_SET = "api.questionset.review"
	val PUBLISH_QUESTION_SET = "api.questionset.publish"
	val RETIRE_QUESTION_SET = "api.questionset.retire"
	val ADD_QUESTION_SET = "api.questionset.add"
	val REMOVE_QUESTION_SET = "api.questionset.remove"
	val UPDATE_HIERARCHY = "api.questionset.hierarchy.update"
	val GET_HIERARCHY = "api.questionset.hierarchy.get"
	val REJECT_QUESTION_SET = "api.questionset.reject"
	val IMPORT_QUESTION_SET = "api.questionset.import"
	val SYSTEM_UPDATE_QUESTION_SET = "api.questionset.system.update"
	val COPY_QUESTION_SET = "api.questionset.copy"
	val UPDATE_COMMENT_QUESTION_SET = "api.questionset.update.comment"
	val READ_COMMENT_QUESTION_SET = "api.questionset.read.comment"

	// Content APIs
	val CREATE_CONTENT = "api.content.create"
	val READ_CONTENT = "api.content.read"
	val READ_PRIVATE_CONTENT = "api.content.private.read"
	val UPDATE_CONTENT = "api.content.update"
	val UPLOAD_CONTENT = "api.content.upload"
	val UPLOAD_PRE_SIGNED_CONTENT = "api.content.upload.url"
	val REVIEW_CONTENT = "api.content.review"
	val PUBLISH_CONTENT = "api.content.publish"
	val PUBLISH_CONTENT_PUBLIC = "api.content.publish.public"
	val PUBLISH_CONTENT_UNLSTED = "api.content.publish.unlisted"
	val LINK_DIAL_CONTENT = "api.content.dialcode.link"
	val LINK_DIAL_COLLECTION = "api.collection.dialcode.link"
	val RESERVE_DIAL_CONTENT = "api.content.dialcode.reserve"
	val RELEASE_DIAL_CONTENT = "api.content.dialcode.release"
	val RESERVE_DIAL_COLLECTION = "api.collection.dialcode.reserve"
	val RELEASE_DIAL_COLLECTION = "api.collection.dialcode.release"
	val ASSET_LICENSE_VALIDATE = "api.asset.license.validate"
	val IMPORT_CSV = "api.collection.import.csv"
	val EXPORT_CSV = "api.collection.export.csv"
	val APPLICATION_AUDIT_HISTORY = "api.audit.history"
	val APPLICATION_SEARCH = "api.search"
	val APPLICATION_PRIVATE_SEARCH = "api.private.search"
	val APPLICATION_COUNT = "api.search.count"

	// License APIs
	val CREATE_LICENSE = "api.license.create"
	val READ_LICENSE = "api.license.read"
	val UPDATE_LICENSE = "api.license.update"
	val RETIRE_LICENSE = "api.license.retire"
	val RETIRE_CONTENT = "api.content.retire"
	val DISCARD_CONTENT = "api.content.discard"
	val COPY_CONTENT = "api.content.copy"
	val FlAG_CONTENT = "api.content.flag"
	val FLAG_CONTENT = "api.content.flag"
	val ACCEPT_FLAG = "api.content.flag.accept"
	val ACCEPT_FLAG_CONTENT = "api.content.flag.accept"
	val REJECT_CONTENT = "api.content.review.reject"
	val IMPORT_CONTENT = "api.content.import"
	val SYSTEM_UPDATE_CONTENT = "api.content.system.update"

	// Collection APIs
	val CREATE_COLLECTION = "api.collection.create"
	val READ_COLLECTION = "api.collection.read"
	val READ_PRIVATE_COLLECTION = "api.collection.private.read"
	val UPDATE_COLLECTION = "api.collection.update"
	val RETIRE_COLLECTION = "api.collection.retire"
	val COPY_COLLECTION = "api.collection.copy"
	val DISCARD_COLLECTION = "api.collection.discard"
	val FlAG_COLLECTION = "api.collection.flag"
	val ACCEPT_FLAG_COLLECTION = "api.collection.flag.accept"
	val ADD_HIERARCHY = "api.content.hierarchy.add"
	val REMOVE_HIERARCHY = "api.content.hierarchy.remove"
	val ADD_HIERARCHY_V4 = "api.collection.hierarchy.add"
	val REMOVE_HIERARCHY_V4 = "api.collection.hierarchy.remove"
	val UPDATE_HIERARCHY_V4 = "api.collection.hierarchy.update"
	val GET_HIERARCHY_V4 = "api.collection.hierarchy.get"
	val SYSTEM_UPDATE_COLLECTION = "api.collection.system.update"
	val REVIEW_COLLECTION = "api.collection.review"
	val REJECT_COLLECTION = "api.collection.review.reject"

	// Asset APIs
	val CREATE_ASSET = "api.asset.create"
	val READ_ASSET = "api.asset.read"
	val UPDATE_ASSET = "api.asset.update"
	val UPLOAD_ASSET = "api.asset.upload"
	val UPLOAD_PRE_SIGNED_ASSET = "api.asset.upload.url"
	val COPY_ASSET = "api.asset.copy"

	// Category APIs
	val CREATE_CATEGORY = "api.category.create"
	val READ_CATEGORY = "api.category.read"
	val UPDATE_CATEGORY = "api.category.update"
	val RETIRE_CATEGORY = "api.category.retire"

	// Channel APIs
	val CREATE_CHANNEL = "api.channel.create"
	val READ_CHANNEL = "api.channel.read"
	val UPDATE_CHANNEL = "api.channel.update"
	val LIST_CHANNEL = "api.channel.list"
	val RETIRE_CHANNEL = "api.channel.retire"

	// App APIs
	val REGISTER_APP = "api.app.register"
	val READ_APP = "api.app.read"
	val UPDATE_APP = "api.app.update"
	val APPROVE_APP = "api.app.approve"
	val REJECT_APP = "api.app.reject"
	val RETIRE_APP = "api.app.retire"

	// Event APIs
	val CREATE_EVENT = "api.event.create"
	val UPDATE_EVENT = "api.event.update"
	val PUBLISH_EVENT = "api.event.publish"

	// Event Set APIs
	val CREATE_EVENT_SET = "api.eventset.create"
	val UPDATE_EVENT_SET = "api.eventset.update"
	val PUBLISH_EVENT_SET = "api.eventset.publish"

	// Object APIs
	val CREATE_OBJECT = "api.learning.object.create"
	val READ_OBJECT = "api.learning.object.read"
	val UPDATE_OBJECT = "api.learning.object.update"
	val SEARCH_OBJECTS = "api.learning.objects.search"

	// Taxonomy APIs
	val CREATE_FRAMEWORK = "api.framework.create"
	val READ_FRAMEWORK = "api.framework.read"
	val UPDATE_FRAMEWORK = "api.framework.update"
	val LIST_FRAMEWORK = "api.framework.list"
	val COPY_FRAMEWORK = "api.framework.copy"
	val PUBLISH_FRAMEWORK = "api.framework.publish"
	val RETIRE_FRAMEWORK = "api.framework.retire"

	val CREATE_TAXONOMY_CATEGORY = "api.taxonomy.category.create"
	val READ_TAXONOMY_CATEGORY = "api.taxonomy.category.read"
	val UPDATE_TAXONOMY_CATEGORY = "api.taxonomy.category.update"
	val SEARCH_TAXONOMY_CATEGORY = "api.taxonomy.category.search"
	val RETIRE_TAXONOMY_CATEGORY = "api.taxonomy.category.retire"

	val CREATE_CATEGORY_INSTANCE = "api.taxonomy.category.instance.create"
	val READ_CATEGORY_INSTANCE = "api.taxonomy.category.instance.read"
	val UPDATE_CATEGORY_INSTANCE = "api.taxonomy.category.instance.update"
	val SEARCH_CATEGORY_INSTANCE = "api.taxonomy.category.instance.search"

	val CREATE_TERM = "api.taxonomy.term.create"
	val READ_TERM = "api.taxonomy.term.read"
	val UPDATE_TERM = "api.taxonomy.term.update"
	val SEARCH_TERM = "api.taxonomy.term.search"
	val RETIRE_TERM = "api.taxonomy.term.retire"

	val CREATE_OBJECT_CATEGORY = "api.object.category.create"
	val READ_OBJECT_CATEGORY = "api.object.category.read"
	val UPDATE_OBJECT_CATEGORY = "api.object.category.update"
	val SEARCH_OBJECT_CATEGORY = "api.object.category.search"
	val RETIRE_OBJECT_CATEGORY = "api.object.category.retire"

	val CREATE_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.create"
	val READ_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.read"
	val UPDATE_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.update"
	val SEARCH_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.search"
	val RETIRE_OBJECT_CATEGORY_DEFINITION = "api.object.category.definition.retire"

	// Lock APIs
	val CREATE_LOCK = "api.lock.create"
	val READ_LOCK = "api.lock.read"
	val LIST_LOCK = "api.lock.list"
	val RETIRE_LOCK = "api.lock.retire"
	val REFRESH_LOCK = "api.lock.refresh"

	// Additional taxonomy APIs
	val RETIRE_CATEGORY_INSTANCE = "api.taxonomy.category.instance.retire"
}
