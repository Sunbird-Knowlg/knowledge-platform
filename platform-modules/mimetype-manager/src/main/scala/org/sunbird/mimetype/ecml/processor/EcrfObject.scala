package org.sunbird.mimetype.ecml.processor

import scala.annotation.Annotation

case class Plugin(id: String, data: Map[String, AnyRef], innerText: String, cData: String, childrenPlugin: List[Plugin], manifest: Manifest, controllers: List[Controller], events: List[Event]) {
    def this() = this("", null, "", "", null, null, null, null)
}
case class Manifest(id: String, data: Map[String, AnyRef], innerText: String, cData: String, medias: List[Media]) {
    def this() = this("", null, "", "", null)
}
case class Controller(id: String, data: Map[String, AnyRef], innerText: String, cData: String) {
    def this() = this("", null, "", "")
}
case class Media(id: String, data: Map[String, AnyRef], innerText: String, cData: String, src: String, `type`: String, childrenPlugin: List[Plugin]) {
    def this() = this("", null, "", "", "", "", null)
}
case class Event(id: String, data: Map[String, AnyRef], innerText: String, cData: String, childrenPlugin: List[Plugin]) {
    def this() = this("", null, "", "", null)
}


