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

import ballerinax/azure.openai.chat as azureChat;

final azureChat:Client? chatClient;

function init() returns error? {
    if azureOpenAIClientConfig is () {
        return ();
    }

    AzureOpenAIClientConfig config = <AzureOpenAIClientConfig>azureOpenAIClientConfig;
    AzureOpenAIClientConfig {serviceUrl, apiKey} = config;
    chatClient = check new (config = {auth: {apiKey: apiKey}}, serviceUrl = serviceUrl);
}

isolated function buildPromptString(Prompt prompt, typedesc<anydata> td) returns string {
    string str = prompt.strings[0];
    anydata[] insertions = prompt.insertions;
    foreach int i in 0 ..< insertions.length() {
        str = str + insertions[i].toString() + prompt.strings[i + 1];
    }
    return string `${str}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        ${generateJsonSchemaForTypedesc(td)}`;
}

isolated function callLlm(Prompt prompt, typedesc<anydata> td) returns anydata|error {
    azureChat:Client azureClient = check chatClient.ensureType();
    AzureOpenAIClientConfig {deploymentId, apiVersion} = check azureOpenAIClientConfig.ensureType();

    azureChat:CreateChatCompletionRequest chatBody = {
        messages: [{role: "user", "content": buildPromptString(prompt, td)}]
    };

    azureChat:CreateChatCompletionResponse chatResult =
        check azureClient->/deployments/[deploymentId]/chat/completions.post(apiVersion, chatBody);
    record {
        azureChat:ChatCompletionResponseMessage message?;
        azureChat:ContentFilterChoiceResults content_filter_results?;
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
