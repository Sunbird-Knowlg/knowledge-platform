package org.sunbird.mimetype.ecml.processor

import org.sunbird.cloudstore.StorageService

class BaseProcessor(basePath: String, identifier: String) extends IProcessor(basePath, identifier) {
    override def process(ecrf: Plugin)(implicit ss: StorageService): Plugin = {
        ecrf
    }
}
