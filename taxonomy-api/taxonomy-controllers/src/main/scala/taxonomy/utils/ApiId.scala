package taxonomy.utils

object ApiId {

	final val APPLICATION_HEALTH = "api.taxonomy.service.health"
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

	final val CREATE_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.create"
	final val READ_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.read"
	final val UPDATE_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.update"
	final val RETIRE_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.retire"
	final val PUBLISH_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.publish"
	final val REVIEW_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.review"
	final val REJECT_COMPETENCY_FRAMEWORK = "api.taxonomy.competencyframework.reject"

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
	final val BULK_UPDATE_TERM = "api.taxonomy.term.bulk.update"
	final val BULK_VALIDATE_TERM = "api.taxonomy.term.bulk.validate"
	final val BULK_COMMIT_TERM = "api.taxonomy.term.bulk.commit"
	final val BULK_DOWNLOAD_TERM = "api.taxonomy.term.bulk.download"

	final val CREATE_LOCK = "api.taxonomy.lock.create"
	final val REFRESH_LOCK = "api.taxonomy.lock.refresh"
	final val RETIRE_LOCK = "api.taxonomy.lock.retire"
	final val LIST_LOCK = "api.taxonomy.lock.list"


}
