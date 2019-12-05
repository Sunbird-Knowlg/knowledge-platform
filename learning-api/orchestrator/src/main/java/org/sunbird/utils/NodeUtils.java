package org.sunbird.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.schema.DefinitionNode;
import scala.collection.JavaConversions;

import java.util.*;
import java.util.stream.Collectors;

public class NodeUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * This method will convert a Node to map
     * @param node
     * @param fields
     * @return
     */
    public static Map<String, Object> serialize(Node node, List<String> fields, String schemaName) {
        Map<String, Object> metadataMap = node.getMetadata();
        List<String> languages = Arrays.asList( (String[]) node.getMetadata().get("language"));
        List<String> languageCodes = new ArrayList<String>();
        languageCodes.addAll(languages.stream().map(lang -> Platform.config.getConfig("languageCode").hasPath(lang.toLowerCase()) ? Platform.config.getConfig("languageCode").getString(lang.toLowerCase()) : "").collect(Collectors.toList()));
        metadataMap.put("languageCode",languageCodes);
        if (CollectionUtils.isNotEmpty(fields))
            filterOutFields(metadataMap, fields);
        metadataMap.put("identifier", node.getIdentifier());
        List<String> jsonProps = JavaConversions.seqAsJavaList(DefinitionNode.fetchJsonProps(node.getGraphId(), "1.0", schemaName));
        Map<String, Object> updatedMetadataMap = metadataMap.entrySet().stream().collect(Collectors.toMap(entry -> handleKeyNames(entry, fields), entry -> convertJsonProperties(entry, jsonProps)));
        Map<String, Object> definitionMap = JavaConversions.mapAsJavaMap(DefinitionNode.getRelationDefinitionMap(node.getGraphId(), "1.0", schemaName));
        if (CollectionUtils.isEmpty(fields) || definitionMap.keySet().stream().anyMatch(key -> fields.contains(key))) {
            getRelationMap(node, updatedMetadataMap, definitionMap);
        }
        return updatedMetadataMap;
    }

    private static void filterOutFields(Map<String, Object> inputMetadata, List<String> fields) {
        inputMetadata.keySet().retainAll(fields);
    }

    private static Object convertJsonProperties(Map.Entry<String, Object> entry, List<String> jsonProps) {
        if (jsonProps.contains(entry.getKey()))
            try {
                return mapper.readTree(entry.getValue().toString());
            } catch (Exception e) {
                return entry.getValue();
            }
        else
            return entry.getValue();
    }

    private static String handleKeyNames(Map.Entry<String, Object> entry, List<String> fields) {
        if (CollectionUtils.isEmpty(fields))
            return entry.getKey().substring(0, 1) + entry.getKey().substring(1);
        else
            return entry.getKey();
    }

    private static void getRelationMap(Node node, Map<String, Object> metadata, Map<String, Object> relationMap) {
        List<Relation> inRelations = CollectionUtils.isEmpty(node.getInRelations()) ? new ArrayList<>(): node.getInRelations();
        List<Relation> outRelations = CollectionUtils.isEmpty(node.getOutRelations()) ? new ArrayList<>(): node.getOutRelations();
        Map<String, List<Map<String, Object>>> relMap = new HashMap<>();
        for (Relation rel : inRelations) {
            if (relMap.containsKey(relationMap.get(rel.getRelationType() + "_in_" + rel.getStartNodeObjectType()))) {
                relMap.get(relationMap.get(rel.getRelationType() + "_in_" + rel.getStartNodeObjectType())).add(populateRelationMaps(rel, "in"));
            } else {
                String relKey = (String) relationMap.get(rel.getRelationType() + "_in_" + rel.getStartNodeObjectType());
                if (StringUtils.isNotBlank(relKey)) {
                    relMap.put(relKey,
                            new ArrayList<Map<String, Object>>() {{
                                add(populateRelationMaps(rel, "in"));
                            }});
                }
            }

        }

        for (Relation rel : outRelations) {
            if (relMap.containsKey(relationMap.get(rel.getRelationType() + "_out_" + rel.getEndNodeObjectType()))) {
                relMap.get(relationMap.get(rel.getRelationType() + "_out_" + rel.getEndNodeObjectType())).add(populateRelationMaps(rel, "out"));
            } else {
                String relKey = (String) relationMap.get(rel.getRelationType() + "_out_" + rel.getEndNodeObjectType());
                if (StringUtils.isNotBlank(relKey)) {
                    relMap.put(relKey,
                            new ArrayList<Map<String, Object>>() {{
                                add(populateRelationMaps(rel, "out"));
                            }});
                }
            }

        }
        metadata.putAll(relMap);
    }

    private static Map<String, Object> populateRelationMaps(Relation relation, String direction) {
        if (StringUtils.equalsAnyIgnoreCase("out", direction)) {
            return new HashMap<String, Object>() {{
                put("identifier", relation.getEndNodeId().replace(".img", ""));
                put("name", relation.getEndNodeName());
                put("objectType", relation.getEndNodeObjectType().replace("Image", ""));
                put("relation", relation.getRelationType());
                put("description", relation.getEndNodeMetadata().get("description"));
                put("status", relation.getEndNodeMetadata().get("status"));
            }};
        } else {
            return new HashMap<String, Object>() {{
                put("identifier", relation.getStartNodeId().replace(".img", ""));
                put("name", relation.getStartNodeName());
                put("objectType", relation.getStartNodeObjectType().replace("Image", ""));
                put("relation", relation.getRelationType());
                put("description", relation.getStartNodeMetadata().get("description"));
                put("status", relation.getStartNodeMetadata().get("status"));
            }};
        }
    }

    public static Boolean isRetired(Node node) {
        return StringUtils.equalsIgnoreCase((String) node.getMetadata().get("status"), "Retired");
    }
}