package org.sunbird.mimetype.ecml.processor

import org.apache.commons.lang.{StringEscapeUtils, StringUtils}
import org.sunbird.common.exception.ClientException

import scala.collection.mutable.ListBuffer
import scala.xml._

object XmlParser {

    val nonPluginElements: List[String] = List("manifest", "controller", "media", "events", "event", "__cdata", "__text")
    val START_TAG_OPENING: String = "<"
    val END_TAG_OPENING: String = "</"
    val TAG_CLOSING: String = ">"
    val ATTRIBUTE_KEY_VALUE_SEPARATOR: String = "="
    val BLANK_SPACE: String = " "
    val DOUBLE_QUOTE: String = "\""


    def parse(xml: String): Plugin = {
        val xmlObj = XML.loadString(xml)
        processDocument(xmlObj)
    }

    def processDocument(root: Node): Plugin = {
        if(null != root) {
            Plugin(getId(root), getData(root), "", getCdata(root), getChildrenPlugin(root), getManifest(root, true), getControllers(root \ "controllers"), getEvents(root))
        }else classOf[Plugin].newInstance()
    }

    def getAttributesMap(node: Node):Map[String, AnyRef] = {
        node.attributes.asAttrMap
    }

    def getId(node: Node): String = {
        getAttributesMap(node).getOrElse("id", "").asInstanceOf[String]
    }

    def getData(node: Node): Map[String, AnyRef] = {
        if(null != node) Map("cwp_element_name" -> node.label) ++ getAttributesMap(node) else Map()
    }

    //TODO: Review the below code, this is as per the existing logic
    def getCdata(node: Node): String = {
        if(null != node && !node.child.isEmpty){
            val childNodes = node.child
            var cdata = ""
            childNodes.toList.filter(childNode => childNode.isInstanceOf[PCData]).map(childNode => {
                cdata = childNode.text
            })
            cdata
        }else ""
    }

    def getChildrenPlugin(node: Node): List[Plugin] = {
        if(null != node && !node.child.isEmpty){
            val nodeList = node.child
            nodeList.toList.filter(childNode => childNode.isInstanceOf[Elem] && !nonPluginElements.contains(childNode.label) && !"event".equalsIgnoreCase(childNode.label))
                    .map(chilNode => Plugin(getId(chilNode), getData(chilNode), getInnerText(chilNode), getCdata(chilNode), getChildrenPlugin(chilNode), getManifest(chilNode, false), getControllers(chilNode \"controllers"), getEvents(chilNode)))
        }else {
            List()
        }
    }

    def getInnerText(node: Node): String = {
        if(null != node && node.isInstanceOf[Elem] && !node.child.isEmpty){
            val childNodes = node.child
            val innerTextlist = childNodes.toList.filter(childNode => childNode.isInstanceOf[Text]).map(item => item.text)
            if(!innerTextlist.isEmpty) innerTextlist.head else  ""        
        }else ""
    }

    def getMedia(node: Node, validateNode: Boolean): Media = {
        if(null != node){
            val attributeMap = getAttributesMap(node)
            val id: String = attributeMap.getOrElse("id", "").asInstanceOf[String]
            val `type`: String = attributeMap.getOrElse("type", "").asInstanceOf[String]
            val src: String = attributeMap.getOrElse("src", "").asInstanceOf[String]
            if(validateNode){
                if(StringUtils.isBlank(id))
                    throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('id' is required.) in '" + node.buildString(true) + "' ...")
                if(!(StringUtils.isNotBlank(`type`) && (StringUtils.equalsIgnoreCase(`type`, "js") || StringUtils.equalsIgnoreCase(`type`, "css"))))
                    throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('type' is required.) in '" + node.buildString(true) + "' ...")
                if(StringUtils.isBlank(src))
                    throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('src' is required.) in '" + node.buildString(true) + "' ...")
            }
            Media(id, getData(node), getInnerText(node), getCdata(node), src, `type`, getChildrenPlugin(node))
        } else classOf[Media].newInstance()
    }

    def getManifest(node: Node, validateNode: Boolean): Manifest = {
        val childNodes = node.child
        var manifestNode : Node = null
        childNodes.toList.filter(childNode => childNode.isInstanceOf[PCData]).map(childNode => manifestNode = childNode)
        val mediaList = {
            if(null != manifestNode && !manifestNode.child.isEmpty){
                manifestNode.child.toList.filter(childNode => childNode.isInstanceOf[Elem] && "media".equalsIgnoreCase(childNode.label)).map(childNode => getMedia(childNode, validateNode))
            } else List()
        }
        if(null != manifestNode){
            Manifest(getId(manifestNode), getData(manifestNode), getInnerText(manifestNode), getCdata(manifestNode), mediaList)
        } else classOf[Manifest].newInstance()
    }

    def getControllers(nodeList: Seq[Node]): List[Controller] = {
        if(null != nodeList && nodeList.length > 0) {
            nodeList.toList.filter(node => node.isInstanceOf[Elem]).map(node => Controller(node.\@("id") , getData(node), getInnerText(node), getCdata(node)))
        } else {
            List()
        }
    }

    def getEvents(node: Node): List[Event] = {
        var eventsList: ListBuffer[Event] = ListBuffer()
        if(null != node && !node.child.isEmpty){
            val childNodes = node.child
            childNodes.toList.map(childNode => {
                if(childNode.isInstanceOf[Elem] && "events".equalsIgnoreCase(childNode.label)){
                    eventsList ++= getEvents(childNode)
                }
                if(childNode.isInstanceOf[Elem] && "event".equalsIgnoreCase(childNode.label)){
                    eventsList += Event(getId(childNode), getData(childNode), getInnerText(childNode), getCdata(childNode), getChildrenPlugin(childNode))
                }
            })
        }
        eventsList.toList
    }


    /**
     * serialize
     *
     */
    def toString(plugin: Plugin): String = {
        val strBuilder = StringBuilder.newBuilder
        if(null != plugin) {
            strBuilder.append(getElementXml(plugin.data))
            .append(getInnerTextXml(plugin.innerText))
            .append(getCdataXml(plugin.cData))
            .append(getContentManifestXml(plugin.manifest))
            .append(getContentControllersXml(plugin.controllers))
            .append(getPluginsXml(plugin.childrenPlugin))
            .append(getEventsXml(plugin.events))
            .append(getEndTag(plugin.data.getOrElse("cwp_element_name", "").asInstanceOf[String]))
        }
        strBuilder.toString()
    }

    def getElementXml(data: Map[String, AnyRef]): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(null != data){
            strBuilder.append(START_TAG_OPENING + data.get("cwp_element_name").get)
            data.map(entry => strBuilder.append(BLANK_SPACE + entry._1 + ATTRIBUTE_KEY_VALUE_SEPARATOR + DOUBLE_QUOTE + entry._2 + DOUBLE_QUOTE))
            strBuilder.append(TAG_CLOSING)
        }
        strBuilder
    }

    def getInnerTextXml(innerText: String): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(StringUtils.isNotBlank(innerText)) strBuilder.append(StringEscapeUtils.escapeXml(innerText))
        strBuilder
    }

    def getCdataXml(cData: String): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(StringUtils.isNotBlank(cData)) strBuilder.append("<![CDATA[" + cData + "]]>")
        strBuilder
    }

    def getContentManifestXml(manifest: Manifest) = {
        val strBuilder = StringBuilder.newBuilder
        if(null != manifest && null != manifest.medias && !manifest.medias.isEmpty){
            strBuilder.append(getElementXml(manifest.data)).append(getInnerTextXml(manifest.innerText))
            .append(getCdataXml(manifest.cData))
            .append(getMediaXml(manifest.medias))
            .append(getEndTag(manifest.data.getOrElse("cwp_element_name", "").asInstanceOf[String]))
        }
    }

    def getPluginsXml(childrenPlugin: List[Plugin]): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(null != childrenPlugin && !childrenPlugin.isEmpty){
            childrenPlugin.map(plugin => strBuilder.append(toString(plugin)))
        }
        strBuilder
    }

    def getContentControllersXml(controllers: List[Controller]): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(null != controllers && !controllers.isEmpty) {
            controllers.map(controller => {
                strBuilder.append(getElementXml(controller.data))
                        .append(getInnerTextXml(controller.innerText))
                        .append(getCdataXml(controller.cData))
                        .append(getEndTag(controller.data.getOrElse("cwp_element_name", "").asInstanceOf[String]))
            })
        }
        strBuilder
    }

    def getEventsXml(events: List[Event]): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(null != events && !events.isEmpty){
            if(events.size > 1) strBuilder.append(START_TAG_OPENING + "events" + TAG_CLOSING)
            events.map(event => strBuilder.append(getElementXml(event.data)).append(getInnerTextXml(event.innerText)).append(getCdataXml(event.cData)).append(getPluginsXml(event.childrenPlugin)).append(getEndTag("event")) )
            if(events.size > 1) strBuilder.append(getEndTag("events"))
        }
        strBuilder
    }

    def getEndTag(str: String): String = {
        if(StringUtils.isNotBlank(str)) END_TAG_OPENING + str + TAG_CLOSING
        else ""
    }

    def getMediaXml(medias: List[Media]): StringBuilder = {
        val strBuilder = StringBuilder.newBuilder
        if(null != medias && !medias.isEmpty){
            medias.map(media => {
                strBuilder.append(getElementXml(media.data)).append(getInnerTextXml(media.innerText)).append(media.cData).append(getPluginsXml(media.childrenPlugin)).append(getEndTag("media"))
            })
        }
        strBuilder
    }
}
