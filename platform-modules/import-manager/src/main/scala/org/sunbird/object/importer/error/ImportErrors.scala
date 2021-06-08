package org.sunbird.`object`.importer.error

object ImportErrors {

	//Error Codes
	val ERR_INVALID_IMPORT_REQUEST: String = "ERR_INVALID_IMPORT_REQUEST"
	val ERR_REQUEST_LIMIT_EXCEED: String = "ERR_REQUEST_LIMIT_EXCEED"
	val ERR_READ_SOURCE: String = "ERR_READ_SOURCE"
	val ERR_REQUIRED_PROPS_VALIDATION: String = "ERR_REQUIRED_PROPS_VALIDATION"
	val BE_JOB_REQUEST_EXCEPTION: String = "BE_JOB_REQUEST_EXCEPTION"
	val ERR_OBJECT_STAGE_VALIDATION: String = "ERR_OBJECT_STAGE_VALIDATION"

	//Error Messages
	val ERR_INVALID_IMPORT_REQUEST_MSG: String = "Invalid Request! Please Provide Valid Request."
	val ERR_REQUEST_LIMIT_EXCEED_MSG: String = "Request Limit Exceeded. Maximum Allowed Objects In Single Request is "
	val ERR_READ_SOURCE_MSG: String = "Received Invalid Response While Reading Data From Source. Response Code is : "
	val ERR_REQUIRED_PROPS_VALIDATION_MSG: String = "Validation Failed! Mandatory Properties Are "
	val BE_JOB_REQUEST_EXCEPTION_MSG: String = "Kafka Event Is Not Generated Properly."
	val ERR_OBJECT_STAGE_VALIDATION_MSG: String = "Object Stage Validation Failed! Valid Object Stages Are "
}
