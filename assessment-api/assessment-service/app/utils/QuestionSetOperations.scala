package utils

object QuestionSetOperations extends Enumeration {
	val createQuestionSet, readQuestionSet, updateQuestionSet, reviewQuestionSet, publishQuestionSet,
	retireQuestionSet, addQuestion, removeQuestion, updateHierarchyQuestion, readHierarchyQuestion,
	rejectQuestionSet, importQuestionSet = Value
}
