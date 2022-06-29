package utils

object QuestionSetOperations extends Enumeration {
  val createQuestionSet, readQuestionSet, readPrivateQuestionSet, updateQuestionSet, reviewQuestionSet, publishQuestionSet,
  retireQuestionSet, addQuestion, removeQuestion, updateHierarchyQuestion, readHierarchyQuestion,
  rejectQuestionSet, importQuestionSet, systemUpdateQuestionSet, copyQuestionSet = Value
}
