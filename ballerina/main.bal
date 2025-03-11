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

type DefaultModelConfig DefaultAzureOpenAIModelConfig|DefaultOpenAIModelConfig|DefaultBallerinaAzureOpenAIModelConfig;

type DefaultAzureOpenAIModelConfig record {|
    *AzureOpenAIModelConfig;
    string deploymentId;
    string apiVersion;
|};

type DefaultOpenAIModelConfig record {|
    *OpenAIModelConfig;
    string model;
|};

public annotation map<json> Schema on type;

final Model? defaultModel;

function init() returns error? {
    DefaultModelConfig? defaultModelConfigVar = defaultModelConfig;
    if defaultModelConfigVar is () {
        defaultModel = ();
        return;
    }

    if defaultModelConfigVar is DefaultAzureOpenAIModelConfig {
        defaultModel = check new AzureOpenAIModel({
            connectionConfig: defaultModelConfigVar.connectionConfig,
            serviceUrl: defaultModelConfigVar.serviceUrl
        }, defaultModelConfigVar.deploymentId, defaultModelConfigVar.apiVersion);
        return;
    }

    if defaultModelConfigVar is DefaultOpenAIModelConfig {
        string? serviceUrl = defaultModelConfigVar?.serviceUrl;
        defaultModel = serviceUrl is () ?
            check new OpenAIModel({
                connectionConfig: defaultModelConfigVar.connectionConfig
            }, defaultModelConfigVar.model) :
            check new OpenAIModel({
                connectionConfig: defaultModelConfigVar.connectionConfig,
                serviceUrl
            }, defaultModelConfigVar.model);
        return;
    }

    defaultModel = check new DefaultBallerinaAzureOpenAIModel(defaultModelConfigVar);
}

isolated function getDefaultModel() returns Model {
    final Model? defaultModelVar = defaultModel;
    if defaultModelVar is () {
        panic error("Default model is not initialized");
    }
    return defaultModelVar;
}

isolated function buildPromptString(Prompt prompt, typedesc<json> td) returns string {
    string str = prompt.strings[0];
    anydata[] insertions = prompt.insertions;
    foreach int i in 0 ..< insertions.length() {
        str = str + insertions[i].toString() + prompt.strings[i + 1];
    }

    map<json>? ann = td.@Schema;
    string schema = ann is () ? generateJsonSchemaForTypedescAsString(td) : ann.toJsonString();
    return string `${str}.  
        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        ${schema}`;
}

isolated function callLlmBal(Prompt prompt, Context context, typedesc<json> td) returns json|error {
    Model model = context.model;
    string resp = check model->call(prompt, td);
    return parseResponse(resp, td);
}

isolated function parseResponse(string resp, typedesc<json> td) returns json|error {
    string processedResponse = re `${"```json|```"}`.replaceAll(resp, "");
    return processedResponse.fromJsonStringWithType(td);
}
