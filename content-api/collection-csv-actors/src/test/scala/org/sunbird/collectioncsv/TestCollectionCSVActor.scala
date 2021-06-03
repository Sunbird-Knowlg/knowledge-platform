package org.sunbird.collectioncsv

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import org.sunbird.common.{HttpUtil, JsonUtils}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import java.io.File
import java.util
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration


class TestCollectionCSVActor extends FlatSpec with Matchers with MockFactory {

    implicit val ec: ExecutionContext = ExecutionContext.global
    val system: ActorSystem = ActorSystem.create("system")
    val httpUtil: HttpUtil = mock[HttpUtil]
    implicit val ss: StorageService = mock[StorageService]
    implicit val oec: OntologyEngineContext  = mock[OntologyEngineContext]
    val graphDB: GraphService = mock[GraphService]
    val currentDirectory: String = new java.io.File(".").getCanonicalPath 

    "CollectionCSVActor" should "return failed response for 'unknown' operation" in {
        testUnknownOperation( Props(new CollectionCSVActor()), getCollectionRequest())
    }

    "get TOC URL" should "return client error on giving content Id with no children" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

        val node = createNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()

        val collectionID = "do_113293355858984960134"

        val response = callActorDownload(collectionID)
        assert(response != null)
        assert(response.getResponseCode === ResponseCode.CLIENT_ERROR)
    }

    "get TOC URL" should "return success response on giving valid content Id" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        (ss.getUri(_:String)).expects(*).returns("").once()
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array("/content/textbook/toc/do_11329104609801011211_testcsvuploaddemo_1622453851546.csv", "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/textbook/toc/do_11329104609801011211_testcsvuploaddemo_1622453851546.csv")).once()

        val response = callActorDownload(collectionID)
        val result = response.getResult
        assert(response != null)
        assert(response.getResponseCode == ResponseCode.OK)
        assert(result.get("collection") != null)
    }

    "uploadTOC" should "return client error on input of blank csv" in {
        val collectionID = "do_1132828073514926081518"
        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "Blank.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equals("INVALID_CSV_FILE"))
    }

    "uploadTOC" should "return client error on input of create csv with collection Id already having children" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "CreateTOC.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("COLLECTION_CHILDREN_EXISTS"))
    }

    "uploadTOC" should "return client error on input of create csv with missing column and additional column" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidHeadersFound.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("INVALID_HEADERS_FOUND"))
    }

    "uploadTOC" should "return client error on input of create csv with missing column" in {
       (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "RequiredHeaderMissing.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("REQUIRED_HEADER_MISSING") || response.getParams.getErr.equalsIgnoreCase("INVALID_HEADER_SEQUENCE"))
    }

    "uploadTOC" should "return client error on input of create csv with additional column" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "AdditionalHeaderFound.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("ADDITIONAL_HEADER_FOUND"))
    }

    "uploadTOC" should "return client error on input of create csv with no records" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "NoRecords.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("BLANK_CSV_DATA"))
    }

    "uploadTOC" should "return client error on input of create csv with records exceeding maximum allowed rows" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "CSVMaxRows.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_ROWS_EXCEEDS"))
    }

    "uploadTOC" should "return client error on input of create csv with record having missing data in mandatory columns" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "MandatoryColMissingData.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("REQUIRED_FIELD_MISSING"))
    }

    "uploadTOC" should "return client error on input of create csv with duplicate record rows" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_113293355858984960134"
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "DuplicateRecords.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("DUPLICATE_ROWS"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid QRCodeRequired and QRCode combination" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "QRCodeYesNo.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("ERROR_QR_CODE_ENTRY"))
    }

    "uploadTOC" should "return client error on input of update csv with duplicate QRCode entry" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "DuplicateQRCode.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("DUPLICATE_QR_CODE_ENTRY"))
    }

    "uploadTOC" should "return client error on input of update csv with linked content data missing" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "LinkedContentsDataMissing.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("LINKED_CONTENTS_DATA_MISSING"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid collection name" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidCollectionName.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_INVALID_COLLECTION_NAME"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid child nodes" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidNodeIds.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_INVALID_COLLECTION_NODE_ID"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid QR Codes" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse()).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidQRCodes.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_INVALID_DIAL_CODES"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid Mapped Topics" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.get(_: String, _: String, _:java.util.Map[String, String])).expects(*, *, *).returns(getFrameworkResponse()).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidMappedTopics.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_INVALID_MAPPED_TOPICS"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid linked contents" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(searchLinkedContentsResponse()).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidLinkedContents.csv")

        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_INVALID_LINKED_CONTENTS"))
    }

    "uploadTOC" should "return client error on input of update csv with invalid contentType of linked contents" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(linkedContentsInvalidContentTypeResponse()).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "InvalidLinkedContentContentType.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr.equalsIgnoreCase("CSV_INVALID_LINKED_CONTENTS_CONTENT_TYPE"))
    }

    "uploadTOC" should "return success response on input of valid update TOC csv" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val collectionID = "do_1132828073514926081518"
        val node = updateNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes(node))).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()

        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects("https://dev.sunbirded.org/api" + "/dialcode/v1/list", *, *).returns(getDIALSearchResponse()).anyNumberOfTimes()
        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects("https://dev.sunbirded.org/api" + "/content/v1/search", *, *).returns(searchLinkedContentsResponse()).anyNumberOfTimes()
        (oec.httpUtil _).expects().returns(httpUtil)
        (httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects("" + "/collection/v3/dialcode/link" + "/" + collectionID, *, *).returns(linkDIALCodesResponse()).anyNumberOfTimes()

        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "UpdateTOC.csv")
        assert(response != null)
        println("uploadTOC should return success response on input of validate update TOC csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode != ResponseCode.OK)
    }

    "uploadTOC" should "return success response on giving a valid create TOC csv" in {
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = createNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes()
        (graphDB.updateExternalProps(_:Request)).expects(*).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()


        val collectionID = "do_113293355858984960134"
        val response = uploadFileToActor(collectionID, "./content-api/collection-csv-actors/src/test/resources/" + "CreateTOC.csv")
        assert(response != null)
        println("uploadTOC should return client error on input of blank csv --> response.getParams: " + response.getParams)
        assert(response.getResponseCode == ResponseCode.OK)
    }

    def uploadFileToActor(collectionID: String, uploadFilePath: String): Response = {
        val uploadTOCFile = new File(currentDirectory+uploadFilePath)
        val request = getCollectionRequest()
        request.put(CollectionTOCConstants.IDENTIFIER, collectionID)
        request.setOperation(CollectionTOCConstants.COLLECTION_CSV_TOC_UPLOAD)
        request.getRequest.put("file",uploadTOCFile)

        callActorUpload(request)
    }

    def callActorUpload(request: Request)(implicit oec: OntologyEngineContext, ss:StorageService): Response = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(Props(new CollectionCSVActor()))
        actorRef.tell(request, probe.testActor)
        probe.expectMsgType[Response](FiniteDuration.apply(100, TimeUnit.SECONDS))
    }

    def callActorDownload(collectionID: String)(implicit oec: OntologyEngineContext, ss:StorageService): Response = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(Props(new CollectionCSVActor()))
        val request = getCollectionRequest()
        request.put(CollectionTOCConstants.IDENTIFIER, collectionID)
        request.setOperation(CollectionTOCConstants.COLLECTION_CSV_TOC_DOWNLOAD)
        println("TestCollectionCSVActor --> callActorDownload before actor call: ")
        actorRef.tell(request, probe.testActor)
        println("TestCollectionCSVActor --> callActorDownload after actor call: ")
        probe.expectMsgType[Response](FiniteDuration.apply(100, TimeUnit.SECONDS))
    }

    private def getCollectionRequest(): Request = {
        val request = new Request()
        request.setContext(new java.util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Collection")
                put("schemaName", "collection")
            }
        })
        request.setObjectType("Collection")
        request
    }

    def testUnknownOperation(props: Props, request: Request) = {
        implicit val oec: OntologyEngineContext  = mock[OntologyEngineContext]
        request.setOperation("unknown")
        val response = callActor(request, props)
        assert("failed".equals(response.getParams.getStatus))
    }

    def callActor(request: Request, props: Props): Response = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(props)
        actorRef.tell(request, probe.testActor)
        probe.expectMsgType[Response](FiniteDuration.apply(30, TimeUnit.SECONDS))
    }

    private def updateNode(): Node = {
        val childNodes = new java.util.ArrayList[String]()
        childNodes.add("do_1132339274094346241120")
        childNodes.add("do_1132833371215872001720")
        childNodes.add("do_1132833371215134721712")
        childNodes.add("do_1132833371215462401716")
        childNodes.add("do_113223967141863424174")
        childNodes.add("do_1132833371214970881710")
        childNodes.add("do_1132833371215708161718")
        childNodes.add("do_1132372524622561281279")
        childNodes.add("do_1132338069147811841118")
        childNodes.add("do_1132833371215298561714")
        childNodes.add("do_1132833371215953921722")
        childNodes.add("do_11322383952751820816")
        childNodes.add("do_1132216902566133761410")
        childNodes.add("do_1132344630588948481134")

        val collectionID = "do_1132828073514926081518"
        val LATEST_CONTENT_VERSION: Integer = 2
        val depth: Integer = 0

        val node = new Node()
        node.setIdentifier(collectionID)
        node.setNodeType("TextBook")
        node.setObjectType("Collection")
        node.setMetadata(new java.util.HashMap[String, AnyRef]() {
            {
                put("identifier", collectionID)
                put("objectType", "Collection")
                put("name", "TestCSVUpload")
                put("mimeType", "application/vnd.ekstep.content-collection")
                put("contentType","TextBook")
                put("language", "English")
                put("primaryCategory", "Digital Textbook")
                put("versionKey", "1621501113536")
                put("childNodes", childNodes)
                put("channel", "0126825293972439041")
                put("framework", "tn_k-12")
                put("version", LATEST_CONTENT_VERSION)
                put("status","Draft")
                put("depth",depth)
                put("visibility","Default")
            }
        })
        node
    }

    private def createNode(): Node = {
        val LATEST_CONTENT_VERSION: Integer = 2
        val depth: Integer = 0
        val node = new Node()
        val collectionID = "do_113293355858984960134"
        node.setIdentifier(collectionID)
        node.setNodeType("TextBook")
        node.setObjectType("Collection")
        node.setMetadata(new java.util.HashMap[String, AnyRef]() {
            {
                put("identifier", collectionID)
                put("objectType", "Collection")
                put("name", "TestCSVUpload")
                put("mimeType", "application/vnd.ekstep.content-collection")
                put("contentType","TextBook")
                put("primaryCategory", "Digital Textbook")
                put("versionKey","1622724103891")
                put("channel", "0126825293972439041")
                put("framework", "tn_k-12")
                put("version", LATEST_CONTENT_VERSION)
                put("mediaType","content")
                put("generateDIALCodes","No")
                put("resourceType","Book")
                put("code","testing")
                put("status","Draft")
                put("depth",depth)
                put("visibility","Default")

            }
        })
        node
    }

    def getCassandraHierarchy(): Response = {
        val hierarchyString: String = """{"status":"Draft","children":[{"parent":"do_11283193441064550414","code":"2e837725-d663-45da-8ace-9577ab111982", "identifier":"do_11283193463014195215","name":"U1","description":"dfsdf","language":["English"],"contentType":"TextBookUnit","mimeType":"application/vnd.ekstep.content-collection","depth":1,"index":1,"objectType":"Collection","primaryCategory":"Digital Textbook Unit","versionKey":"1566398270281","status":"Draft","visibility":"Parent","framework":"NCF"}],"childNodes":["do_11329114194478694417","do_11329114194471321615","do_113291141945090048113","do_11329114194301747211","do_11329114194489344019","do_11329114194467225613","do_113291141944999936111"],"identifier":"do_11329104609801011211"}"""
        val response = new Response
        response.put("hierarchy", hierarchyString)
    }

    def getEmptyCassandraHierarchy(): Response = {
        val response = new Response
        response.put("hierarchy", "{}")
    }

    def getNodes(node: Node): java.util.List[Node] = {
        val result = new java.util.ArrayList[Node](){{
            add(node)
        }}
        result
    }

    def getDIALSearchResponse():Response = {
        val resString = """{"id": "sunbird.dialcode.search",  "ver": "3.0",  "ts": "2020-04-21T19:39:14ZZ",  "params": {"resmsgid": "1dfcc25b-6c37-49f8-a6c3-7185063e8752",    "msgid": null,    "err": null,    "status": "successful",    "errmsg": null  },  "responseCode": "OK",  "result": {"dialcodes": [{"dialcode_index": 7609876,"identifier": "Q51XXZ","channel": "testr01","batchcode": "testPub0001.20200421T193801","publisher": "testPub0001","generated_on": "2020-04-21T19:38:01.603+0000","status": "Draft","objectType": "DialCode"},{"dialcode_index": 7610113,"identifier": "VZKAFQ","channel": "testr01","batchcode": "testPub0001.20200421T193801","publisher": "testPub0001","generated_on": "2020-04-21T19:38:01.635+0000","status": "Draft","objectType": "DialCode"}]},    "count": 2}"""
        JsonUtils.deserialize(resString, classOf[Response])
    }

    def linkDIALCodesResponse():Response = {
        val resString = """{"id": "sunbird.dialcode.link",  "ver": "3.0",  "ts": "2020-04-21T19:39:14ZZ",  "params": {"resmsgid": "1dfcc25b-6c37-49f8-a6c3-7185063e8752",    "msgid": null,    "err": null,    "status": "successful",    "errmsg": null  },  "responseCode": "OK",  "result": {"dialcodes": []}}"""
        JsonUtils.deserialize(resString, classOf[Response])
    }

    def getFrameworkResponse(): Response = {
        val resString = """{ "id": "api.framework.read", "ver": "1.0", "ts": "2021-06-02T12:50:06.398Z", "params": {  "resmsgid": "0e6b0de0-c3a1-11eb-93eb-61ea877568bd",  "msgid": "0e684ec0-c3a1-11eb-8b68-6b054371f5a3",  "status": "successful",  "err": null,  "errmsg": null }, "responseCode": "OK", "result": {  "framework": {"identifier": "igot_health","code": "igot_health","name": "IGOT-Health","description": "IGOT-Health","categories": [ {  "identifier": "igot_health_topic",  "code": "topic",  "translations": null,  "name": "Concept",  "description": "Concept",  "index": 5,  "status": "Live", "terms": [{ "identifier": "ekstep_ncert_k-12_topic_environmentalstudies_l1con_1", "code": "environmentalstudies_l1Con_1", "translations": null, "name": "Look and say", "description": "Look and say", "index": 1, "category": "topic", "status": "Live"}] }],"type": "K-12","objectType": "Framework"  } }}"""
        JsonUtils.deserialize(resString, classOf[Response])
    }

    def searchLinkedContentsResponse(): Response = {
        val resString = """{ "id": "api.content.search", "ver": "1.0", "ts": "2021-06-02T13:23:16.096Z", "params": {  "resmsgid": "b05ee000-c3a5-11eb-8f0d-5b69b763f5d8",  "msgid": "b05cbd20-c3a5-11eb-a92c-2b2f306434f8",  "status": "successful",  "err": null,  "errmsg": null }, "responseCode": "OK", "result": {  "count": 5,  "content": [{ "identifier": "do_1132339274094346241120", "primaryCategory": "Learning Resource", "name": "Untitled Content", "mimeType": "video/mp4", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_1132344630588948481134", "primaryCategory": "Learning Resource", "name": "Untitled Content", "mimeType": "video/mp4", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_1132338069147811841118", "primaryCategory": "Learning Resource", "name": "Untitled Content", "mimeType": "video/mp4", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_113223967141863424174", "primaryCategory": "Exam Question", "name": "esa", "mimeType": "application/vnd.ekstep.ecml-archive", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_11322383952751820816", "primaryCategory": "Exam Question", "name": "sa:practice", "mimeType": "application/vnd.ekstep.ecml-archive", "contentType": "Resource", "objectType": "Content"}  ],  "facets": [{ "values": [  {"name": "b00bc992ef25f1a9a8d63291e20efc8d","count": 3  },  {"name": "01309282781705830427","count": 2  } ], "name": "channel"},{ "values": [  {"name": "live","count": 5  } ], "name": "status"}  ] }}"""
        JsonUtils.deserialize(resString, classOf[Response])
    }

    def linkedContentsInvalidContentTypeResponse(): Response = {
        val resString = """{ "id": "api.content.search", "ver": "1.0", "ts": "2021-06-02T13:34:42.430Z", "params": {  "resmsgid": "497521e0-c3a7-11eb-8f0d-5b69b763f5d8",  "msgid": "4972ff00-c3a7-11eb-a92c-2b2f306434f8",  "status": "successful",  "err": null,  "errmsg": null }, "responseCode": "OK", "result": {  "count": 6,  "content": [{ "identifier": "do_113281831886274560129", "primaryCategory": "Course", "name": "Test Course", "mimeType": "application/vnd.ekstep.content-collection", "contentType": "Course", "objectType": "Content"},{ "identifier": "do_1132339274094346241120", "primaryCategory": "Learning Resource", "name": "Untitled Content", "mimeType": "video/mp4", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_1132344630588948481134", "primaryCategory": "Learning Resource", "name": "Untitled Content", "mimeType": "video/mp4", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_1132338069147811841118", "primaryCategory": "Learning Resource", "name": "Untitled Content", "mimeType": "video/mp4", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_113223967141863424174", "primaryCategory": "Exam Question", "name": "esa", "mimeType": "application/vnd.ekstep.ecml-archive", "contentType": "Resource", "objectType": "Content"},{ "identifier": "do_11322383952751820816", "primaryCategory": "Exam Question", "name": "sa:practice", "mimeType": "application/vnd.ekstep.ecml-archive", "contentType": "Resource", "objectType": "Content"}  ],  "facets": [{ "values": [  {"name": "b00bc992ef25f1a9a8d63291e20efc8d","count": 3  },  {"name": "01309282781705830427","count": 3  } ], "name": "channel"},{ "values": [  {"name": "live","count": 6  } ], "name": "status"}  ] }}"""
        JsonUtils.deserialize(resString, classOf[Response])
    }

}


