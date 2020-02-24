package org.sunbird.mimetype.ecml.processor

import java.io.{StringReader, StringWriter}

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{Transformer, TransformerFactory}
import org.apache.commons.lang.StringUtils
import org.sunbird.common.exception.ClientException
import org.w3c.dom.{Element, Node, NodeList}

import scala.collection.mutable.ListBuffer
import scala.xml._
object XmlParser {

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

    def getChildrenPlugin(root: Node): List[Plugin] = ???

    def getInnerText(manifestNode: Node): String = ???

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

    def getEvents(root: Element): List[Event] = ???


}
