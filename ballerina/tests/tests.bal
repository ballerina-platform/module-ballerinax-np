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

import np.azure.openai as azureOpenAI;
import np.openai;
import ballerina/np;
import ballerina/test;

const ERROR_MESSAGE = "Error occurred while attempting to parse the response from the LLM as the expected type. Retrying and/or validating the prompt could fix the response.";

final np:ModelProvider azureOpenAI = check new azureOpenAI:ModelProvider({
    serviceUrl: "http://localhost:8080/llm/azureopenai",
    connectionConfig: {
        auth: {
            apiKey: "not-a-real-api-key"
        }
    }
}, "gpt4onew", "2023-08-01-preview");

@test:Config
function testPromptAsCodeFunctionWithSimpleExpectedTypeWithDefaultAzureOpenAIClient() returns error? {
    int rating = check np:callLlm(`Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}`, {model: azureOpenAI});
    test:assertEquals(rating, 4);
}

@test:Config
function testPromptAsCodeFunctionWithStructuredExpectedTypeWithOpenAIClient() returns error? {
    np:ModelProvider model = check new openai:ModelProvider({
        connectionConfig: {
            auth: {token: "not-a-real-token"}
        },
        serviceUrl: "http://localhost:8080/llm/openai"
    }, "gpt4o");
    Review review = check np:callLlm(`Please rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}`, {model});
    test:assertEquals(review, review2);
}

@test:Config
function testJsonConversionError() {
    boolean|error rating = np:callLlm(`What is 1 + 1?`, {model: azureOpenAI});
    test:assertTrue(rating is error);
    test:assertTrue((<error> rating).message().includes(ERROR_MESSAGE));
}

type RecordForInvalidBinding record {| string name; |};
@test:Config
function testJsonConversionError2() {
    RecordForInvalidBinding[]|error rating = np:callLlm(
        `Tell me name and the age of the top 10 world class cricketers`, {model: azureOpenAI});
    test:assertTrue(rating is error);
    test:assertTrue((<error> rating).message().includes(ERROR_MESSAGE));
}

@test:Config
function testJsonContentAfterTextDescription() returns error? {
    int result = check np:callLlm(`What's the output of the Ballerina code below?

    ${"```"}ballerina
    import ballerina/io;

    public function main() {
        int x = 10;
        int y = 20;
        io:println(x + y);
    \}
    ${"```"}`, 
    {model: azureOpenAI});
    test:assertEquals(result, 30);
}

@test:Config
function testJsonContentWithoutJsonAfterBackticks() returns error? {
    string result = check np:callLlm(
        `Which country is known as the pearl of the Indian Ocean?`, {model: azureOpenAI});
    test:assertEquals(result, "Sri Lanka");
}
