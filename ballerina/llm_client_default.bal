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

# Configuration for the default Azure OpenAI model.
public type DefaultBallerinaAzureOpenAIModelConfig record {|
    # LLM service URL
    string url;
    # Access token
    string accessToken;
|};

# Default Azure OpenAI model chat completion client.
public isolated distinct client class DefaultBallerinaAzureOpenAIModel {
    *Model;

    private final http:Client cl;

    public isolated function init(DefaultBallerinaAzureOpenAIModelConfig config) returns error? {
        var {url, accessToken} = config;

        self.cl = check new (url, {
            auth: {
                token: accessToken
            }
        });
    }

    isolated remote function call(Prompt prompt, typedesc<json> td) returns string|error {
        chat:CreateChatCompletionRequest chatBody = {
            messages: [{role: "user", "content": buildPromptString(prompt, td)}]
        };

        http:Client cl = self.cl;
        chat:CreateChatCompletionResponse chatResult = check cl->/chat/complete.post(chatBody);
        record {
            chat:ChatCompletionResponseMessage message?;
            chat:ContentFilterChoiceResults content_filter_results?;
            int index?;
            string finish_reason?;
        }[]? choices = chatResult.choices;

        if choices is () {
            return error("No completion choices");
        }

        string? resp = choices[0].message?.content;
        if resp is () {
            return error("No completion message");
        }
        return resp;
    }
}
