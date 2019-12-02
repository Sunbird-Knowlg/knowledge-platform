package org.sunbird.schema.impl;

import org.apache.commons.lang3.StringUtils;
import org.leadpony.justify.api.Problem;
import org.leadpony.justify.api.ProblemHandler;
import scala.collection.immutable.StringOps;

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
        switch (problem.getKeyword()) {
            case "enum":
                return ("Metadata " + Arrays.stream(problem.getPointer().split("/"))
                        .filter(StringUtils::isNotBlank)
                        .findFirst().get()
                        + " should be one of: "
                        + problem.parametersAsMap().get("expected")).replace("\"", "");
            case "required":
                return "Required Metadata "
                        + problem.parametersAsMap().get(problem.getKeyword())
                        .toString().replace("\"", "")
                        + " not set";
            case "type": {
                return ("Metadata " + Arrays.stream(problem.getPointer().split("/"))
                        .filter(StringUtils::isNotBlank)
                        .findFirst().get()
                        + " should be a/an "
                        + StringUtils.capitalize(((Enum) problem.parametersAsMap().get("expected")).name().toLowerCase())).replace("\"", "")
                        + " value";
            }
            default:
                return "";
        }
    }
}