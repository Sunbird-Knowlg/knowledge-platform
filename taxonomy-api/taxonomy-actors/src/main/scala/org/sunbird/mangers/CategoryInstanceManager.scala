package org.sunbird.mangers

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Slug

object CategoryInstanceManager {

  def generateIdentifier(scopeId: String, code: String): String = {
    var id: String = null
    if (StringUtils.isNotBlank(scopeId)) id = Slug.makeSlug(scopeId + "_" + code)
    id
  }

}
