package org.sunbird.mimetype.ecml.processor

case class Plugin(id: String, data: Map[String, AnyRef], innerText: String, cData: String, childrenPlugin: List[Plugin], manifest: Manifest, controllers: List[Controller], events: List[Event])
case class Manifest(id: String, data: Map[String, AnyRef], innerText: String, cData: String, medias: List[Media])
case class Controller(id: String, data: Map[String, AnyRef], innerText: String, cData: String)
case class Media(id: String, data: Map[String, AnyRef], innerText: String, cData: String, src: String, `type`: String, childrenPlugin: List[Plugin])
case class Event(id: String, data: Map[String, AnyRef], innerText: String, cData: String, childrenPlugin: List[Plugin])


