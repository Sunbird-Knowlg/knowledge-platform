package org.sunbird.schema.impl;

import org.leadpony.justify.api.Problem;
import org.leadpony.justify.api.ProblemHandler;

import java.util.ArrayList;
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
                .map(problem -> "Metadata " + problem.getPointer().replace("/", "") + " should " + problem.getMessage().substring(0, problem.getMessage().length() - 1).replace("The value must", "").replace("\"", ""))
                .collect(Collectors.toList());
        Collections.reverse(tempMessageList);
        this.messages.addAll(tempMessageList);
    }

    protected List<String> getProblemMessages() {
        return this.messages;
    }
}