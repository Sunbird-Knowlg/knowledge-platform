package org.sunbird.mimetype.ecml.processor

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException

trait MissingControllerValidatorProcessor extends IProcessor {

    abstract override def process(ecrf: Plugin)(implicit ss: StorageService): Plugin = {
        validateControllers(ecrf)
        super.process(ecrf)
    }

    def validateControllers(plugin: Plugin) = {
        if(null != plugin.controllers && !plugin.controllers.isEmpty){
            val controllers:List[Controller] = plugin.controllers
            val controllerIds:List[String] = controllers.map(ctrl => ctrl.id)
            if(controllerIds.size != controllerIds.distinct.size)
                throw new ClientException("DUPLICATE_CONTROLLER_ID", "Error! Duplicate Controller Id used in the ECML. "
                        +  controllerIds.groupBy(identity).mapValues(_.size).filter(p => p._2 > 1).keySet
                        +"' are used more than once in the ECML.]")

            val blankCdata:List[Controller] = controllers.filter(ctrl => StringUtils.isBlank(ctrl.cData))
            if(!blankCdata.isEmpty) blankCdata.map(ctrl => {
                val controllerType:String = ctrl.data.get("type").asInstanceOf[String]
                if("data".equalsIgnoreCase(controllerType) && !new File(getBasePath() + File.separator + "data" + File.separator + ctrl.id + ".json").exists())
                    throw new ClientException("MISSING_CONTROLLER_FILE", "Error! Missing Controller file. | [Controller Id '" + ctrl.id +"' is missing.]")
                else if("items".equalsIgnoreCase(controllerType) && !new File(getBasePath() + File.separator + "items" + File.separator + ctrl.id + ".json").exists())
                    throw new ClientException("MISSING_CONTROLLER_FILE", "Error! Missing Controller file. | [Controller Id '" + ctrl.id +"' is missing.]")
            })
        }
    }
}
