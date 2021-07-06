package org.sunbird.mimetype.ecml.processor

import java.io.File
import java.nio.charset.StandardCharsets

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService

trait EmbedControllerProcessor extends IProcessor {

    abstract override def process(ecrf: Plugin)(implicit ss: StorageService): Plugin = {
        val controllerList = embedController(ecrf)
        super.process(Plugin(ecrf.id, ecrf.data, ecrf.innerText, ecrf.cData, ecrf.childrenPlugin, ecrf.manifest, controllerList, ecrf.events))
    }

    def embedController(plugin: Plugin):List[Controller] = {
        val controllers = plugin.controllers
        controllers.map(control => {
            if(StringUtils.isBlank(control.cData)){
                val id:String = control.data.get("id").asInstanceOf[String]
                val dataType:String = control.data.get("type").asInstanceOf[String]
                if(StringUtils.isNoneBlank(id) && StringUtils.isNotBlank(dataType)) {
                    val file = {
                        if("items".equalsIgnoreCase(dataType))
                            new File(getBasePath() + File.separator + "items" + File.separator + id + ".json")
                        else if("data".equalsIgnoreCase(dataType))
                            new File(getBasePath() + File.separator + "data" + File.separator + id + ".json")
                        else null
                    }
                    if(null != file && file.exists()){
                        Controller(control.id, control.data, control.innerText, FileUtils.readFileToString(file, StandardCharsets.UTF_8))
                    }else control
                }else control
            }else control
        })
    }
}
