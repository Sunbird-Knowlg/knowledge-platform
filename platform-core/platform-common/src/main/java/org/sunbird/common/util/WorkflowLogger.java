package org.sunbird.common.util;

import java.util.Map;
import java.util.HashMap;

/**
 * Workflow Logger for tracking API request flow through different stages
 * Provides structured logging with stage numbers, data flow, and navigation info
 */
public class WorkflowLogger {
    
    private static final String WORKFLOW_PREFIX = "═══════════════════════════════════════════════════════";
    private static final String STAGE_PREFIX = "║ STAGE";
    private static final String DATA_PREFIX = "║ DATA";
    private static final String NAV_PREFIX = "║ NAVIGATION";
    private static final String WORKFLOW_SUFFIX = "═══════════════════════════════════════════════════════";
    
    /**
     * Log workflow stage with full context
     * 
     * @param stageNumber Current stage number
     * @param stageName Name of current stage
     * @param operation Operation being performed
     * @param previousStage Previous stage name (null if first stage)
     * @param nextStages Possible next stages (comma-separated)
     * @param dataContext Data flow context (identifier, objectType, etc.)
     */
    public static void logStage(int stageNumber, String stageName, String operation, 
                                 String previousStage, String nextStages, Map<String, Object> dataContext) {
        StringBuilder log = new StringBuilder("\n");
        log.append(WORKFLOW_PREFIX).append("\n");
        log.append(STAGE_PREFIX).append(" ").append(stageNumber).append(": ").append(stageName).append("\n");
        log.append("║ Operation: ").append(operation).append("\n");
        log.append("║\n");
        
        // Navigation info
        log.append(NAV_PREFIX).append(":\n");
        if (previousStage != null) {
            log.append("║   ← Previous: ").append(previousStage).append("\n");
        } else {
            log.append("║   ← Previous: [START]\n");
        }
        log.append("║   • Current:  ").append(stageName).append("\n");
        log.append("║   → Next:     ").append(nextStages).append("\n");
        log.append("║\n");
        
        // Data flow context
        log.append(DATA_PREFIX).append(" FLOW:\n");
        if (dataContext != null && !dataContext.isEmpty()) {
            for (Map.Entry<String, Object> entry : dataContext.entrySet()) {
                String value = entry.getValue() != null ? entry.getValue().toString() : "null";
                if (value.length() > 100) {
                    value = value.substring(0, 100) + "... [truncated]";
                }
                log.append("║   • ").append(entry.getKey()).append(": ").append(value).append("\n");
            }
        } else {
            log.append("║   [No data context]\n");
        }
        
        log.append(WORKFLOW_SUFFIX);
        
        System.out.println(log.toString());
    }
    
    /**
     * Log error at a workflow stage
     */
    public static void logError(int stageNumber, String stageName, String operation, 
                                 String error, Map<String, Object> dataContext) {
        StringBuilder log = new StringBuilder("\n");
        log.append("✗✗✗ ERROR AT STAGE ").append(stageNumber).append(": ").append(stageName).append(" ✗✗✗\n");
        log.append("Operation: ").append(operation).append("\n");
        log.append("Error: ").append(error).append("\n");
        
        if (dataContext != null && !dataContext.isEmpty()) {
            log.append("Context Data:\n");
            for (Map.Entry<String, Object> entry : dataContext.entrySet()) {
                log.append("  • ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        System.err.println(log.toString());
    }
    
    /**
     * Quick log for simple stages
     */
    public static void logQuick(int stageNumber, String stageName, String identifier, String objectType) {
        System.out.println(String.format("[STAGE %d] %s - %s (%s)", 
            stageNumber, stageName, identifier != null ? identifier : "N/A", objectType != null ? objectType : "N/A"));
    }
}
