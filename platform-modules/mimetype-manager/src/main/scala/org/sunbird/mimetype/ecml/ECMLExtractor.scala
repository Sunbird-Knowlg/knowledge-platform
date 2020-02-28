package org.sunbird.mimetype.ecml

import org.sunbird.mimetype.ecml.processor.{AssetsValidatorProcessor, BaseProcessor, EmbedControllerProcessor, GlobalizeAssetProcessor, MissingAssetValidatorProcessor, MissingControllerValidatorProcessor}

class ECMLExtractor(basePath: String, identifier: String) extends BaseProcessor(basePath, identifier) with EmbedControllerProcessor with GlobalizeAssetProcessor with MissingControllerValidatorProcessor with AssetsValidatorProcessor with MissingAssetValidatorProcessor {

}
