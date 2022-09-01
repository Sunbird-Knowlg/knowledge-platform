package org.sunbird.content.dial

object DIALErrors {

	//Error Codes
	val ERR_DIALCODE_LINK_REQUEST: String = "ERR_DIALCODE_LINK_REQUEST"
	val ERR_DIALCODE_LINK: String = "ERR_DIALCODE_LINK"
	val ERR_INVALID_CHANNEL: String = "ERR_INVALID_CHANNEL"
	val ERR_CONTENT_BLANK_OBJECT_ID: String = "ERR_CONTENT_BLANK_OBJECT_ID"
	val ERR_CONTENT_MIMETYPE: String = "ERR_CONTENT_MIMETYPE"
	val ERR_INVALID_COUNT: String = "ERR_INVALID_COUNT"
	val ERR_INVALID_COUNT_RANGE: String = "ERR_INVALID_COUNT_RANGE"
	val ERR_CONTENT_INVALID_OBJECT: String = "ERR_CONTENT_INVALID_OBJECT"
	val ERR_DUPLICATE_DIAL_CODES: String = "ERR_DUPLICATE_DIAL_CODES"
	val ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING: String = "ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING"
	val ERR_CONTENT_RETIRED_OBJECT_ID: String = "ERR_CONTENT_RETIRED_OBJECT_ID"
	val ERR_CONTENT_MISSING_RESERVED_DIAL_CODES: String = "ERR_CONTENT_MISSING_RESERVED_DIAL_CODES"
	val ERR_ALL_DIALCODES_UTILIZED: String = "ERR_ALL_DIALCODES_UTILIZED"

	//Error Messages
	val ERR_INVALID_REQ_MSG: String = "Invalid Request! Please Provide Valid Request."
	val ERR_REQUIRED_PROPS_MSG: String = "Invalid Request! Please Provide Required Properties In Request."
	val ERR_MAX_LIMIT_MSG: String = "Max Limit For Link Content To DIAL Code In A Request Is "
	val ERR_DIAL_NOT_FOUND_MSG: String = "DIAL Code Not Found With Id(s): "
	val ERR_CONTENT_NOT_FOUND_MSG: String = "Content Not Found With Id(s): "
	val ERR_SERVER_ERROR_MSG: String = "Something Went Wrong While Processing Your Request. Please Try Again After Sometime!"
	val ERR_INVALID_CHANNEL_MSG: String = "Invalid Channel Id."
	val ERR_CONTENT_BLANK_OBJECT_ID_MSG: String = "Content Id cannot be Blank."
	val ERR_CONTENT_MIMETYPE_MSG: String = "Invalid mimeType."
	val ERR_INVALID_COUNT_MSG: String = "Invalid dialcode count."
	val ERR_INVALID_COUNT_RANGE_MSG: String = "Invalid dialcode count range. It should be between 1 to "
	val ERR_DIAL_GEN_LIST_EMPTY_MSG: String = "Dialcode generated list is empty. Please Try Again After Sometime!"
	val ERR_DIAL_GENERATION_MSG: String = "Error During generate Dialcode. Please Try Again After Sometime!"
	val ERR_DIAL_INVALID_COUNT_RESPONSE = "No new DIAL Codes have been generated, as requested count is less or equal to existing reserved dialcode count."
	val ERR_CONTENT_INVALID_OBJECT_MSG = "Invalid Request. Cannot update 'Live' content."
	val ERR_DUPLICATE_DIAL_CODES_MSG: String = "QR Code should not be linked to multiple contents. Please validate: "
	val ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING_MSG: String = "Required fields for content link dialcode are missing: "
	val ERR_CONTENT_RETIRED_OBJECT_ID_MSG: String = "Invalid Request. Cannot update 'Retired' content."
	val ERR_CONTENT_MISSING_RESERVED_DIAL_CODES_MSG: String = "Invalid Request. Content does not have reserved DIAL codes."
	val ERR_ALL_DIALCODES_UTILIZED_MSG: String = "Error! All Reserved DIAL Codes are Utilized."
}
