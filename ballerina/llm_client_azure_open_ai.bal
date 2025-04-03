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

import ballerinax/azure.openai.chat;

# Configuration for Azure OpenAI model.
public type AzureOpenAIModelConfig record {|
    # Connection configuration for the Azure OpenAI model.
    chat:ConnectionConfig connectionConfig;
    # Service URL for the Azure OpenAI model.
    string serviceUrl;
|};

type AzureOpenAIResponseFormat chat:ResponseFormatText
            |chat:ResponseFormatJsonObject|chat:ResponseFormatJsonSchema;

# Azure OpenAI model chat completion client.
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

    isolated remote function call(string prompt, map<json> expectedResponseSchema) returns json|error {
        AzureOpenAIResponseFormat responseFormat = check getJsonSchemaResponseTypeForAzureOpenAI(expectedResponseSchema);
        chat:CreateChatCompletionRequest chatBody = {
            messages: [{role: "user", "content": getPromptWithExpectedResponseSchema(prompt, expectedResponseSchema)}],
            response_format: responseFormat
        };

        chat:Client cl = self.cl;
        chat:inline_response_200_1 chatResult =
            check cl->/deployments/[self.deploymentId]/chat/completions.post(chatBody, {}, api\-version = self.apiVersion);
        
        if chatResult is chat:createChatCompletionStreamResponse {
            return error("Invalid completion choices");
        }

        chat:CreateChatCompletionResponse chatCompletionResponse = <chat:CreateChatCompletionResponse>chatResult;

        chat:CreateChatCompletionResponse_choices[] choices = chatCompletionResponse.choices;

        string? resp = choices[0].message?.content;
        if resp is () {
            return error("No completion message");
        }
        return parseResponseAsJson(resp);
    }
}

isolated function getJsonSchemaResponseTypeForAzureOpenAI(map<json> schema) returns AzureOpenAIResponseFormat|error {
    return check getJsonSchemaResponseTypeForModel(schema).cloneWithType();
}
