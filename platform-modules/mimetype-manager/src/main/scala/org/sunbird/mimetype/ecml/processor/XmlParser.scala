package org.sunbird.mimetype.ecml.processor

import java.io.{StringReader, StringWriter}

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{Transformer, TransformerFactory}
import org.apache.commons.lang.{StringEscapeUtils, StringUtils}
import org.sunbird.common.exception.ClientException
import org.w3c.dom.{Element, Node, NodeList}

import scala.collection.JavaConversions._
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
        val strReader = new StringReader(xml)
        try{
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = builder.parse(new InputSource(strReader))
            document.getDocumentElement.normalize()
            val root = document.getDocumentElement
            processDocument(root)
        } finally {
            if(null != strReader) strReader.close()

        }
    }

    def processDocument(root: Element): Plugin = {
        if(null != root) {
            Plugin(getId(root), getData(root), "", getCdata(root), getChildrenPlugin(root), getManifest(root, true), getControllers(root.getElementsByTagName("controllers")), getEvents(root))
        }else classOf[Plugin].newInstance()
    }

    def getAttributesMap(node: Node):Map[String, AnyRef] = {
        (0 to node.getAttributes.getLength).toList.map(index => (node.getAttributes.item(index).getNodeName -> node.getAttributes.item(index).getNodeValue)).toMap
    }

    def getId(node: Node): String = {
        getAttributesMap(node).getOrElse("id", "").asInstanceOf[String]
    }

    def getData(node: Node): Map[String, AnyRef] = {
        if(null != node) Map("cwp_element_name" -> node.getNodeName) ++ getAttributesMap(node) else Map()
    }

    //TODO: Review the below code, this is as per the existing logic
    def getCdata(node: Node): String = {
        if(null != node && node.hasChildNodes){
            val childNodes = node.getChildNodes
            var cdata = ""
            (0 to childNodes.getLength).toList.map(index => {
                if(Node.CDATA_SECTION_NODE == childNodes.item(index).getNodeType)
                    cdata = childNodes.item(index).getNodeValue
            })
            cdata
        }else ""
    }

    def getChildrenPlugin(node: Node): List[Plugin] = {
        if(null != node && node.hasChildNodes){
            val nodeList = node.getChildNodes
            (0 to nodeList.getLength).toList.map(index => nodeList.item(index))
                    .filter(node => (Node.ELEMENT_NODE == node.getNodeType && !nonPluginElements.contains(node.getNodeName) && !"event".equalsIgnoreCase(node.getNodeName)))
                    .map(node => Plugin(getId(node), getData(node), getInnerText(node), getCdata(node), getChildrenPlugin(node), getManifest(node, false), getControllers(node.asInstanceOf[Element].getElementsByTagName("controllers")), getEvents(node)))
        }else {
            List()
        }
    }

    def getInnerText(node: Node): String = {
        if(null != node && Node.ELEMENT_NODE == node.getNodeType && node.hasChildNodes){
            val childNodes = node.getChildNodes
            (0 to childNodes.getLength).toList.map(index => childNodes.item(index)).filter(item => Node.TEXT_NODE == item.getNodeType).map(item => item.getTextContent).head
        }else ""
    }

    def getNodeString(node: Node): String = {
        val writer = new StringWriter()
        try {
            val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
            transformer.transform(new DOMSource(node), new StreamResult(writer))
            val output: String = writer.toString
            output.substring(output.indexOf("?>") + 2)
        } finally {
            writer.close()
        }
    }

    def getMedia(node: Node, validateNode: Boolean): Media = {
        if(null != node){
            val attributeMap = getAttributesMap(node)
            val id: String = attributeMap.getOrElse("id", "").asInstanceOf[String]
            val `type`: String = attributeMap.getOrElse("type", "").asInstanceOf[String]
            val src: String = attributeMap.getOrElse("src", "").asInstanceOf[String]
            if(validateNode){
                if(StringUtils.isBlank(id))
                    throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('id' is required.) in '" + getNodeString(node) + "' ...")
                if(!(StringUtils.isNotBlank(`type`) && (StringUtils.equalsIgnoreCase(`type`, "js") || StringUtils.equalsIgnoreCase(`type`, "css"))))
                    throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('type' is required.) in '" + getNodeString(node) + "' ...")
                if(StringUtils.isBlank(src))
                    throw new ClientException("INVALID_MEDIA", "Error! Invalid Media ('src' is required.) in '" + getNodeString(node) + "' ...")
            }
            Media(id, getData(node), getInnerText(node), getCdata(node), src, `type`, getChildrenPlugin(node))
        } else classOf[Media].newInstance()
    }

    def getManifest(node: Node, validateNode: Boolean): Manifest = {
        val childNodes = node.getChildNodes
        var manifestNode : Node = null
        (0 to childNodes.getLength).toList.map(index => {
            if(Node.CDATA_SECTION_NODE == childNodes.item(index).getNodeType)
                manifestNode = childNodes.item(index)
        })
        val mediaList:ListBuffer[Media] = ListBuffer()
        if(null != manifestNode && manifestNode.hasChildNodes) {
            (0 to manifestNode.getChildNodes.getLength).toList.foreach(index => {
                if(Node.ELEMENT_NODE == manifestNode.getChildNodes.item(index).getNodeType && "media".equalsIgnoreCase(manifestNode.getChildNodes.item(index).getNodeName))
                    mediaList += getMedia(manifestNode.getChildNodes.item(index), validateNode)
            })
        }
        Manifest(getId(manifestNode), getData(manifestNode), getInnerText(manifestNode), getCdata(manifestNode), mediaList.toList)
    }

    def getControllers(nodeList: NodeList): List[Controller] = {
        if(null != nodeList && nodeList.getLength > 0) {
            (0 to nodeList.getLength).toList.map(index => nodeList.item(index)).filter(node => node.getNodeType == Node.ELEMENT_NODE).map(node => {
                val attributeMap = getAttributesMap(node)
                Controller(attributeMap.getOrElse("id","").asInstanceOf[String], getData(node), getInnerText(node), getCdata(node))
            })
        } else {
            List()
        }
    }

    def getEvents(node: Node): List[Event] = {
        var eventsList: ListBuffer[Event] = ListBuffer()
        if(null != node && node.hasChildNodes){
            val childNodes = node.getChildNodes
            (0 to childNodes.getLength).toList.map(index => {
                if(Node.ELEMENT_NODE == childNodes.item(index).getNodeType && "events".equalsIgnoreCase(childNodes.item(index).getNodeName)){
                    eventsList ++= getEvents(childNodes.item(index))
                }
                if(Node.ELEMENT_NODE == childNodes.item(index).getNodeType && "event".equalsIgnoreCase(childNodes.item(index).getNodeName)){
                    eventsList += Event(getId(childNodes.item(index)), getData(childNodes.item(index)), getInnerText(childNodes.item(index)), getCdata(childNodes.item(index)), getChildrenPlugin(childNodes.item(index)))
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
