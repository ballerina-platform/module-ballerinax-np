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

const UNAUTHORIZED = 401;

# Configuration for the default Ballerina model.
public type DefaultBallerinaModelConfig record {|
    # LLM service URL
    string url;
    # Access token
    string accessToken;
|};

# Default Ballerina model chat completion client.
public isolated distinct client class DefaultBallerinaModel {
    *Model;

    private final http:Client cl;

    public isolated function init(DefaultBallerinaModelConfig config) returns error? {
        var {url, accessToken} = config;

        self.cl = check new (url, {
            auth: {
                token: accessToken
            }
        });
    }

    isolated remote function call(string prompt, map<json> expectedResponseSchema) returns json|error {
        http:Client cl = self.cl;
        http:Response chatResponse = 
            check cl->/chat/complete.post(getPromptWithExpectedResponseSchema(prompt, expectedResponseSchema));
        int statusCode = chatResponse.statusCode;
        if statusCode == UNAUTHORIZED {
            return error("The default Ballerina model is being used. The token has expired and needs to be regenerated.");
        }

        if !(statusCode >= 200 && statusCode < 300) {
            return error(string `LLM call failed: ${check chatResponse.getTextPayload()}`);
        }

        string|error resp = check chatResponse.getTextPayload();
        if resp is error {
            return error("Failed to retrieve completion message", resp);
        }
        return parseResponseAsJson(resp);
    }
}
