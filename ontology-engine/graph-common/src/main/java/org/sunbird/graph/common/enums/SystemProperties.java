package org.sunbird.graph.common.enums;

public enum SystemProperties {

    IL_SYS_NODE_TYPE, IL_FUNC_OBJECT_TYPE, IL_UNIQUE_ID, IL_SEQUENCE_INDEX;

    public static boolean isSystemProperty(String str) {
        SystemProperties val = null;
        try {
            val = SystemProperties.valueOf(str);
        } catch (Exception e) {
        }
        if (null == val)
            return false;
        return true;
    }

}
