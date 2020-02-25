package org.sunbird.mimetype.ecml.processor

import com.google.gson.{Gson, JsonElement, JsonObject}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object JsonParser {


    val nonPluginElements: List[String] = List("manifest", "controller", "media", "events", "event", "__cdata", "__text")

    def parse(jsonString: String): Plugin = {
        val json: JsonObject = new Gson().toJsonTree(jsonString).getAsJsonObject
        processDocument(json)
    }

    def processDocument(json: JsonObject): Plugin = {
        if(null != json && json.has("theme")){
            val root = json.getAsJsonObject("theme")
            Plugin(getId(root), getData(root, "theme"), getInnerText(root), getCdata(root), getChildrenPlugin(root), getManifest(root, true), getControllers(root), getEvents(root))
        } else classOf[Plugin].newInstance()
    }


    def getDatafromJsonObject(jsonObject: JsonObject, elementName: String): String = {
        if(null != jsonObject && jsonObject.has(elementName)){
            jsonObject.get(elementName).getAsString
        }else ""
    }


    def getId(jsonObject: JsonObject): String = getDatafromJsonObject(jsonObject, "id")

    def getData(jsonObject: JsonObject, elementName: String): Map[String, AnyRef] = {
        if(null != jsonObject && StringUtils.isNotBlank(elementName)){
            val objectMap : Map[String, AnyRef] = new Gson().fromJson(jsonObject, Map.getClass)
            var result = objectMap.filter(p => !p._1.equalsIgnoreCase("__cdata") && !p._1.equalsIgnoreCase("__text"))
            result += ("cwp_element_name" -> elementName)
            result
        } else Map[String, AnyRef]()
    }

    def getInnerText(jsonObject: JsonObject): String = getDatafromJsonObject(jsonObject, "__text")

    def getCdata(jsonObject: JsonObject): String = getDatafromJsonObject(jsonObject, "__cdata")

    def getChildrenPlugin(jsonObject: JsonObject): List[Plugin] = {
        var childPluginList: ListBuffer[Plugin] = ListBuffer()
        val filteredObject = jsonObject.entrySet().filter(entry => null != entry.getValue)
        childPluginList ++= filteredObject.filter(entry=> entry.getValue.isJsonArray && !nonPluginElements.contains(entry.getKey)).map(entry => {
            val objectList:List[JsonObject] = new Gson().fromJson(entry.getValue, List[JsonObject].getClass)
            objectList.map(obj => Plugin(getId(obj), getData(obj, entry.getKey), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj), getManifest(obj, false), getControllers(obj), getEvents(obj)))
        }).toList.flatten
        childPluginList ++= filteredObject.filter(entry => entry.getValue.isJsonObject && !nonPluginElements.contains(entry.getKey)).map(entry => {
            val obj = entry.getValue.getAsJsonObject
            Plugin(getId(obj), getData(obj, entry.getKey), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj), getManifest(obj, false), getControllers(obj), getEvents(obj))
        }).toList
        childPluginList.toList
    }

    def getManifest(jsonObject: JsonObject, validateMedia: Boolean): Manifest = {
        if(null != jsonObject && jsonObject.has("manifest") && jsonObject.get("manifest").isJsonArray) throw new ClientException("EXPECTED_JSON_OBJECT", "Error! JSON Object is Expected for the Element. manifest")
        else if(jsonObject.get("manifest").isJsonObject && jsonObject.get("manifest").getAsJsonObject.has("media")){
            val manifestObject = jsonObject.get("manifest").getAsJsonObject
            Manifest(getId(manifestObject), getData(manifestObject, "manifest"), getInnerText(manifestObject), getCdata(manifestObject), getMedias(manifestObject.get("media"), validateMedia))
        }else classOf[Manifest].newInstance()
    }

    def getControllers(jsonObject: JsonObject): List[Controller] = {
        if(null != jsonObject && jsonObject.has("controller") && jsonObject.get("controller").isJsonArray){
            val controllerList:List[JsonObject] = new Gson().fromJson(jsonObject.get("controller"), List[JsonObject].getClass)
            controllerList.map(obj =>{
                validateController(obj)
                Controller(getId(obj), getData(obj, "controller"), getInnerText(obj), getCdata(obj))
            })
        } else if(null != jsonObject && jsonObject.has("controller") && jsonObject.get("controller").isJsonObject) {
            val obj = jsonObject.get("controller").getAsJsonObject
            validateController(obj)
            List(Controller(getId(obj), getData(obj, "controller"), getInnerText(obj), getCdata(obj)))
        }else List()
    }

    def validateController(obj: JsonObject) = {
        val id = obj.get("id")
        val `type` = obj.get("type")

        if(null == id || StringUtils.isBlank(id.toString))
            throw new ClientException("INVALID_CONTROLLER", "Error! Invalid Controller ('id' is required.)")
        if(null == `type` || StringUtils.isBlank(`type`.toString))
            throw new ClientException("INVALID_CONTROLLER", "Error! Invalid Controller ('type' is required.)")
        if(!"items".equalsIgnoreCase(`type`.getAsString) && !"data".equalsIgnoreCase(`type`.getAsString))
            throw new ClientException("INVALID_CONTROLLER", "Error! Invalid Controller ('type' should be either 'items' or 'data')")
    }
    
    def getEventsfromObject(jsonObject: JsonObject): List[Event] = {
        if(null != jsonObject && jsonObject.isJsonArray){
            val jsonList:List[JsonObject] = new Gson().fromJson(jsonObject, List.getClass)
            jsonList.map(obj => Event(getId(obj), getData(obj, "event"), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj)))
        }else if(null != jsonObject && jsonObject.isJsonObject) {
            List(Event(getId(jsonObject), getData(jsonObject, "event"), getInnerText(jsonObject), getCdata(jsonObject), getChildrenPlugin(jsonObject)))
        }else List()
    }

    def getEvents(jsonObject: JsonObject): List[Event] = {
        var eventList: ListBuffer[Event] = ListBuffer()
        if(null != jsonObject && jsonObject.has("events")) {
            val value = jsonObject.get("events")
            if(value.isJsonArray){
                val jsonList:List[JsonObject] = new Gson().fromJson(value, List.getClass)
                eventList ++= jsonList.map(obj => Event(getId(obj), getData(obj, "event"), getInnerText(obj), getCdata(obj), getChildrenPlugin(obj)))
            } else if(value.isJsonObject){
                eventList ++= getEventsfromObject(value.getAsJsonObject)
            }
        } else if(jsonObject.has("event")) {
            eventList ++= getEventsfromObject(jsonObject.get("event").getAsJsonObject)
        }
        eventList.toList
    }

    def getMedias(manifestObject: JsonElement, validateMedia: Boolean): List[Media] = {
        if(null != manifestObject && manifestObject.isJsonArray) {
           val jsonList:List[JsonObject] = new Gson().fromJson(manifestObject, List.getClass)
            jsonList.map(json => getMedia(json, validateMedia))
        } else if(null != manifestObject && manifestObject.isJsonObject) {
            List(getMedia(manifestObject.getAsJsonObject, validateMedia))
        } else List()
    }

    def getMedia(mediaJson: JsonObject, validateMedia: Boolean): Media = {
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
