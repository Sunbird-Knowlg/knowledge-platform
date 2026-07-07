package org.sunbird.search.util;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class SearchInputValidator {

    private static final Set<String> DEFAULT_FACET_FIELDS = new HashSet<>(Arrays.asList(
            "objectType", "contentType", "status", "mimeType", "primaryCategory",
            "channel", "framework", "board", "medium", "gradeLevel", "subject",
            "resourceType", "audience", "license", "language", "domain",
            "targetFWIds", "se_boards", "se_mediums", "se_gradeLevels", "se_subjects",
            "visibility", "compatibilityLevel", "mediaType", "origin", "createdBy"
    ));

    private static final Set<String> DEFAULT_SORT_FIELDS = new HashSet<>(Arrays.asList(
            "name", "lastUpdatedOn", "createdOn", "pkgVersion", "identifier",
            "objectType", "contentType", "status", "mimeType", "primaryCategory",
            "lastPublishedOn", "lastSubmittedOn", "rating", "me_averageRating"
    ));

    private static final Set<String> DEFAULT_EXISTS_FIELDS = new HashSet<>(Arrays.asList(
            "name", "description", "status", "mimeType", "primaryCategory", "objectType",
            "contentType", "channel", "framework", "board", "medium", "gradeLevel",
            "subject", "artifactUrl", "downloadUrl", "variants", "pkgVersion",
            "audience", "license", "language", "lastPublishedOn", "identifier",
            "createdOn", "lastUpdatedOn", "resourceType", "appIcon", "posterImage",
            "visibility", "compatibilityLevel", "mediaType", "origin", "rating",
            "originData"
    ));

    private static final Set<String> ALLOWED_FACET_FIELDS;
    private static final Set<String> ALLOWED_SORT_FIELDS;
    private static final Set<String> ALLOWED_EXISTS_FIELDS;
    private static final int MAX_FACETS;
    private static final int MAX_SORT_FIELDS;
    private static final int MAX_EXISTS_FIELDS;

    static {
        ALLOWED_FACET_FIELDS = loadConfiguredFields("search.allowed.facet.fields", DEFAULT_FACET_FIELDS);
        ALLOWED_SORT_FIELDS = loadConfiguredFields("search.allowed.sort.fields", DEFAULT_SORT_FIELDS);
        ALLOWED_EXISTS_FIELDS = loadConfiguredFields("search.allowed.exists.fields", DEFAULT_EXISTS_FIELDS);
        MAX_FACETS = getConfigInt("search.max.facets", 20);
        MAX_SORT_FIELDS = getConfigInt("search.max.sort.fields", 5);
        MAX_EXISTS_FIELDS = getConfigInt("search.max.exists.fields", 10);
    }

    private static Set<String> loadConfiguredFields(String configKey, Set<String> defaults) {
        try {
            if (Platform.config.hasPath(configKey)) {
                List<String> configured = Platform.config.getStringList(configKey);
                if (configured != null && !configured.isEmpty()) {
                    return Collections.unmodifiableSet(new HashSet<>(configured));
                }
            }
        } catch (Exception e) {
            // Config not available or parse error — use defaults
        }
        return Collections.unmodifiableSet(defaults);
    }

    private static int getConfigInt(String configKey, int defaultValue) {
        try {
            if (Platform.config.hasPath(configKey)) {
                return Platform.config.getInt(configKey);
            }
        } catch (Exception e) {
            // Config not available — use default
        }
        return defaultValue;
    }

    private static final Pattern REGEX_METACHAR = Pattern.compile("[.?+*|{}\\[\\]()\\\\^$]");

    public static String escapeRegexValue(String input) {
        if (StringUtils.isBlank(input)) return input;
        return REGEX_METACHAR.matcher(input).replaceAll("\\\\$0");
    }

    public static void validateFacets(List<String> facets) {
        if (facets == null || facets.isEmpty()) return;
        if (facets.size() > MAX_FACETS)
            throw new ClientException("ERR_INVALID_FACETS", "Maximum " + MAX_FACETS + " facets allowed");
        for (String facet : facets) {
            if (!ALLOWED_FACET_FIELDS.contains(facet))
                throw new ClientException("ERR_INVALID_FACETS", "Facet field not allowed: " + facet);
        }
    }

    public static void validateSortBy(Map<String, String> sortBy) {
        if (sortBy == null || sortBy.isEmpty()) return;
        if (sortBy.size() > MAX_SORT_FIELDS)
            throw new ClientException("ERR_INVALID_SORT", "Maximum " + MAX_SORT_FIELDS + " sort fields allowed");
        for (Map.Entry<String, String> entry : sortBy.entrySet()) {
            if (!ALLOWED_SORT_FIELDS.contains(entry.getKey()))
                throw new ClientException("ERR_INVALID_SORT", "Sort field not allowed: " + entry.getKey());
            String dir = entry.getValue();
            if (dir != null && !dir.equalsIgnoreCase("asc") && !dir.equalsIgnoreCase("desc"))
                throw new ClientException("ERR_INVALID_SORT", "Sort direction must be 'asc' or 'desc'");
        }
    }

    public static void validateExistsFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) return;
        if (fields.size() > MAX_EXISTS_FIELDS)
            throw new ClientException("ERR_INVALID_EXISTS", "Maximum " + MAX_EXISTS_FIELDS + " exists fields allowed");
        for (String field : fields) {
            if (!ALLOWED_EXISTS_FIELDS.contains(field))
                throw new ClientException("ERR_INVALID_EXISTS", "Exists field not allowed: " + field);
        }
    }

    public static void validateSoftConstraints(Map<String, Object> softConstraints) {
        if (softConstraints == null || softConstraints.isEmpty()) return;
        for (Map.Entry<String, Object> entry : softConstraints.entrySet()) {
            String key = entry.getKey();
            if (!ALLOWED_EXISTS_FIELDS.contains(key))
                throw new ClientException("ERR_INVALID_SOFT_CONSTRAINTS",
                        "Soft constraint field not allowed: " + key);
        }
    }
}
