import ballerinax/openai.chat as openAIChat;

type OpenAIClientConfig record {|
    openAIChat:ConnectionConfig connectionConfig;
    string serviceUrl?;
    string model;
|};

isolated function callOpenAI(Prompt prompt, typedesc<anydata> td) returns anydata|error {
    openAIChat:Client openAIClient = <openAIChat:Client> chatClient;
    OpenAIClientConfig {model} = <OpenAIClientConfig> llmClientConfig;

    openAIChat:CreateChatCompletionRequest chatBody = {
        messages: [{role: "user", "content": buildPromptString(prompt, td)}],
        model
    };

    openAIChat:CreateChatCompletionResponse chatResult = check openAIClient->/chat/completions.post(chatBody);
    openAIChat:CreateChatCompletionResponse_choices[] choices = chatResult.choices;

    string? resp = choices[0].message?.content;
    if resp is () {
        return error("No completion found");
    }

    string processedResponse = re `${"```json|```"}`.replaceAll(resp, "");
    return processedResponse.fromJsonStringWithType(td);
}
