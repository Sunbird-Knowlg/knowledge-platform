package org.sunbird.graph.dac.model;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.graph.common.enums.SystemProperties;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class Filter implements Serializable {

    private static final long serialVersionUID = 6519813570430055306L;
    private String property;
    private Object value;
    private String operator;

    public Filter() {

    }

    public Filter(String property, Object value) {
        setProperty(property);
        setValue(value);
    }

    public Filter(String property, String operator, Object value) {
        setProperty(property);
        setOperator(operator);
        setValue(value);
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getOperator() {
        if (StringUtils.isBlank(this.operator) || !SearchConditions.operators.contains(this.operator))
            this.operator = SearchConditions.OP_EQUAL;
        return this.operator;
    }

    public void setOperator(String operator) {
        if (StringUtils.isBlank(operator) || !SearchConditions.operators.contains(operator))
            operator = SearchConditions.OP_EQUAL;
        this.operator = operator;
    }

    public String getCypher(SearchCriteria sc, String param) {
        String graphDBName = Platform.config.hasPath("graphDatabase") ? Platform.config.getConfig("graphDatabase").toString() : "janusgraph";
        String queryString = "";
        switch(graphDBName) {
            case "neo4j" :
                queryString=  getNeo4jCypher(sc, param);
                break;
            case "janusgraph" :
                queryString= getJanusCypher(sc, param);
                break;
        }
        return queryString;
    }

    public String getNeo4jCypher(SearchCriteria sc, String param) {
        StringBuilder sb = new StringBuilder();
        int pIndex = sc.pIndex;
        if (StringUtils.isBlank(param))
            param = "n";
        param = param + ".";
        if (StringUtils.equals("identifier", property)) {
            property = SystemProperties.IL_UNIQUE_ID.name();
        }
        if (SearchConditions.OP_EQUAL.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" = {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        } else if (SearchConditions.OP_LIKE.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" =~ {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, "(?i).*" + value + ".*");
            pIndex += 1;
        } else if (SearchConditions.OP_STARTS_WITH.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" =~ {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, "(?i)" + value + ".*");
            pIndex += 1;
        } else if (SearchConditions.OP_ENDS_WITH.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" =~ {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, "(?i).*" + value);
            pIndex += 1;
        } else if (SearchConditions.OP_GREATER_THAN.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" > {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        } else if (SearchConditions.OP_GREATER_OR_EQUAL.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" >= {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        } else if (SearchConditions.OP_LESS_THAN.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" < {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        } else if (SearchConditions.OP_LESS_OR_EQUAL.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" <= {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        } else if (SearchConditions.OP_NOT_EQUAL.equals(getOperator())) {
            sb.append(" NOT ").append(param).append(property).append(" = {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        } else if (SearchConditions.OP_IN.equals(getOperator())) {
            sb.append(" ").append(param).append(property).append(" in {").append(pIndex).append("} ");
            sc.params.put("" + pIndex, value);
            pIndex += 1;
        }
        sc.pIndex = pIndex;
        return sb.toString();
    }

    public String getJanusCypher(SearchCriteria sc, String param) {
        StringBuilder sb = new StringBuilder();
        int pIndex = sc.pIndex;

        String propertyKey = (StringUtils.equals("identifier", property)) ? SystemProperties.IL_UNIQUE_ID.name() : property;

        switch (getOperator()) {
            case SearchConditions.OP_EQUAL:
                sb.append(".has('").append(propertyKey).append("', '").append(value).append("')");
                break;
            case SearchConditions.OP_LIKE:
                sb.append(".has('").append(propertyKey).append("', TextP.containing('").append(value).append("'))");
                break;
            case SearchConditions.OP_STARTS_WITH:
                sb.append(".has('").append(propertyKey).append("', TextP.startingWith('").append(value).append("'))");
                break;
            case SearchConditions.OP_ENDS_WITH:
                sb.append(".has('").append(propertyKey).append("', TextP.endingWith('").append(value).append("'))");
                break;
            case SearchConditions.OP_GREATER_THAN:
                sb.append(".has('").append(propertyKey).append("', P.gt(").append(value).append("))");
                break;
            case SearchConditions.OP_GREATER_OR_EQUAL:
                sb.append(".has('").append(propertyKey).append("', P.gte(").append(value).append("))");
                break;
            case SearchConditions.OP_LESS_THAN:
                sb.append(".has('").append(propertyKey).append("', P.lt(").append(value).append("))");
                break;
            case SearchConditions.OP_LESS_OR_EQUAL:
                sb.append(".has('").append(propertyKey).append("', P.lte(").append(value).append("))");
                break;
            case SearchConditions.OP_NOT_EQUAL:
                sb.append(".not(has('").append(propertyKey).append("', '").append(value).append("'))");
                break;
            case SearchConditions.OP_IN:
                List<Object> inValues = (List<Object>) value;
                String inClause = inValues.stream()
                        .map(v -> "'" + v + "'")
                        .collect(Collectors.joining(", "));
                sb.append(".has('").append(propertyKey).append("', P.within(").append(inClause).append("))");
                break;
            default:
                throw new IllegalArgumentException("Unknown operator: " + getOperator());
        }

        return sb.toString();
    }
}
