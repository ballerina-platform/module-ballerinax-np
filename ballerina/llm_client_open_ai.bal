import ballerinax/openai.chat as openAIChat;

public type OpenAIModelConfig record {|
    openAIChat:ConnectionConfig connectionConfig;
    string serviceUrl?;
|};

public isolated distinct client class OpenAIModel {
    *Model;

    private final openAIChat:Client cl;
    private final string model;

    public isolated function init(openAIChat:Client|OpenAIModelConfig openAI, string model) returns error? {
        self.cl = openAI is openAIChat:Client ?
            openAI :
            let string? serviceUrl = openAI?.serviceUrl in
                    serviceUrl is () ?
                    check new (openAI.connectionConfig) :
                    check new (openAI.connectionConfig, serviceUrl);
        self.model = model;
    }

    isolated remote function call(Prompt prompt, typedesc<anydata> td) returns string|error {
        openAIChat:CreateChatCompletionRequest chatBody = {
            messages: [{role: "user", "content": buildPromptString(prompt, td)}],
            model: self.model
        };

        openAIChat:CreateChatCompletionResponse chatResult =
            check self.cl->/chat/completions.post(chatBody);
        openAIChat:CreateChatCompletionResponse_choices[] choices = chatResult.choices;

        string? resp = choices[0].message?.content;
        if resp is () {
            return error("No completion found");
        }
        return resp;
    }
}
