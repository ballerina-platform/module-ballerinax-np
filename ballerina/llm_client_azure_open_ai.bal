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
import ballerinax/azure.openai.chat;

# Configuration for Azure OpenAI model.
public type AzureOpenAIModelConfig record {|
    # Connection configuration for the Azure OpenAI model.
    chat:ConnectionConfig connectionConfig;
    # Service URL for the Azure OpenAI model.
    string serviceUrl;
|};

type ChatCompletionAzureRequest record {
    record {
        "user"|"system" role;
        string content;
    }[] messages = [];
    AzureOpenAIResponseFormat response_format;
};

type ChatCompletionRequest record {
    string prompt;
    AzureOpenAIResponseFormat responseFormat;
};

type AzureOpenAIResponseFormat ResponseFormatJsonSchema;

type ResponseFormatJsonSchema record {|
    "json_schema" 'type;
    ResponseFormatJsonSchemaValue json_schema;
|};

type ResponseFormatJsonSchemaValue record {|
    string description?;
    string name;
    json schema?;
    boolean? strict = true;
|};

type ChatCompletionAzureResponse record {
    AzureOpenAIResponseMessage[] choices?;
};

type AzureOpenAIResponseMessage record {
    record {
        string? content;
    } message;
};

# Azure OpenAI model chat completion client.
public isolated distinct client class AzureOpenAIModel {
    *Model;

    private final string deploymentId;
    private final string apiVersion;
    private final string apiKey;
    private final http:Client azureOpenAIClient;

    public isolated function init(AzureOpenAIModelConfig azureOpenAI,
            string deploymentId,
            string apiVersion) returns error? {
        self.deploymentId = deploymentId;
        self.apiVersion = apiVersion;
        http:BearerTokenConfig|chat:ApiKeysConfig auth = azureOpenAI.connectionConfig.auth;

        if auth is http:BearerTokenConfig {
            self.apiKey = auth.token;
        } else {
            self.apiKey = auth.apiKey;
        }

        self.azureOpenAIClient = check generateHttpClientFromAzureOpenAIModelConfig(azureOpenAI);
    }

    isolated remote function call(string prompt, map<json> expectedResponseSchema) returns json|error {
        ChatCompletionAzureRequest chatBody = {
            messages: [{role: "user", "content": getPromptWithExpectedResponseSchema(prompt, expectedResponseSchema)}],
            response_format: check getJsonSchemaResponseFormatForAzureOpenAI(expectedResponseSchema)
        };

        string resourcePath = string
            `/deployments/${getEncodedUri(self.deploymentId)}/chat/completions?api-version=${self.apiVersion}`;
        http:Request request = new;
        request.setPayload(chatBody);

        ChatCompletionAzureResponse|error chatResult = self.azureOpenAIClient->post(
                                            resourcePath, request, {api\-key: self.apiKey});

        if chatResult is error {
            return error("Chat completion failed");
        }

        AzureOpenAIResponseMessage[]? choices = chatResult.choices;

        if choices is () {
            return error("No completion message");
        }

        string? resp = choices[0].message?.content;
        if resp is () {
            return error("No completion message");
        }
        return parseResponseAsJson(resp);
    }
}

isolated function getJsonSchemaResponseFormatForAzureOpenAI(map<json> schema) returns AzureOpenAIResponseFormat|error {
    return getJsonSchemaResponseFormatForModel(schema).cloneWithType();
}

isolated function generateHttpClientFromAzureOpenAIModelConfig(AzureOpenAIModelConfig azureOpenAI) 
        returns http:Client|error {
    chat:ConnectionConfig config = azureOpenAI.connectionConfig;
    http:ClientConfiguration httpClientConfig = {
        httpVersion: config.httpVersion, timeout: config.timeout, 
        forwarded: config.forwarded, poolConfig: config.poolConfig, 
        compression: config.compression, circuitBreaker: config.circuitBreaker, 
        retryConfig: config.retryConfig, validation: config.validation
    };

    if config.http1Settings is chat:ClientHttp1Settings {
        chat:ClientHttp1Settings settings = check config.http1Settings.ensureType(chat:ClientHttp1Settings);
        httpClientConfig.http1Settings = {...settings};
    }
    if config.http2Settings is http:ClientHttp2Settings {
        httpClientConfig.http2Settings = check config.http2Settings.ensureType(http:ClientHttp2Settings);
    }
    if config.cache is http:CacheConfig {
        httpClientConfig.cache = check config.cache.ensureType(http:CacheConfig);
    }
    if config.responseLimits is http:ResponseLimitConfigs {
        httpClientConfig.responseLimits = check config.responseLimits.ensureType(http:ResponseLimitConfigs);
    }
    if config.secureSocket is http:ClientSecureSocket {
        httpClientConfig.secureSocket = check config.secureSocket.ensureType(http:ClientSecureSocket);
    }
    if config.proxy is http:ProxyConfig {
        httpClientConfig.proxy = check config.proxy.ensureType(http:ProxyConfig);
    }

    return new (azureOpenAI.serviceUrl, httpClientConfig);
}
