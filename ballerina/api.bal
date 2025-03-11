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

# Configuration for the model to default to if not explicitly
# specified in the call to function `callLlm` or an external
# function annotated with annotation `NaturalFunction`.
configurable DefaultModelConfig? defaultModelConfig = ();

# Raw template type for prompts.
public type Prompt object {
    *object:RawTemplate;

    # The fixed string parts of the template.
    public string[] & readonly strings;

    # The interpolations in the template.
    public anydata[] insertions;
};

# Calls a Large Language Model (LLM) with a given prompt and context and returns 
# the response parsed as the expected type.
#
# + prompt - The prompt to send to the LLM
# + context - The context to use, including the LLM to use
# + targetType - The expected response type. The JSON schema corresponding to this type 
#  is generated to inlcude in the request to the LLM
# + return - The LLM response parsed according to the specified type, or an error if the call 
# fails or parsing fails
public isolated function callLlm(Prompt prompt, Context context = {}, typedesc<json> targetType = <>)
        returns targetType|error = @java:Method {
    'class: "io.ballerina.lib.np.Native"
} external;

# Annotation to indicate that the implementation of a function should be
# a call to an LLM with the prompt specified as a parameter and using the 
# return type as the schema for the expected response.
# If function has a `context` parameter, the model specified in the 
# context will be used as the model to call.
# If not, defaults to the default model configured via the configurable 
# variable `defaultModelConfig`
public const annotation NaturalFunction on source external;

# Context for Large Language Model (LLM) usage.
public type Context record {|
    # The model to use
    Model model = getDefaultModel();
|};

# Abstraction for a Large Language Model (LLM), with chat/completion functionality.
public type Model distinct isolated client object {

    # Makes a call to the Large Language Model (LLM) with the given prompt and returns the result.
    #
    # + prompt - The prompt to be sent to the LLM
    # + expectedResponseSchema - The schema for the expected response from the LLM 
    # + return - The JSON value extracted/parsed from the LLM's response or an error if the call fails
    isolated remote function call(string prompt, map<json> expectedResponseSchema) returns json|error;
};
