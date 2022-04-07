package org.sunbird.actors

import org.mortbay.util.StringUtil
import org.sunbird.common.dto.{Request, Response, ResponseParams}
import org.sunbird.graph.dac.model.Node
import org.sunbird.utils.AssessmentConstants
import org.sunbird.common.exception.{ResponseCode}

import java.util

trait copyTrait {

	private def getQuestionSetRequest(): Request = {
		val request = new Request()
		request.setContext(new java.util.HashMap[String, AnyRef]() {
			{
				put("graph_id", "domain")
				put("version", "1.0")
				put("objectType", "QuestionSet")
				put("schemaName", "questionset")
			}
		})
		request.setObjectType("QuestionSet")
		request
	}

	def getQuestionSetCopyRequest(): Request = {
		val request = getQuestionSetRequest()
		request.putAll(new util.HashMap[String, AnyRef]() {
			{
				put("createdBy", "Shikshalokam")
				put("createdFor", new util.ArrayList[String]() {
					{
						add("Shikshalokam")
					}
				})
				put("name", "NewRootNode")
			}
		})
		request
	}

	def getInvalidQuestionSetCopyRequest(): Request = {
		val request = getQuestionSetRequest()
		request.putAll(new util.HashMap[String, AnyRef]() {
			{
				put("name", "NewRootNode")
			}
		})
		request
	}

	def getInvalidQuestionCopyRequest(): Request = {
		val request = getQuestionRequest()
		request.putAll(new util.HashMap[String, AnyRef]() {
			{
				put("name", "NewQuestion")
			}
		})
		request
	}

	private def getQuestionRequest(): Request = {
		val request = new Request()
		request.setContext(new java.util.HashMap[String, AnyRef]() {
			{
				put("graph_id", "domain")
				put("version", "1.0")
				put("objectType", "Question")
				put("schemaName", "question")
			}
		})
		request.setObjectType("Question")
		request
	}

	def getQuestionCopyRequest(): Request = {
		val request = getQuestionRequest()
		request.putAll(new util.HashMap[String, AnyRef]() {
			{
				put("createdBy", "Shikshalokam")
				put("createdFor", new util.ArrayList[String]() {
					{
						add("Shikshalokam")
					}
				})
				put("name", "NewQuestion")
			}
		})
		request
	}

	private def getNode(objectType: String, identifier: String, primaryCategory: String, visibility: String, name: String, id: Long,
						status: String): Node = {
		val node = new Node("domain", "DATA_NODE", objectType)
		node.setGraphId("domain")
		node.setIdentifier(identifier)
		node.setId(id)
		node.setNodeType("DATA_NODE")
		node.setObjectType(objectType)
		node.setMetadata(new util.HashMap[String, AnyRef]() {
			{
				put("code", "xyz")
				put("mimeType", {
					if (StringUtil.endsWithIgnoreCase(objectType, AssessmentConstants.QUESTIONSET_SCHEMA_NAME)) {
						AssessmentConstants.QUESTIONSET_MIME_TYPE
					} else {
						AssessmentConstants.QUESTION_MIME_TYPE
					}
				})
				put("createdOn", "2022-03-16T14:35:11.040+0530")
				put("objectType", objectType)
				put("primaryCategory", primaryCategory)
				put("contentDisposition", "inline")
				put("contentEncoding", "gzip")
				put("lastUpdatedOn", "2022-03-16T14:38:51.287+0530")
				put("showSolutions", "No")
				put("allowAnonymousAccess", "Yes")
				put("identifier", identifier)
				put("lastStatusChangedOn", "2022-03-16T14:35:11.040+0530")
				put("visibility", visibility)
				put("showTimer", "No")
				put("version", 1.asInstanceOf[Number])
				put("showFeedback", "No")
				put("versionKey", "1234")
				put("license", "CC BY 4.0")
				put("compatibilityLevel", 5.asInstanceOf[Number])
				put("name", name)
				put("status", status)
			}
		})
		node
	}

	def getExistingRootNode(): Node = {
		val node = getNode("QuestionSet", "do_1234", "Observation", AssessmentConstants.VISIBILITY_DEFAULT, "ExistingRootNode", 1234,
			"Live")
		node.getMetadata.put("childNodes", Array("do_5678"))
		node
	}

	def getNewRootNode(): Node = {
		val node = getNode("QuestionSet", "do_9876", "Observation", AssessmentConstants.VISIBILITY_DEFAULT, "NewRootNode", 0, "Draft")
		node.getMetadata.put("origin", "do_1234")
		node.getMetadata.put("originData", "{\"name\":\"ExistingRootNode\",\"copyType\":\"deep\"}")
		node.getMetadata.put("createdFor", Array("ShikshaLokam"))
		node.getMetadata.put("createdBy", "ShikshaLokam")
		node.setExternalData(new util.HashMap[String, AnyRef]() {
			{
				put("instructions", "This is the instruction.")
				put("outcomeDeclaration", "This is outcomeDeclaration.")
			}
		})
		node
	}

	def getExistingQuestionNode(): Node = {
		val node = getNode("Question", "do_1234", "Slider", AssessmentConstants.VISIBILITY_DEFAULT, "ExistingQuestionNode", 1234,
			"Live")
		node
	}

	def getQuestionNode(): Node = {
		val node = getNode("Question", "do_5678", "Slider", AssessmentConstants.VISIBILITY_PARENT, "Question1", 0, "Draft")
		node.setExternalData(new util.HashMap[String, AnyRef]() {
			{
				put("answer", "This is Answer.")
				put("body", "This is Body.")
			}
		})
		node
	}

	def getNewQuestionNode(): Node = {
		val node = getNode("Question", "do_5678", "Slider", AssessmentConstants.VISIBILITY_DEFAULT, "NewQuestion", 0, "Draft")
		node.setExternalData(new util.HashMap[String, AnyRef]() {
			{
				put("answer", "This is Answer.")
				put("body", "This is Body.")
			}
		})
		node.getMetadata.put("origin", "do_1234")
		node.getMetadata.put("originData", "{\\\"name\\\":\\\"Q2\\\",\\\"copyType\\\":\\\"deep\\\",\\\"license\\\":\\\"CC BY 4.0\\\"}")
		node.getMetadata.put("createdFor", Array("ShikshaLokam"))
		node.getMetadata.put("createdBy", "ShikshaLokam")
		node
	}

	def getSuccessfulResponse(): Response = {
		val response = new Response
		response.setVer("3.0")
		val responseParams = new ResponseParams
		responseParams.setStatus("successful")
		response.setParams(responseParams)
		response.setResponseCode(ResponseCode.OK)
		response
	}

	def getExternalPropsRequest(): Request = {
		val request = getQuestionSetRequest()
		request.putAll(new util.HashMap[String, AnyRef]() {
			{
				put("instructions", "This is the instruction.")
				put("outcomeDeclaration", "This is outcomeDeclaration.")
			}
		})
		request
	}

	def getExternalPropsResponseWithData(): Response = {
		val response = getSuccessfulResponse()
		response.put("instructions", "This is the instruction for this QuestionSet")
		response.put("outcomeDeclaration", "This is the outcomeDeclaration for this QuestionSet")
		response.put("hierarchy", "{\"code\":\"ExistingRootNode\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\"," +
		  "\"channel\":\"{{all}}\",\"language\":[\"English\"],\"showHints\":\"No\",\"mimeType\":\"application/vnd" + "" + ".sunbird" + ""
		  + ".questionset\",\"createdOn\":\"2022-03-16T14:35:11.040+0530\",\"objectType\":\"QuestionSet\"," +
		  "\"primaryCategory\":\"Observation\",\"contentDisposition\":\"inline\",\"contentEncoding\":\"gzip\"," +
		  "\"lastUpdatedOn\":\"2022-03-16T14:38:51.287+0530\",\"generateDIALCodes\":\"No\",\"showSolutions\":\"No\"," +
		  "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_1234\"," + "\"lastStatusChangedOn\":\"2022-03-16T14:35:11.040+0530\"," +
		  "\"requiresSubmit\":\"No\",\"visibility\":\"Default\"," + "" + "" + "\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"showTimer\":\"No\"," +
		  "\"childNodes\":[\"do_113495678820704256110\"]," + "\"setType\":\"materialised\",\"version\":1," + "\"showFeedback\":\"No\"," +
		  "\"versionKey\":\"1647421731287\"," + "\"license\":\"CC BY 4.0\",\"depth\":0," + "\"compatibilityLevel\":5," +
		  "\"IL_FUNC_OBJECT_TYPE\":\"QuestionSet\"," + "\"allowBranching\":\"No\"," + "\"navigationMode\":\"non-linear\"," +
		  "\"name\":\"CopyQuestionSet\",\"shuffle\":true," + "\"IL_UNIQUE_ID\":\"do_11349567701798912019\",\"status\":\"Live\"," +
		  "\"children\":[{\"parent\":\"do_11349567701798912019\",\"code\":\"Q1\",\"channel\":\"{{channel_id}}\"," +
		  "\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\"," +
		  "\"createdOn\":\"2022-03-16T14:38:51.043+0530\",\"objectType\":\"Question\",\"primaryCategory\":\"Slider\"," +
		  "\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2022-03-16T14:38:51.042+0530\"," + "\"contentEncoding\":\"gzip\"," +
		  "\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\"," + "\"identifier\":\"do_113495678820704256110\"," +
		  "\"lastStatusChangedOn\":\"2022-03-16T14:38:51.043+0530\"," + "\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":1," +
		  "\"languageCode\":[\"en\"],\"version\":1," + "\"versionKey\":\"1647421731066\",\"showFeedback\":\"No\",\"license\":\"CC BY " +
		  "4.0\",\"depth\":1," + "\"compatibilityLevel\":4,\"name\":\"Q1\",\"status\":\"Live\"}]}")
		response.put("body", "This is Body")
		response.put("answer", "This is Answer")
		response
	}

	def getReadPropsResponseForQuestion(): Response = {
		val response = getSuccessfulResponse()
		response.put("answer", "This is Answer 2")
		response.put("body", "This is Body 2")
		response
	}

	def getUpsertNode(): Node = {
		val node = getNewRootNode()
		node.setExternalData(new util.HashMap[String, AnyRef]() {
			{
				put("hierarchy", "{\\\"identifier\\\":\\\"do_9876\\\"," + "\\\"children\\\":[{\\\"parent\\\":\\\"do_9876\\\"," +
				  "\\\"code\\\":\\\"b65f36d1-a243-4043-9df7-da14a2dd83b9\\\",\\\"channel\\\":\\\"{{channel_id}}\\\"," +
				  "\\\"language\\\":[\\\"English\\\"],\\\"mimeType\\\":\\\"application/vnd.sunbird.question\\\"," +
				  "\\\"createdOn\\\":\\\"2022-03-23T15:45:28.620+0530\\\",\\\"objectType\\\":\\\"Question\\\"," +
				  "\\\"primaryCategory\\\":\\\"Slider\\\",\\\"contentDisposition\\\":\\\"inline\\\"," +
				  "\\\"lastUpdatedOn\\\":\\\"2022-03-23T15:45:28.616+0530\\\",\\\"contentEncoding\\\":\\\"gzip\\\"," +
				  "\\\"showSolutions\\\":\\\"No\\\",\\\"allowAnonymousAccess\\\":\\\"Yes\\\"," +
				  "\\\"identifier\\\":\\\"do_11350066609045504013\\\",\\\"lastStatusChangedOn\\\":\\\"2022-03-23T15:45:28.621+0530\\\"," +
				  "\\\"visibility\\\":\\\"Parent\\\",\\\"showTimer\\\":\\\"No\\\",\\\"index\\\":1,\\\"languageCode\\\":[\\\"en\\\"]," +
				  "\\\"version\\\":1,\\\"versionKey\\\":\\\"1648030746815\\\",\\\"showFeedback\\\":\\\"No\\\",\\\"license\\\":\\\"CC BY "
				  + "4.0\\\",\\\"depth\\\":1,\\\"compatibilityLevel\\\":4,\\\"name\\\":\\\"Q1\\\",\\\"status\\\":\\\"Draft\\\"}]}")
			}
		})
		node
	}

	def getRootNodeWithBL(rootId: String, sectionId: String, addBranchingLogic: Boolean, withChildren: Boolean): Node = {
		val node = getNode("QuestionSet", rootId, "Observation", AssessmentConstants.VISIBILITY_DEFAULT, "ExistingRootNode", 1234, "Live")
		if (withChildren) {
			val section = getNode("QuestionSet", sectionId, "Observation", AssessmentConstants.VISIBILITY_DEFAULT, "Section_1", 1234, "Live")
			val children = new util.ArrayList[util.Map[String, AnyRef]]()
			children.add(getNode("Question", "do_5555", "Slider", AssessmentConstants.VISIBILITY_DEFAULT, "Question1", 1234, "Live")
			  .getMetadata)
			children.add(getNode("Question", "do_7777", "Slider", AssessmentConstants.VISIBILITY_DEFAULT, "Question2", 1234, "Live")
			  .getMetadata)
			if (addBranchingLogic) {
				section.getMetadata.put("branchingLogic", new util.HashMap[String, AnyRef]() {
					{
						put("do_5555", new util.HashMap[String, AnyRef]() {
							put("target", new util.ArrayList[String]() {
								{
									add("do_7777")
								}
							})
							put("preCondition", new util.HashMap[String, AnyRef]())
							put("source", new util.ArrayList[String]())
						})
						put("do_7777", new util.HashMap[String, AnyRef]() {
							put("target", new util.ArrayList[String]())
							put("preCondition", new util.HashMap[String, AnyRef]() {
								{
									put("and", new util.ArrayList[util.HashMap[String, AnyRef]]() {
										add(new util.HashMap[String, AnyRef]() {
											put("eq", new util.ArrayList[AnyRef]() {
												{
													add(new util.HashMap[String, String]() {
														put("var", "do_5555" + ".response1.value")
														put("type", "responseDeclaration")
													})
													add("0")
												}
											})
										})
									})
								}
							})
							put("source", new util.ArrayList[String]() {
								{
									add("do_5555")
								}
							})
						})
					}
				})
			}
			node.getMetadata.put("childNodes", new util.ArrayList[String]() {
				{
					add(sectionId)
					add("do_5555")
					add("do_7777")
				}
			})
			section.getMetadata.put("children", children)
			node.getMetadata.put("children", new util.ArrayList[util.Map[String, AnyRef]]() {
				{
					add(section.getMetadata)
				}
			})
		}
		node
	}

	def getQuestionNodeBL(identifier: String): Node = {
		val node = getNode("Question", identifier, "Slider", AssessmentConstants.VISIBILITY_DEFAULT, identifier, 1234, "Live")
		node
	}

	def getUpsertNodeBLWithoutBL(): Node = {
		val node = getRootNodeWithBL("do_9876", "do_3333", false, false)
		node.setExternalData(new util.HashMap[String, AnyRef]() {
			{
				put("hierarchy", "{\"identifier\":\"do_9876\",\"children\":[{\"parent\":\"do_9876\"," +
				  "\"code\":\"9f0332ad-c3e3-4803-b673-50174aff24e3\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\"," +
				  "\"channel\":\"{{channel_id}}\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.questionset\"," +
				  "\"showHints\":\"No\",\"createdOn\":\"2022-04-06T12:51:53.592+0530\",\"objectType\":\"QuestionSet\"," +
				  "\"primaryCategory\":\"Observation\",\"children\":[{\"parent\":\"do_3333\",\"code\":\"Q1\"," +
				  "\"channel\":\"{{channel_id}}\"," + "\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\"," +
				  "\"createdOn\":\"2022-04-06T10:13:32.859+0530\"," + "\"objectType\":\"Question\",\"primaryCategory\":\"Slider\"," +
				  "\"contentDisposition\":\"inline\"," + "\"lastUpdatedOn\":\"2022-04-06T10:13:32.911+0530\",\"contentEncoding\":\"gzip\"," +
				  "" + "\"showSolutions\":\"No\"," + "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_5555\"," +
				  "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.859+0530\"," + "\"visibility\":\"Default\",\"showTimer\":\"No\"," +
				  "\"index\":1," + "\"languageCode\":[\"en\"],\"version\":1," + "\"versionKey\":\"1649220212911\",\"showFeedback\":\"No\"," +
				  "\"license\":\"CC BY " + "4.0\",\"depth\":2,\"compatibilityLevel\":4," + "\"name\":\"Q1\",\"status\":\"Live\"}," +
				  "{\"parent\":\"do_3333\",\"code\":\"Q2\",\"channel\":\"{{channel_id}}\"," + "\"language\":[\"English\"]," +
				  "\"mimeType\":\"application/vnd.sunbird.question\",\"createdOn\":\"2022-04-06T10:13:32.896+0530\"," +
				  "\"objectType\":\"Question\",\"primaryCategory\":\"Slider\",\"contentDisposition\":\"inline\"," +
				  "\"lastUpdatedOn\":\"2022-04-06T10:13:32.954+0530\",\"contentEncoding\":\"gzip\",\"showSolutions\":\"No\"," +
				  "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_7777\"," +
				  "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.896+0530\"," + "\"visibility\":\"Default\",\"showTimer\":\"No\"," +
				  "\"index\":2," + "\"languageCode\":[\"en\"],\"version\":1," + "\"versionKey\":\"1649220212954\",\"showFeedback\":\"No\"," +
				  "\"license\":\"CC BY " + "4.0\",\"depth\":2,\"compatibilityLevel\":4," + "\"name\":\"Q2\",\"status\":\"Live\"}]," +
				  "\"contentDisposition\":\"inline\"," + "\"lastUpdatedOn\":\"2022-04-06T12:51:53.591+0530\"," +
				  "\"contentEncoding\":\"gzip\",\"generateDIALCodes\":\"No\"," + "\"showSolutions\":\"No\"," +
				  "\"allowAnonymousAccess\":\"Yes\"," + "\"identifier\":\"do_3333\"," +
				  "\"lastStatusChangedOn\":\"2022-04-06T12:51:53.592+0530\",\"requiresSubmit\":\"No\"," + "\"visibility\":\"Parent\"," +
				  "\"showTimer\":\"No\",\"index\":1,\"setType\":\"materialised\",\"languageCode\":[\"en\"],\"version\":1," + "" + "" +
				  "\"versionKey\":\"1649229713592\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"name\":\"S1\"," +
				  "\"navigationMode\":\"non-linear\",\"allowBranching\":\"Yes\",\"shuffle\":true,\"status\":\"Draft\"}]}")
			}
		})
		node
	}

	def getUpsertNodeBLWithBL(): Node = {
		val node = getRootNodeWithBL("do_9876", "do_3333", false, false)
		node.setExternalData(new util.HashMap[String, AnyRef]() {
			{
				put("hierarchy", "{\"identifier\":\"do_9876\",\"children\":[{\"parent\":\"do_9876\"," + "\"code\":\"S1\"," +
				  "\"allowSkip\":\"Yes\",\"containsUserData\":\"No\",\"channel\":\"{{channel_id}}\"," +
				  "\"branchingLogic\":{\"do_7777\":{\"preCondition\":{\"and\":[{\"eq\":[{\"type\":\"responseDeclaration" + "\"," +
				  "\"var\":\"do_5555.response1.value\"},\"0\"]}]},\"target\":[]," + "\"source\":[\"do_5555\"]}," +
				  "\"do_5555\":{\"preCondition\":{}," + "\"target\":[\"do_7777\"],\"source\":[]}},\"description\":\"Section 1\"," +
				  "\"language\":[\"English\"]," + "\"mimeType\":\"application/vnd" + ".sunbird.questionset\",\"showHints\":\"No\"," +
				  "\"createdOn\":\"2022-04-04T16:30:59.566+0530\"," + "\"objectType\":\"QuestionSet\"," +
				  "\"primaryCategory\":\"Observation\"," + "\"children\":[{\"parent\":\"do_3333\",\"code\":\"Q1\"," +
				  "\"channel\":\"{{channel_id}}\"," + "\"description\":\"Q1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd" +
				  ".sunbird" + ".question\"," + "\"createdOn\":\"2022-04-04T16:30:59.539+0530\",\"objectType\":\"Question\"," +
				  "\"primaryCategory\":\"Slider\"," + "\"contentDisposition\":\"inline\"," +
				  "\"lastUpdatedOn\":\"2022-04-04T16:32:46.200+0530\",\"contentEncoding\":\"gzip\"," + "\"showSolutions\":\"No\"," +
				  "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_5555\"," +
				  "\"lastStatusChangedOn\":\"2022-03-29T15:37:42.837+0530\",\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":1," +
				  "\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1649070166325\",\"showFeedback\":\"No\",\"license\":\"CC BY "
				  + "4.0\"," + "\"depth\":2,\"compatibilityLevel\":4,\"name\":\"Q1\",\"status\":\"Draft\"}," + "{\"parent\":\"do_3333\"," +
				  "\"code\":\"Q2\"," + "\"channel\":\"{{channel_id}}\",\"description\":\"Q2\"," + "\"language\":[\"English\"]," +
				  "\"mimeType\":\"application/vnd.sunbird" + "" + ".question\"," + "\"createdOn\":\"2022-03-29T15:37:42.852+0530\"," +
				  "\"objectType\":\"Question\",\"primaryCategory\":\"Slider\"," + "\"contentDisposition\":\"inline\"," +
				  "\"lastUpdatedOn\":\"2022-03-29T15:37:42.896+0530\",\"contentEncoding\":\"gzip\"," + "\"showSolutions\":\"No\"," +
				  "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_7777\"," +
				  "\"lastStatusChangedOn\":\"2022-03-29T15:37:42.852+0530\",\"visibility\":\"Default\",\"showTimer\":\"No\",\"index\":2,"
				  + "\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1648548462896\",\"showFeedback\":\"No\",\"license\":\"CC BY " +
				  "" + "4.0\"," + "\"depth\":2,\"compatibilityLevel\":4,\"name\":\"Q2\",\"status\":\"Live\"}]," +
				  "\"contentDisposition\":\"inline\"," + "\"lastUpdatedOn\":\"2022-04-04T16:32:46.273+0530\",\"contentEncoding\":\"gzip\"," +
				  "\"generateDIALCodes\":\"No\"," + "\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_3333\"," +
				  "" + "\"lastStatusChangedOn\":\"2022-03-29T15:37:42.872+0530\",\"requiresSubmit\":\"No\",\"visibility\":\"Parent\"," +
				  "\"showTimer\":\"No\",\"index\":1,\"setType\":\"materialised\",\"languageCode\":[\"en\"],\"version\":1," +
				  "\"versionKey\":\"1649070059566\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"name\":\"S1\"," +
				  "\"navigationMode\":\"non-linear\",\"allowBranching\":\"Yes\",\"shuffle\":true,\"status\":\"Draft\"}]}")
			}
		})
		node
	}

	def getRootExternalPropsResponseBL(): Response = {
		val response = getSuccessfulResponse()
		response.put("hierarchy", "{\"code\":\"CopyQuestionSetv21\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\"," +
		  "\"channel\":\"{{channel_id}}\",\"language\":[\"English\"],\"showHints\":\"No\",\"mimeType\":\"application/vnd.sunbird" + "" +
		  "" + ".questionset\",\"createdOn\":\"2022-04-06T10:13:15.975+0530\",\"objectType\":\"QuestionSet\"," +
		  "\"primaryCategory\":\"Observation\",\"contentDisposition\":\"inline\",\"contentEncoding\":\"gzip\"," +
		  "\"lastUpdatedOn\":\"2022-04-06T10:16:05.263+0530\",\"generateDIALCodes\":\"No\",\"showSolutions\":\"No\"," +
		  "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_1234\"," + "\"lastStatusChangedOn\":\"2022-04-06T10:13:15.975+0530\"," +
		  "\"requiresSubmit\":\"No\",\"visibility\":\"Default\"," + "\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"showTimer\":\"No\"," +
		  "\"childNodes\":[\"do_5555\"," + "\"do_2222\",\"do_7777\"],\"setType\":\"materialised\",\"version\":1,\"showFeedback\":\"No\","
		  + "\"versionKey\":\"1649220365263\",\"license\":\"CC BY 4.0\",\"depth\":0,\"compatibilityLevel\":5," +
		  "\"IL_FUNC_OBJECT_TYPE\":\"QuestionSet\",\"allowBranching\":\"No\",\"navigationMode\":\"non-linear\"," +
		  "\"name\":\"CopyQuestionSetv21\",\"shuffle\":true,\"IL_UNIQUE_ID\":\"do_1234\",\"status\":\"Live\"," +
		  "\"children\":[{\"parent\":\"do_1234\",\"code\":\"S1\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\"," +
		  "\"channel\":\"{{channel_id}}\",\"branchingLogic\":{\"do_5555\":{\"target\":[\"do_7777\"]," + "\"preCondition\":{}," +
		  "\"source\":[]}," + "\"do_7777\":{\"target\":[]," + "\"preCondition\":{\"and\":[{\"eq\":[{\"var\":\"do_5555.response1.value\","
		  + "\"type\":\"responseDeclaration\"}," + "\"0\"]}]},\"source\":[\"do_5555\"]}},\"language\":[\"English\"]," +
		  "\"mimeType\":\"application/vnd" + ".sunbird" + ".questionset\",\"showHints\":\"No\"," +
		  "\"createdOn\":\"2022-04-06T10:13:32.949+0530\"," + "\"objectType\":\"QuestionSet\"," + "\"primaryCategory\":\"Observation\"," +
		  "\"children\":[{\"parent\":\"do_2222\",\"code\":\"Q1\"," + "\"channel\":\"{{channel_id}}\"," + "\"language\":[\"English\"]," +
		  "\"mimeType\":\"application/vnd.sunbird.question\"," + "\"createdOn\":\"2022-04-06T10:13:32.859+0530\"," +
		  "\"objectType\":\"Question\",\"primaryCategory\":\"Slider\"," + "\"contentDisposition\":\"inline\"," +
		  "\"lastUpdatedOn\":\"2022-04-06T10:13:32.911+0530\",\"contentEncoding\":\"gzip\"," + "\"showSolutions\":\"No\"," +
		  "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_5555\"," + "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.859+0530\"," +
		  "\"visibility\":\"Default\",\"showTimer\":\"No\",\"index\":1," + "\"languageCode\":[\"en\"],\"version\":1," +
		  "\"versionKey\":\"1649220212911\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\"," + "\"depth\":2,\"compatibilityLevel\":4,"
		  + "\"name\":\"Q1\",\"status\":\"Live\"},{\"parent\":\"do_2222\"," + "\"code\":\"Q2\"," + "\"channel\":\"{{channel_id}}\"," +
		  "\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\"," +
		  "\"createdOn\":\"2022-04-06T10:13:32.896+0530\"," + "\"objectType\":\"Question\",\"primaryCategory\":\"Slider\"," +
		  "\"contentDisposition\":\"inline\"," + "\"lastUpdatedOn\":\"2022-04-06T10:13:32.954+0530\",\"contentEncoding\":\"gzip\"," +
		  "\"showSolutions\":\"No\"," + "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_7777\"," +
		  "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.896+0530\"," + "\"visibility\":\"Default\",\"showTimer\":\"No\",\"index\":2," +
		  "\"languageCode\":[\"en\"],\"version\":1," + "\"versionKey\":\"1649220212954\",\"showFeedback\":\"No\",\"license\":\"CC BY " +
		  "4.0\"," + "\"depth\":2,\"compatibilityLevel\":4," + "\"name\":\"Q2\",\"status\":\"Live\"}],\"contentDisposition\":\"inline\","
		  + "\"lastUpdatedOn\":\"2022-04-06T10:16:05.061+0530\"," + "\"contentEncoding\":\"gzip\",\"generateDIALCodes\":\"No\"," +
		  "\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\"," + "\"identifier\":\"do_2222\"," +
		  "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.949+0530\",\"requiresSubmit\":\"No\"," + "\"visibility\":\"Parent\"," +
		  "\"showTimer\":\"No\",\"index\":1,\"setType\":\"materialised\",\"languageCode\":[\"en\"],\"version\":1," + "" +
		  "\"versionKey\":\"1649220212949\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"compatibilityLevel\":5," +
		  "\"name\":\"S1\",\"navigationMode\":\"non-linear\",\"allowBranching\":\"Yes\",\"shuffle\":true,\"status\":\"Live\"}]}")
		response
	}

	def getNewRootExternalPropsResponseBL(): Response = {
		val response = getSuccessfulResponse()
		response.put("hierarchy", "{\"identifier\":\"do_9876\",\"children\":[{\"parent\":\"do_9876\"," +
		  "\"code\":\"1911de43-48aa-4533-b93e-2e342e9f6ec7\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\"," +
		  "\"channel\":\"{{channel_id}}\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.questionset\"," +
		  "\"showHints\":\"No\",\"createdOn\":\"2022-04-06T14:10:31.187+0530\",\"objectType\":\"QuestionSet\"," +
		  "\"primaryCategory\":\"Observation\",\"children\":[{\"parent\":\"do_3333\",\"code\":\"Q1\"," + "\"channel\":\"{{channel_id}}\","
		  + "\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\"," +
		  "\"createdOn\":\"2022-04-06T10:13:32.859+0530\"," + "\"objectType\":\"Question\",\"primaryCategory\":\"Slider\"," +
		  "\"contentDisposition\":\"inline\"," + "\"lastUpdatedOn\":\"2022-04-06T10:13:32.911+0530\",\"contentEncoding\":\"gzip\"," +
		  "\"showSolutions\":\"No\"," + "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_5555\"," +
		  "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.859+0530\"," + "\"visibility\":\"Default\",\"showTimer\":\"No\",\"index\":1," +
		  "\"languageCode\":[\"en\"],\"version\":1," + "\"versionKey\":\"1649220212911\",\"showFeedback\":\"No\",\"license\":\"CC BY " +
		  "4.0\"," + "\"depth\":2,\"compatibilityLevel\":4," + "\"name\":\"Q1\",\"status\":\"Live\"},{\"parent\":\"do_3333\"," +
		  "\"code\":\"Q2\",\"channel\":\"{{channel_id}}\"," + "\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird" +
		  ".question\"," + "\"createdOn\":\"2022-04-06T10:13:32.896+0530\"," + "\"objectType\":\"Question\"," +
		  "\"primaryCategory\":\"Slider\"," + "\"contentDisposition\":\"inline\"," + "\"lastUpdatedOn\":\"2022-04-06T10:13:32.954+0530\"," +
		  "\"contentEncoding\":\"gzip\"," + "\"showSolutions\":\"No\"," + "\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_7777\"," +
		  "\"lastStatusChangedOn\":\"2022-04-06T10:13:32.896+0530\"," + "\"visibility\":\"Default\",\"showTimer\":\"No\",\"index\":2," +
		  "\"languageCode\":[\"en\"],\"version\":1," + "\"versionKey\":\"1649220212954\",\"showFeedback\":\"No\",\"license\":\"CC BY " +
		  "4.0\"," + "\"depth\":2,\"compatibilityLevel\":4," + "\"name\":\"Q2\",\"status\":\"Live\"}],\"contentDisposition\":\"inline\","
		  + "\"lastUpdatedOn\":\"2022-04-06T14:10:31.185+0530\"," + "\"contentEncoding\":\"gzip\",\"generateDIALCodes\":\"No\"," +
		  "\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\"," + "\"identifier\":\"do_3333\"," +
		  "\"lastStatusChangedOn\":\"2022-04-06T14:10:31.187+0530\",\"requiresSubmit\":\"No\"," + "\"visibility\":\"Parent\"," +
		  "\"showTimer\":\"No\",\"index\":1,\"setType\":\"materialised\",\"languageCode\":[\"en\"],\"version\":1," + "" + "" +
		  "\"versionKey\":\"1649234431187\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"name\":\"S1\"," +
		  "\"navigationMode\":\"non-linear\",\"allowBranching\":\"Yes\",\"shuffle\":true,\"status\":\"Draft\"}]}")
		response
	}

	def getResourceNotFoundResponse(): Response = {
		val response = new Response
		response.setVer("3.0")
		val responseParams = new ResponseParams
		responseParams.setStatus("failed")
		response.setParams(responseParams)
		response.setResponseCode(ResponseCode.RESOURCE_NOT_FOUND)
		response
	}
}
