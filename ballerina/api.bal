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

import ballerina/jballerina.java;

type AzureOpenAIClientConfig record {|
    string serviceUrl;
    string apiKey;
    string deploymentId;
    string apiVersion;
|};

configurable AzureOpenAIClientConfig? azureOpenAIClientConfig = ();

public type Prompt object {
    *object:RawTemplate;
    public string[] & readonly strings;
    public anydata[] insertions;
};

public isolated function call(Prompt prompt, typedesc<anydata> td = <>) 
        returns td|error = @java:Method {
    name: "callLlmCallBallerinaFunction",
    'class: "io.ballerina.lib.np.Native"
} external;

public const annotation LlmCall on source external;
