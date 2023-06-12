package org.sunbird.graph.schema

import org.sunbird.graph.BaseSpec

class TestDefinitionNode extends BaseSpec {

  "fetchOneOfProps" should "return oneOfProps from definition if available" in {
    val oneOfProps = DefinitionNode.fetchOneOfProps("domain", "1.0","content")
    assert(oneOfProps.isEmpty)
  }
}
