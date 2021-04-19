package org.sunbird.schema.impl;

import org.apache.commons.lang3.StringUtils;
import org.leadpony.justify.api.Problem;
import org.leadpony.justify.api.ProblemHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CustomProblemHandler implements ProblemHandler {

    private List<String> messages;

    protected CustomProblemHandler() {
        this.messages = new ArrayList<>();
    }

    @Override
    public void handleProblems(List<Problem> problems) {
        List<String> tempMessageList = problems.stream()
                .map(problem -> processMessage(problem))
                .filter(message -> StringUtils.isNotBlank(message))
                .collect(Collectors.toList());
        Collections.reverse(tempMessageList);
        this.messages.addAll(tempMessageList);
    }

    protected List<String> getProblemMessages() {
        return this.messages;
    }

    private String processMessage(Problem problem) {
        String keyword = StringUtils.isNotBlank(problem.getKeyword()) ? problem.getKeyword() : "additionalProp";
        switch (keyword) {
            case "enum":
                return ("Metadata " + Arrays.stream(problem.getPointer().split("/"))
                        .filter(StringUtils::isNotBlank)
                        .findFirst().get()
                        + " should be one of: "
                        + problem.parametersAsMap().get("expected")).replace("\"", "");
            case "required":
                String param;
                if (StringUtils.isNotBlank(problem.getPointer())) {
                    param = problem.getPointer().replaceAll("/", "") + "." + problem.parametersAsMap().get(problem.getKeyword());
                } else {
                    param = problem.parametersAsMap().get(problem.getKeyword()).toString();
                }
                return "Required Metadata "
                        + param.replace("\"", "")
                        + " not set";
            case "type": {
                return ("Metadata " + Arrays.stream(problem.getPointer().split("/"))
                        .filter(StringUtils::isNotBlank)
                        .findFirst().get()
                        + " should be a/an "
                        + StringUtils.capitalize(((Enum) problem.parametersAsMap().get("expected")).name().toLowerCase())).replace("\"", "")
                        + " value";
            }
            case "additionalProp": {
                return ("Metadata " + Arrays.stream(problem.getPointer().split("/"))
                        .filter(StringUtils::isNotBlank)
                        .findFirst().get()
                        + " cannot have new property with name "
                        + ((String) problem.parametersAsMap().get("name")).replace("\"", ""));
            }
            case "format": {
                return ("Incorrect format for " + Arrays.stream(problem.getPointer().split("/"))
                        .filter(StringUtils::isNotBlank)
                        .findFirst().orElse("")
                        + " : "
                        + problem.getMessage());
            }
            default:
                return "";
        }
    }
}