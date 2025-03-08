// Copyright (c) 2025 WSO2 LLC. (http://www.wso2.org).
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/http;
import ballerina/test;
import ballerinax/azure.openai.chat as azureOpenAIChat;
import ballerinax/openai.chat as openAIChat;

final readonly & Blog blog1 = {
    // Generated.
    title: "Tips for Growing a Beautiful Garden",
    content: string `Spring is the perfect time to start your garden. 
        Begin by preparing your soil with organic compost and ensure proper drainage. 
        Choose plants suitable for your climate zone, and remember to water them regularly. 
        Don't forget to mulch to retain moisture and prevent weeds.`
};

final readonly & Blog blog2 = {
    // Generated.
    title: "Essential Tips for Sports Performance",
    content: string `Success in sports requires dedicated preparation and training.
        Begin by establishing a proper warm-up routine and maintaining good form.
        Choose the right equipment for your sport, and stay consistent with training.
        Don't forget to maintain proper hydration and nutrition for optimal performance.`
};

final readonly & Review review2 = {
    rating: 8,
    comment: "Talks about essential aspects of sports performance including warm-up, form, equipment, and nutrition."
};

@test:Config
function testPromptAsCodeFunctionWithSimpleExpectedTypeWithDefaultAzureOpenAIClient() returns error? {
    int rating = check callLlm(`Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}`);
    test:assertEquals(rating, 4);
}

@test:Config
function testPromptAsCodeFunctionWithStructuredExpectedTypeWithOpenAIClient() returns error? {
    Model model = check new OpenAIModel({
        connectionConfig: {
            auth: {token: "not-a-real-token"}
        },
        serviceUrl: "http://localhost:8080/llm/openai"
    }, "gpt4o");
    Review review = check callLlm(`Rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}`, model);
    test:assertEquals(review, review2);
}

type Blog record {
    string title;
    string content;
};

type Review record {|
    int rating;
    string comment;
|};

service /llm on new http:Listener(8080) {
    resource function post azureopenai/deployments/gpt4onew/chat/completions(
            string api\-version, azureOpenAIChat:CreateChatCompletionRequest payload)
                returns json {
        test:assertEquals(api\-version, "2023-08-01-preview");
        string expectedPromptString = string `Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"integer"}`;
        azureOpenAIChat:ChatCompletionRequestMessage[]? messages = payload.messages;
        if messages is () {
            test:assertFail("Expected messages in the payload");
        }
        azureOpenAIChat:ChatCompletionRequestMessage message = messages[0];
        test:assertEquals(message.role, "user");
        test:assertEquals(message["content"], expectedPromptString);
        return {
            'object: "chat.completion",
            created: 0,
            model: "",
            id: "",
            choices: [
                {
                    message: {
                        content: "4"
                    }
                }
            ]
        };
    }

    resource function post openai/chat/completions(openAIChat:CreateChatCompletionRequest payload)
            returns json {
        string expectedPromptString = string `Rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"$schema":"https://json-schema.org/draft/2020-12/schema", "type":"object", "properties":{"rating":{"type":"integer"}, "comment":{"type":"string"}}, "required":["rating", "comment"]}`;
        azureOpenAIChat:ChatCompletionRequestMessage message = payload.messages[0];
        test:assertEquals(message.role, "user");
        test:assertEquals(message["content"], expectedPromptString);

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
                        content: review2.toJsonString()
                    }
                }
            ]
        };
    }
}

