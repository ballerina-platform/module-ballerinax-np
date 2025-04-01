isolated function getExpectedPrompt(string message) returns string {
    if message.startsWith("Rate this blog") {
        return expectedPromptStringForRateBlog;
    }

    if message.startsWith("Please rate this blog") {
        return expectedPromptStringForRateBlog2;
    }

    if message.startsWith("What is 1 + 1?") {
        return expectedPromptStringForRateBlog3;
    }

    if message.startsWith("Tell me") {
        return expectedPromptStringForRateBlog4;
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return expectedPromptStringForBalProgram;
    }

    if message.startsWith("Which country") {
        return expectedPromptStringForCountry;
    }

    return "INVALID";
}

isolated function getTheMockLLMResult(string message) returns string {
    if message.startsWith("Rate this blog") {
        return "4";
    }

    if message.startsWith("Please rate this blog") {
        return review2.toJsonString();
    }

    if message.startsWith("What is 1 + 1?") {
        return "2";
    }

    if message.startsWith("Tell me") {
        return "[{\"name\":\"Virat Kohli\",\"age\":33},{\"name\":\"Kane Williamson\",\"age\":30}";
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return string `The output of the provided Ballerina code calculates the sum of ${"`"}x${"`"} and ${"`"}y${"`"}, which is ${"`"}10 + 20${"`"}. Therefore, the result will be ${"`"}30${"`"}. \n\nHere is the output formatted as a JSON value that satisfies your specified schema:${"\n\n```"}json${"\n"}30${"\n```"}`;
    }

    if message.startsWith("Which country") {
        return "```\n\"Sri Lanka\"\n```";
    }

    return "INVALID";
}
