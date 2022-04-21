package org.sunbird.content.dial

object DIALErrors {

	//Error Codes
	val ERR_DIALCODE_LINK_REQUEST: String = "ERR_DIALCODE_LINK_REQUEST"
	val ERR_DIALCODE_LINK: String = "ERR_DIALCODE_LINK"
	val ERR_DUPLICATE_DIAL_CODES: String = "ERR_DUPLICATE_DIAL_CODES"

	//Error Messages
	val ERR_INVALID_REQ_MSG: String = "Invalid Request! Please Provide Valid Request."
	val ERR_REQUIRED_PROPS_MSG: String = "Invalid Request! Please Provide Required Properties In Request."
	val ERR_MAX_LIMIT_MSG: String = "Max Limit For Link Content To DIAL Code In A Request Is "
	val ERR_DIAL_NOT_FOUND_MSG: String = "DIAL Code Not Found With Id(s): "
	val ERR_CONTENT_NOT_FOUND_MSG: String = "Content Not Found With Id(s): "
	val ERR_SERVER_ERROR_MSG: String = "Something Went Wrong While Processing Your Request. Please Try Again After Sometime!"
	val ERR_DUPLICATE_DIAL_CODES_MSG: String = "QR Code should not be linked to multiple contents. Please validate: "
}
