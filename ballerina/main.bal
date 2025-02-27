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
import ballerinax/azure.openai.chat as azureOpenAIChat;

final azureOpenAIChat:Client|openAIChat:Client? chatClient;
final (isolated function (Prompt, typedesc<anydata>) returns anydata|error)? llmFunc;

function init() returns error? {
    AzureOpenAIClientConfig|OpenAIClientConfig? llmClientConfigVar = llmClientConfig;
    if llmClientConfigVar is () {
        chatClient = ();
        llmFunc = ();
        return;
    }

    if llmClientConfigVar is AzureOpenAIClientConfig {
        AzureOpenAIClientConfig {serviceUrl, connectionConfig} = llmClientConfigVar;
        chatClient = check new azureOpenAIChat:Client(connectionConfig, serviceUrl);
        llmFunc = callAzureOpenAI;
        return;
    }

    // OpenAIClientConfig
    OpenAIClientConfig {serviceUrl, connectionConfig} = llmClientConfigVar;
    chatClient = check (serviceUrl is () ? 
                            new openAIChat:Client(connectionConfig) : 
                            new openAIChat:Client(connectionConfig, serviceUrl));
    llmFunc = callOpenAI;
    return;
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
        ${generateJsonSchemaForTypedescAsString(td)}`;
}

isolated function callLlmBal(Prompt prompt, typedesc<anydata> td) returns anydata|error {
    (isolated function (Prompt, typedesc<anydata>) returns anydata|error)? llmFuncVar = llmFunc;
    if llmFuncVar is () {
        panic error("LLM configuration is not provided to use with LLM calls");
    }
    return llmFuncVar(prompt, td);
}
