package org.sunbird.graph.engine

class CaseClasses extends Serializable {}

case class SchemaIdentifier(objectType: String, version: String, graphId: String = "domain")
