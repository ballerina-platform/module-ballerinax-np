import ballerinax/azure.openai.chat as azureOpenAIChat;

type AzureOpenAIClientConfig record {|
    azureOpenAIChat:ConnectionConfig connectionConfig;
    string serviceUrl;
    string deploymentId;
    string apiVersion;
|};

isolated function callAzureOpenAI(Prompt prompt, typedesc<anydata> td) returns anydata|error {
    azureOpenAIChat:Client azureOpenAIClient = <azureOpenAIChat:Client> chatClient;
    AzureOpenAIClientConfig {deploymentId, apiVersion} = <AzureOpenAIClientConfig> llmClientConfig;

    azureOpenAIChat:CreateChatCompletionRequest chatBody = {
        messages: [{role: "user", "content": buildPromptString(prompt, td)}]
    };

    azureOpenAIChat:CreateChatCompletionResponse chatResult =
        check azureOpenAIClient->/deployments/[deploymentId]/chat/completions.post(apiVersion, chatBody);
    record {
        azureOpenAIChat:ChatCompletionResponseMessage message?;
        azureOpenAIChat:ContentFilterChoiceResults content_filter_results?;
        int index?;
        string finish_reason?;
    }[]? choices = chatResult.choices;

    if choices is () {
        return error("No completion found");
    }

    string? resp = choices[0].message?.content;
    if resp is () {
        return error("No completion found");
    }

    string processedResponse = re `${"```json|```"}`.replaceAll(resp, "");
    return processedResponse.fromJsonStringWithType(td);
}
