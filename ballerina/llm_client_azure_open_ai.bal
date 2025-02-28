import ballerinax/azure.openai.chat;

public type AzureOpenAIModelConfig record {|
    chat:ConnectionConfig connectionConfig;
    string serviceUrl;
|};

public isolated distinct client class AzureOpenAIModel {
    *Model;

   private final chat:Client cl;
   private final string deploymentId;
   private final string apiVersion;

   public isolated function init(chat:Client|AzureOpenAIModelConfig azureOpenAI,
                        string deploymentId,
                        string apiVersion) returns error? {
       self.cl = azureOpenAI is chat:Client ? 
                    azureOpenAI :
                    check new (azureOpenAI.connectionConfig, azureOpenAI.serviceUrl);
       self.deploymentId = deploymentId;
       self.apiVersion = apiVersion;
   }


    isolated remote function call(Prompt prompt, typedesc<anydata> td) returns string|error {
        chat:CreateChatCompletionRequest chatBody = {
            messages: [{role: "user", "content": buildPromptString(prompt, td)}]
        };

        chat:Client cl = self.cl;
        chat:CreateChatCompletionResponse chatResult =
            check cl->/deployments/[self.deploymentId]/chat/completions.post(self.apiVersion, chatBody);
        record {
            chat:ChatCompletionResponseMessage message?;
            chat:ContentFilterChoiceResults content_filter_results?;
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
        return resp;
    }
}
