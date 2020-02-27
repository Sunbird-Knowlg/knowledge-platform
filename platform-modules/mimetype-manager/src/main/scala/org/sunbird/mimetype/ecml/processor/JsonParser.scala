package org.sunbird.mimetype.ecml.processor

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException
import play.api.libs.json._

import scala.collection.mutable.ListBuffer

object JsonParser {
    val nonPluginElements: List[String] = List("manifest", "controller", "media", "events", "event", "__cdata", "__text")

    def parse(jsonString: String): Plugin = {
        val jsValue:JsValue =  play.api.libs.json.Json.parse(jsonString)
        processDocument(jsValue)
    }

    def processDocument(json: JsValue): Plugin = {

        if((json \ "theme").isDefined){
            val root = (json \ "theme").get
            Plugin(getId(root), getData(root, "theme"), getInnerText(root), getCdata(root), getChildrenPlugin(root), getManifest(root, true), getControllers(root), getEvents(root))
        } else classOf[Plugin].newInstance()
    }


    def getDatafromJsonObject(jsonObject: JsValue, elementName: String): String = {
        if(null != jsonObject && (jsonObject \ elementName).isDefined){
            (jsonObject \ elementName).get.as[String]
        }else ""
    }


    def getId(jsonObject: JsValue): String = getDatafromJsonObject(jsonObject, "id")

    def getData(jsonObject: JsValue, elementName: String): Map[String, AnyRef] = {
        if(null != jsonObject && StringUtils.isNotBlank(elementName)){
            implicit val mapRead = Json.reads[Map[String, AnyRef]]
            val objectMap : Map[String, AnyRef] =  jsonObject.as[Map[String, AnyRef]]
            var result = objectMap.filter(p => !p._1.equalsIgnoreCase("__cdata") && !p._1.equalsIgnoreCase("__text"))
            result += ("cwp_element_name" -> elementName)
            result
        } else Map[String, AnyRef]()
    }

    def getInnerText(jsonObject: JsValue): String = getDatafromJsonObject(jsonObject, "__text")

    def getCdata(jsonObject: JsValue): String = getDatafromJsonObject(jsonObject, "__cdata")

    def getChildrenPlugin(jsonObject: JsValue): List[Plugin] = {
        var childPluginList: ListBuffer[Plugin] = ListBuffer()
        implicit val jsObjRead = Json.reads[JsObject]
        val filteredObject = jsonObject.as[JsObject].fieldSet.filter(entry => !entry._2.isInstanceOf[JsUndefined])
        childPluginList ++= filteredObject.filter(entry=> entry._2.isInstanceOf[JsArray] && !nonPluginElements.contains(entry._1)).map(entry => {
            implicit val mapRead = Json.reads[List[JsValue]]
            val objectList:List[JsValue] = entry._2.as[List[JsValue]]
            objectList.map(obj => Plugin(getId(obj), getData(obj, entry._1), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj), getManifest(obj, false), getControllers(obj), getEvents(obj)))
        }).toList.flatten
        childPluginList ++= filteredObject.filter(entry => entry._2.isInstanceOf[JsObject] && !nonPluginElements.contains(entry._2)).map(entry => {
            val obj = entry._2.asInstanceOf[JsObject]
            Plugin(getId(obj), getData(obj, entry._1), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj), getManifest(obj, false), getControllers(obj), getEvents(obj))
        }).toList
        childPluginList.toList
    }

    def getManifest(jsonObject: JsValue, validateMedia: Boolean): Manifest = {
        if(!jsonObject.isInstanceOf[JsUndefined] && (jsonObject \ "manifest").isDefined && (jsonObject \ "manifest").get.isInstanceOf[JsArray]) throw new ClientException("EXPECTED_JSON_OBJECT", "Error! JSON Object is Expected for the Element. manifest")
        else if((jsonObject \ "manifest").get.isInstanceOf[JsObject] && (jsonObject \ "manifest" \ "media").isDefined){
            val manifestObject = (jsonObject \ "manifest").get
            Manifest(getId(manifestObject), getData(manifestObject, "manifest"), getInnerText(manifestObject), getCdata(manifestObject), getMedias((manifestObject \ "media").get, validateMedia))
        }else classOf[Manifest].newInstance()
    }

    def getControllers(jsonObject: JsValue): List[Controller] = {
        if(!jsonObject.isInstanceOf[JsUndefined] && (jsonObject \ "controller").isDefined && (jsonObject \ "controller").get.isInstanceOf[JsArray]){
            implicit val listRead = Json.reads[List[JsValue]]
            val controllerList:List[JsValue] = jsonObject.as[List[JsValue]]
            controllerList.map(obj =>{
                validateController(obj)
                Controller(getId(obj), getData(obj, "controller"), getInnerText(obj), getCdata(obj))
            })
        } else if(!jsonObject.isInstanceOf[JsUndefined] && (jsonObject \ "controller").isDefined && (jsonObject \ "controller").get.isInstanceOf[JsObject]) {
            val obj = (jsonObject \ "controller").get
            validateController(obj)
            List(Controller(getId(obj), getData(obj, "controller"), getInnerText(obj), getCdata(obj)))
        }else List()
    }

    def validateController(obj: JsValue) = {
        val id = (obj \ "id").get
        val `type` = (obj \ "type").get

        if(null == id || StringUtils.isBlank(id.toString))
            throw new ClientException("INVALID_CONTROLLER", "Error! Invalid Controller ('id' is required.)")
        if(null == `type` || StringUtils.isBlank(`type`.toString))
            throw new ClientException("INVALID_CONTROLLER", "Error! Invalid Controller ('type' is required.)")
        if(!"items".equalsIgnoreCase(`type`.as[String]) && !"data".equalsIgnoreCase(`type`.as[String]))
            throw new ClientException("INVALID_CONTROLLER", "Error! Invalid Controller ('type' should be either 'items' or 'data')")
    }
    
    def getEventsfromObject(jsonObject: JsValue): List[Event] = {
        if(!jsonObject.isInstanceOf[JsUndefined] && jsonObject.isInstanceOf[JsArray]){
            implicit val listRead = Json.reads[List[JsValue]]
            val jsonList:List[JsValue] = jsonObject.as[List[JsValue]]
            jsonList.map(obj => Event(getId(obj), getData(obj, "event"), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj)))
        }else if(!jsonObject.isInstanceOf[JsUndefined] && jsonObject.isInstanceOf[JsObject]) {
            List(Event(getId(jsonObject), getData(jsonObject, "event"), getInnerText(jsonObject), getCdata(jsonObject), getChildrenPlugin(jsonObject)))
        }else List()
    }

    def getEvents(jsonObject: JsValue): List[Event] = {
        var eventList: ListBuffer[Event] = ListBuffer()
        if(!jsonObject.isInstanceOf[JsUndefined] && (jsonObject \ "events").isDefined) {
            val value = (jsonObject \ "events").get
            if(value.isInstanceOf[JsArray]){
                implicit val listRead = Json.reads[List[JsValue]]
                val jsonList:List[JsValue] = value.as[List[JsValue]]
                eventList ++= jsonList.map(obj => Event(getId(obj), getData(obj, "event"), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj)))
            } else if(value.isInstanceOf[JsObject]){
                eventList ++= getEventsfromObject(value)
            }
        } else if((jsonObject \ "event").isDefined) {
            eventList ++= getEventsfromObject((jsonObject \ "event").get)
        }
        eventList.toList
    }

    def getMedias(manifestObject: JsValue, validateMedia: Boolean): List[Media] = {
        if(!manifestObject.isInstanceOf[JsUndefined] && manifestObject.isInstanceOf[JsArray]) {
            implicit val listRead = Json.reads[List[JsValue]]
            val jsonList:List[JsValue] = manifestObject.as[List[JsValue]]
            jsonList.map(json => getMedia(json, validateMedia))
        } else if(!manifestObject.isInstanceOf[JsUndefined] && manifestObject.isInstanceOf[JsObject]) {
            List(getMedia(manifestObject, validateMedia))
        } else List()
    }

    def getMedia(mediaJson: JsValue, validateMedia: Boolean): Media = {
        if(null != mediaJson) {
            val id = getDatafromJsonObject(mediaJson, "id")
            val src = getDatafromJsonObject(mediaJson, "src")
            val `type` = getDatafromJsonObject(mediaJson, "type")
            if(StringUtils.isBlank(id))
                throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('id' is required.)")
            if(!(StringUtils.isNotBlank(`type`) &&(`type`.equalsIgnoreCase("js") || `type`.equalsIgnoreCase("css"))))
                throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('type' is required.)")
            if(StringUtils.isBlank(src))
                throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('src' is required.)")
            Media(id, getData(mediaJson, "media"), getInnerText(mediaJson), getCdata(mediaJson), src, `type`, getChildrenPlugin(mediaJson))
        } else classOf[Media].newInstance()
    }
    
}
