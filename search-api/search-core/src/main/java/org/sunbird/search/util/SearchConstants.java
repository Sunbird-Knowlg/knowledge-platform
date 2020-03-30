package org.sunbird.search.util;

import org.sunbird.common.Platform;

public class SearchConstants {
    public static String COMPOSITE_SEARCH_INDEX = Platform.config.hasPath("compositesearch.index.name") ? Platform.config.getString("compositesearch.index.name"): "compositesearch";
    public static final String COMPOSITE_SEARCH_INDEX_TYPE = "cs";
    public static final String OPERATION_CREATE = "CREATE";
    public static final String OPERATION_UPDATE = "UPDATE";
    public static final String OPERATION_DELETE = "DELETE";
    public static final String NODE_TYPE_DATA = "DATA_NODE";
    public static final String NODE_TYPE_SET = "SET";
    public static final String SEARCH_OPERATION_LESS_THAN = "<";
    public static final String SEARCH_OPERATION_GREATER_THAN = ">";
    public static final String SEARCH_OPERATION_EXISTS = "EXISTS";
    public static final String SEARCH_OPERATION_NOT_EXISTS = "NT_EXISTS";
    public static final String SEARCH_OPERATION_LESS_THAN_EQUALS = "<=";
    public static final String SEARCH_OPERATION_GREATER_THAN_EQUALS = ">=";
    public static final String SEARCH_OPERATION_LIKE = "LIKE";
    public static final String SEARCH_OPERATION_CONTAINS = "CONTAINS";
    public static final String SEARCH_OPERATION_NOT_LIKE = "NT_LIKE";
    public static final String SEARCH_OPERATION_EQUAL = "EQ";
    public static final String SEARCH_OPERATION_NOT_EQUAL = "NT_EQ";
    public static final String SEARCH_OPERATION_NOT_EQUAL_OPERATOR = "!=";
    public static final String SEARCH_OPERATION_NOT_EQUAL_TEXT = "notEquals";
    public static final String SEARCH_OPERATION_NOT_EQUAL_TEXT_UPPERCASE = "NE";
    public static final String SEARCH_OPERATION_NOT_EQUAL_TEXT_LOWERCASE = "ne";
    public static final String SEARCH_OPERATION_STARTS_WITH = "SW";
    public static final String SEARCH_OPERATION_ENDS_WITH = "EW";
    public static final String SEARCH_OPERATION_OR = "OR";
    public static final String SEARCH_OPERATION_AND = "AND";
    public static final String SEARCH_OPERATION_IN = "IN";
    public static final String RAW_FIELD_EXTENSION = ".raw";
    public static final String SEARCH_OPERATION_RANGE_MIN = "min";
    public static final String SEARCH_OPERATION_RANGE_MAX = "max";
    public static final String SEARCH_OPERATION_RANGE = "range";
    public static final String SEARCH_OPERATION_RANGE_GTE="gte";
    public static final String SEARCH_OPERATION_RANGE_LTE="lte";
    public static final String NODE_TYPE_EXTERNAL = "EXTERNAL";
    public static final String NODE_TYPE_DIALCODE_METRICS = "DIALCODE_METRICS";
    public static String DIAL_CODE_INDEX = "dialcode";
    public static String DIAL_CODE_METRICS_INDEX = "dialcodemetrics";
    public static final String DIAL_CODE_INDEX_TYPE = "dc";
    public static final String DIAL_CODE_METRICS_INDEX_TYPE = "dcm";
    public static final String SEARCH_OPERATION_NOT_IN_OPERATOR = "notIn";
    public static final String SEARCH_OPERATION_NOT_IN = "NT_IN";
    public static final String SEARCH_OPERATION_CONTAINS_OPERATOR = "contains";
    public static final String SEARCH_OPERATION_AND_TEXT_LOWERCASE = "and";
    public static final String SEARCH_OPERATION_AND_OPERATOR = "&";


    /**
     * SEARCH ERROR CODES
     */
    public static final String ERR_COMPOSITE_SEARCH_INVALID_QUERY_STRING = "ERR_COMPOSITE_SEARCH_INVALID_QUERY_STRING";
    public static final String ERR_COMPOSITE_SEARCH_UNKNOWN_ERROR = "ERR_COMPOSITE_SEARCH_UNKNOWN_ERROR";
    public static final String ERR_COMPOSITE_SEARCH_INVALID_PARAMS = "ERR_COMPOSITE_SEARCH_INVALID_PARAMS";
    public static final String ERR_SYSTEM_EXCEPTION = "ERR_SYSTEM_EXCEPTION";
    public static final String ERR_SYSTEM_ACTOR_NOT_CREATED = "ERR_SYSTEM_ACTOR_NOT_CREATED";
    public static final String ERR_ROUTER_ACTOR_NOT_FOUND = "ERR_ROUTER_ACTOR_NOT_FOUND";
    public static final String ERR_INVALID_OPERATION = "ERR_INVALID_OPERATION";
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";

    /**
     * SEARCH PARAMS
     */
    public static final String operationType = "operationType";
    public static final String CREATE = "CREATE";
    public static final String DELETE = "DELETE";
    public static final String UPDATE = "UPDATE";
    public static final String nodeGraphId = "nodeGraphId";
    public static final String not_equals = "not_equals";
    public static final String nodeUniqueId = "nodeUniqueId";
    public static final String objectType = "objectType";
    public static final String nodeType = "nodeType";
    public static final String compatibilityLevel = "compatibilityLevel";
    public static final String Content = "Content";
    public static final String transactionData = "transactionData";
    public static final String syncMessage = "syncMessage";
    public static final String properties = "properties";
    public static final String addedProperties = "addedProperties";
    public static final String propertyName = "propertyName";
    public static final String value = "value";
    public static final String removedProperties = "removedProperties";
    public static final String addedTags = "addedTags";
    public static final String removedTags = "removedTags";
    public static final String addedRelations = "addedRelations";
    public static final String graphId = "graphId";
    public static final String identifier = "identifier";
    public static final String key = "key";
    public static final String newValue = "newValue";
    public static final String status = "status";
    public static final String adedTags = "adedTags";
    public static final String RETIRED = "RETIRED";
    public static final String DEFINITION_NODE = "DEFINITION_NODE";
    public static final String graphSyncStatus = "graphSyncStatus";
    public static final String success = "success";
    public static final String query = "query";
    public static final String fields = "fields";
    public static final String operation = "operation";
    public static final String values = "values";
    public static final String limit = "limit";
    public static final String offset = "offset";
    public static final String filters = "filters";
    public static final String exists = "exists";
    public static final String not_exists = "not_exists";
    public static final String facets = "facets";
    public static final String sort_by = "sort_by";
    public static final String graph_id = "graph_id";
    public static final String ContentImage = "ContentImage";
    public static final String mode = "mode";
    public static final String softConstraints = "softConstraints";
    public static String soft = "soft";
}
