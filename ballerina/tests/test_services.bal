import ballerina/http;
import ballerinax/azure.openai.chat as azureOpenAIChat;
import ballerinax/openai.chat as openAIChat;
import ballerina/test;

service /llm on new http:Listener(8080) {
    resource function post azureopenai/deployments/gpt4onew/chat/completions(
            string api\-version, azureOpenAIChat:CreateChatCompletionRequest payload)
                returns json|error {
        test:assertEquals(api\-version, "2023-08-01-preview");
        azureOpenAIChat:ChatCompletionRequestMessage[]? messages = payload.messages;
        if messages is () {
            test:assertFail("Expected messages in the payload");
        }
        azureOpenAIChat:ChatCompletionRequestMessage message = messages[0];
        anydata content = message["content"];
        string contentStr = content.toString();
        test:assertEquals(message.role, "user");
        test:assertEquals(content, getExpectedPrompt(contentStr));
        return {
            'object: "chat.completion",
            created: 0,
            model: "",
            id: "",
            choices: [
                {
                    message: {
                        content: getTheMockLLMResult(contentStr)
                    }
                }
            ]
        };
    }

    resource function post openai/chat/completions(openAIChat:CreateChatCompletionRequest payload)
            returns json|error {

        azureOpenAIChat:ChatCompletionRequestMessage message = payload.messages[0];
        anydata content = message["content"];
        string contentStr = content.toString();
        test:assertEquals(message.role, "user");
        test:assertEquals(content, getExpectedPrompt(content.toString()));

        test:assertEquals(payload.model, "gpt4o");
        return {
            'object: "chat.completion",
            created: 0,
            model: "",
            id: "",
            choices: [
                {
                    finish_reason: "stop",
                    index: 0,
                    logprobs: (),
                    message: {
                        role: "assistant",
                        content: getTheMockLLMResult(contentStr),
                        refusal: ()
                    }
                }
            ]
        };
    }
}
