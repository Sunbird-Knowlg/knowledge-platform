package org.sunbird.mimetype.ecml.processor

class BaseProcessor(basePath: String, identifier: String) extends IProcessor(basePath, identifier) {
    override def process(ecrf: Plugin): Plugin = {
        ecrf
    }
}
