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

	//Question APIs
	val CREATE_QUESTION = "api.question.create"
	val READ_QUESTION = "api.question.read"
	val UPDATE_QUESTION = "api.question.update"
	val REVIEW_QUESTION = "api.question.review"
	val PUBLISH_QUESTION = "api.question.publish"
	val RETIRE_QUESTION = "api.question.retire"
	val IMPORT_QUESTION = "api.question.import"

	//QuestionSet APIs
	val CREATE_QUESTION_SET = "api.questionset.create"
	val READ_QUESTION_SET = "api.questionset.read"
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

}
