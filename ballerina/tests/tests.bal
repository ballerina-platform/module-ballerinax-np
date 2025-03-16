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

import ballerina/test;

const ERROR_MESSAGE = "Error occurred while converting the LLM response to the given type. Please refined your prompt to get a better result.";

@test:Config
function testPromptAsCodeFunctionWithSimpleExpectedTypeWithDefaultAzureOpenAIClient() returns error? {
    int rating = check callLlm(`Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}`);
    test:assertEquals(rating, 4);
}

@test:Config
function testPromptAsCodeFunctionWithStructuredExpectedTypeWithOpenAIClient() returns error? {
    Model model = check new OpenAIModel({
        connectionConfig: {
            auth: {token: "not-a-real-token"}
        },
        serviceUrl: "http://localhost:8080/llm/openai"
    }, "gpt4o");
    Review review = check callLlm(`Please rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}`, {model});
    test:assertEquals(review, review2);
}

@test:Config
function testJsonConversionError() {
    boolean|error rating = callLlm(`What is 1 + 1?`);
    test:assertTrue(rating is error);
    test:assertTrue((<error> rating).message().includes(ERROR_MESSAGE));
}

@test:Config
function testJsonConversionError2() {
    record{|
        string name;
    |}[]|error rating = callLlm(`Tell me name and the age of the top 10 world class cricketers`);
    test:assertTrue(rating is error);
    test:assertTrue((<error> rating).message().includes(ERROR_MESSAGE));
}
