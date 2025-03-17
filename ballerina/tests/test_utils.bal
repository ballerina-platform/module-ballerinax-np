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

    return "INVALID";
}
