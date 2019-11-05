package org.sunbird.graph.dac.mgr;

import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;

public interface IGraphDACSearchMgr {

	Response checkCyclicLoop(Request request);
    

}
