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

import ballerinax/openai.chat as openAIChat;

# Configuration for OpenAI model.
public type OpenAIModelConfig record {|
    # Connection configuration for the OpenAI model.
    openAIChat:ConnectionConfig connectionConfig;
    # Service URL for the OpenAI model.
    string serviceUrl?;
|};

# OpenAI model chat completion client.
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

    isolated remote function call(string prompt, map<json> expectedResponseSchema) returns json|error {
        openAIChat:CreateChatCompletionRequest chatBody = {
            messages: [{role: "user", "content": getPromptWithExpectedResponseSchema(prompt, expectedResponseSchema)}],
            model: self.model
        };

        openAIChat:CreateChatCompletionResponse chatResult =
            check self.cl->/chat/completions.post(chatBody);
        openAIChat:CreateChatCompletionResponse_choices[] choices = chatResult.choices;

        string? resp = choices[0].message?.content;
        if resp is () {
            return error("No completion message");
        }
        return parseResponseAsJson(resp);
    }
}
