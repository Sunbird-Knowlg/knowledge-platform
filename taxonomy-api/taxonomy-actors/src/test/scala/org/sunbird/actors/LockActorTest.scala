package org.sunbird.actors

import java.util
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.{Request, Response, ResponseParams}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node
import org.sunbird.utils.Constants
import org.json.JSONObject
import org.sunbird.common.Platform

import java.sql.Timestamp
import java.util.Date
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
class LockActorTest extends BaseSpec with MockFactory {
  private val defaultLockExpiryTime = if (Platform.config.hasPath("defaultLockExpiryTime")) Platform.config.getInt("defaultLockExpiryTime")
  else 3600

  it should "throw exception if operation is not sent in request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getLockRequest()
    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Invalid operation provided in request to process: null")
  }

  it should "return success response for create lock if found in cassandra" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.CHANNEL, "sunbird")
        put(Constants.VERSION_KEY, "version123")

      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()

    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.RESOURCE_INFO, "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_123\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    request.put(Constants.CREATOR_INFO, "{\"name\":\"creatorName\",\"id\":\"createdBy123\"}")
    request.put(Constants.CREATED_BY, "user123")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.CREATE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("lockKey").equals("lock_123"))
  }

  it should "create a new lock If not found in cassandra" in{
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.CHANNEL, "sunbird")
        put(Constants.VERSION_KEY, "version123")

      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()
    (graphDB.saveExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(getSuccessfulResponse())).anyNumberOfTimes
    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.RESOURCE_INFO, "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_123\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    request.put(Constants.CREATOR_INFO, "{\"name\":\"creatorName\",\"id\":\"createdBy123\"}")
    request.put(Constants.CREATED_BY, "user123")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.CREATE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception for create lock if it is already locked by other user" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.CHANNEL, "sunbird")
        put(Constants.VERSION_KEY, "version123")

      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()

    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.RESOURCE_INFO, "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_123\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    request.put(Constants.CREATOR_INFO, "{\"name\":\"creatorName\",\"id\":\"createdBy123\"}")
    request.put(Constants.CREATED_BY, "user123")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "invalid")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.CREATE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if X_AUTHENTICATED_USER_ID is not sent in request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getLockRequest()
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.CREATE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert(response.getParams.getErrmsg == "You are not authorized to lock this resource")
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if X-device-Id is not sent in create lock request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getLockRequest()
    request.setOperation(Constants.CREATE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert(response.getParams.getErrmsg == "X-device-Id is missing in headers")
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception for create lock if it is self locked" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.CHANNEL, "sunbird")
        put(Constants.VERSION_KEY, "version123")

      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()

    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Framework")
    request.put(Constants.RESOURCE_INFO, "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_123\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    request.put(Constants.CREATOR_INFO, "{\"name\":\"creatorName\",\"id\":\"createdBy123\"}")
    request.put(Constants.CREATED_BY, "user123")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.CREATE_LOCK)
    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Error due to self lock , Resource already locked by user ")

  }

  it should "throw exception if there was error saving in cassandra" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.CHANNEL, "sunbird")
        put(Constants.VERSION_KEY, "version123")

      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()
    (graphDB.saveExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes
    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.RESOURCE_INFO, "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_123\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    request.put(Constants.CREATOR_INFO, "{\"name\":\"creatorName\",\"id\":\"createdBy123\"}")
    request.put(Constants.CREATED_BY, "user123")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.CREATE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Error while saving external props to Cassandra")
  }

  it should "return success response for retire a lock" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.X_DEVICE_ID, "device123")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()
    (graphDB.deleteExternalProps _).expects(*).returns(Future(new Response()))

    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.RETIRE_LOCK)
    val response = callActor(request, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if error in deleting from cassandra in  retire a lock" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.X_DEVICE_ID, "device123")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()
    (graphDB.deleteExternalProps _).expects(*).returns(Future(errorResponse()))

    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.RETIRE_LOCK)
    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Error while deleting external props from Cassandra")
  }

  it should "throw exception if userId and createdBy is not equal in  retire lock" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123");
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "createdBy123")
        put(Constants.X_DEVICE_ID, "device123")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()

    val request = getLockRequest()
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put(Constants.RESOURCE_TYPE, "Content")
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user11")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.setOperation(Constants.RETIRE_LOCK)
    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Unauthorized to retire lock")
  }

  it should "throw exception if X-device-Id is not sent in retire lock request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getLockRequest()
    request.setOperation(Constants.RETIRE_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert(response.getParams.getErrmsg == "X-device-Id is missing in headers")
    assert("failed".equals(response.getParams.getStatus))
  }


  it should "return success response for refresh a lock" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123")
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "user123")
        put(Constants.X_DEVICE_ID, "device123")
        put("lockKey", "lock_123")
      }
    })

    val request = getLockRequest()
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put("resourceType", "Content")
    request.put("lockId", "lock_123")
    request.setOperation(Constants.REFRESH_LOCK)

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()
    (graphDB.updateExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(getSuccessfulResponse())).anyNumberOfTimes()

    val response = callActor(request, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if X-device-Id is not sent in refresh lock request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getLockRequest()
    request.setOperation(Constants.REFRESH_LOCK)

    val response = callActor(request, Props(new LockActor()))
    assert(response.getParams.getErrmsg == "X-device-Id is missing in headers")
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if lockKey and request lockId is not same in 'refresh lock' " in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123")
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "user123")
        put(Constants.X_DEVICE_ID, "device123")
        put("lockKey", "lock_123")
      }
    })

    val request = getLockRequest()
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put("resourceType", "Content")
    request.put("lockId", "lock_11")
    request.setOperation(Constants.REFRESH_LOCK)

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()
    (graphDB.updateExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(getSuccessfulResponse())).anyNumberOfTimes()

    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Lock key and request lock key does not match")
  }

  it should "create a new lock if a lock is not found while refreshing" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123")
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "user123")
        put(Constants.X_DEVICE_ID, "device123")
        put("lockKey", "lock_123")
      }
    })

    val request = getLockRequest()
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put("resourceType", "Content")
    request.put("lockId", "lock_123")
    request.setOperation(Constants.REFRESH_LOCK)

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()
    (graphDB.saveExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(getSuccessfulResponse())).anyNumberOfTimes
    val response = callActor(request, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if error occurs while saving in cassandra while refresh lock operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123")
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "user123")
        put(Constants.X_DEVICE_ID, "device123")
        put("lockKey", "lock_123")
      }
    })

    val request = getLockRequest()
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put("resourceType", "Content")
    request.put("lockId", "lock_123")
    request.setOperation(Constants.REFRESH_LOCK)

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()
    (graphDB.saveExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes
    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Error while saving external props to Cassandra")
  }

  it should "throw exception if userId not equal to createdBy while refresh lock operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123")
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "user123")
        put(Constants.X_DEVICE_ID, "device123")
        put("lockKey", "lock_123")
      }
    })

    val request = getLockRequest()
    request.put(Constants.X_AUTHENTICATED_USER_ID, "invalid")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put("resourceType", "Content")
    request.put("lockId", "lock_123")
    request.setOperation(Constants.REFRESH_LOCK)

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getSuccessfulResponse())).anyNumberOfTimes()
    (graphDB.saveExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(getSuccessfulResponse())).anyNumberOfTimes
    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Unauthorized to refresh this lock")
  }

  it should "throw exception if error occurs while updating in cassandra for refresh a lock" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val node = new Node("domain", "DATA_NODE", "Content")
    node.setIdentifier("do_123")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put(Constants.RESOURCE_ID, "do_123")
        put(Constants.RESOURCE_TYPE, "Content")
        put(Constants.CREATED_BY, "user123")
        put(Constants.X_DEVICE_ID, "device123")
        put("lockKey", "lock_123")
      }
    })

    val request = getLockRequest()
    request.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    request.put(Constants.X_DEVICE_ID, "device123")
    request.put(Constants.RESOURCE_ID, "do_123")
    request.put("resourceType", "Content")
    request.put("lockId", "lock_123")
    request.setOperation(Constants.REFRESH_LOCK)

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponse())).anyNumberOfTimes()
    (graphDB.updateExternalPropsWithTtl(_: Request, _: Int)).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()

    val response = callActor(request, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "Error while updating external props to Cassandra")
  }


  it should "return success response for list locks" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponseForList())).anyNumberOfTimes()

    val lockListRequest = new Request()
    val filters = new util.HashMap[String, AnyRef]()
    filters.put("resourceId", util.Arrays.asList("do_1231"))
    lockListRequest.put("filters", filters)
    lockListRequest.setOperation(Constants.LIST_LOCK)
    lockListRequest.setContext(new util.HashMap[String, AnyRef]())
    lockListRequest.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    lockListRequest.put(Constants.X_DEVICE_ID, "device123")

    val response = callActor(lockListRequest, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return success response for list lock with resourceId as String" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(cassandraResponseForList())).anyNumberOfTimes()

    val lockListRequest = new Request()
    val filters = new util.HashMap[String, AnyRef]()
    filters.put("resourceId", "do_1231")
    lockListRequest.put("filters", filters)
    lockListRequest.setOperation(Constants.LIST_LOCK)
    lockListRequest.setContext(new util.HashMap[String, AnyRef]())
    lockListRequest.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    lockListRequest.put(Constants.X_DEVICE_ID, "device123")

    val response = callActor(lockListRequest, Props(new LockActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if error occurs while reading from cassandra in list lock" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()

    val lockListRequest = new Request()
    val filters = new util.HashMap[String, AnyRef]()
    filters.put("resourceId", util.Arrays.asList("do_1231"))
    lockListRequest.put("filters", filters)
    lockListRequest.setOperation(Constants.LIST_LOCK)
    lockListRequest.setContext(new util.HashMap[String, AnyRef]())
    lockListRequest.put(Constants.X_AUTHENTICATED_USER_ID, "user123")
    lockListRequest.put(Constants.X_DEVICE_ID, "device123")

    val response = callActor(lockListRequest, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "error while fetching lock list data from db")
  }

  it should "throw exception if x-device-id is not passed in the request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(errorResponse())).anyNumberOfTimes()

    val lockListRequest = new Request()
    val filters = new util.HashMap[String, AnyRef]()
    filters.put("resourceId", util.Arrays.asList("do_1231"))
    lockListRequest.put("filters", filters)
    lockListRequest.setOperation(Constants.LIST_LOCK)
    lockListRequest.setContext(new util.HashMap[String, AnyRef]())
    lockListRequest.put(Constants.X_AUTHENTICATED_USER_ID, "user123")

    val response = callActor(lockListRequest, Props(new LockActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(response.getParams.getErrmsg == "X-device-Id is missing in headers")
  }

  def getSuccessfulResponse(): Response = {
    val response = new Response
    val lockInfo = new JSONObject
    lockInfo.put("lockKey", "lock_123")
    lockInfo.put("expiresAt", "2023-08-09 11:54:41.527000+0000")
    lockInfo.put("expiresIn", 60)
    response.setVer("3.0")
    val responseParams = new ResponseParams
    responseParams.setStatus("successful")
    response.setParams(responseParams)
    response.setResponseCode(ResponseCode.OK)
    response
  }
  def cassandraResponse(): Response = {
    val newDateObj = createExpiryTime()
    val response = new Response
    response.put("resourceid", "do_12423")
    response.put("createdby", "user123")
    response.put("createdon", new Timestamp(new Date().getTime))
    response.put("creatorinfo", "{\"name\":\"N11\",\"id\":\"5a587cc1-e018-4859-a0a8-e842650b9d64\"}")
    response.put("deviceid", "device123")
    response.put("expiresat", newDateObj)
    response.put("lockid", "lock_123")
    response.put("resourceinfo", "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_11384879291617280011\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    response.put("resourcetype", "Content")
  }

  def cassandraResponseForList(): Response = {
    val newDateObj = createExpiryTime()
    val lockDataMap = new util.HashMap[String, AnyRef]()
    lockDataMap.put("resourceid", "do_12423")
    lockDataMap.put("createdby", "user123")
    lockDataMap.put("createdon", new Timestamp(new Date().getTime))
    lockDataMap.put("creatorinfo", "{\"name\":\"N11\",\"id\":\"5a587cc1-e018-4859-a0a8-e842650b9d64\"}")
    lockDataMap.put("deviceid", "device123")
    lockDataMap.put("expiresat", newDateObj)
    lockDataMap.put("lockid", "lock_123")
    lockDataMap.put("resourceinfo", "{\"contentType\":\"Content\",\"framework\":\"NCF\",\"identifier\":\"do_11384879291617280011\",\"mimeType\":\"application/vnd.ekstep.h5p-archive\"}")
    lockDataMap.put("resourcetype", "Content")

    val response = new Response
    response.put("lock_123", lockDataMap)

    response
  }

  def createExpiryTime(): Date = {
    val expiryTimeMillis = System.currentTimeMillis() + (defaultLockExpiryTime * 1000)
    new Date(expiryTimeMillis)
  }
  def errorResponse(): Response = {
    val response = new Response
    response.setResponseCode(ResponseCode.RESOURCE_NOT_FOUND)
    val responseParams = new ResponseParams
    responseParams.setStatus("failed")
    response.setParams(responseParams)

    response
  }


  private def getLockRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "Lock")
        put("schemaName", "lock")
      }
    })
    request.setObjectType("Lock")
    request
  }
}
