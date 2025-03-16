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

const JSON_CONVERSION_ERROR = "FromJsonStringError";
const ERROR_MESSAGE = "Error occurred while converting the LLM response to the given type. Please refined your prompt to get a better result.";

isolated function handleClientError(error chatResponseError) returns error {
    if (chatResponseError.message().includes(JSON_CONVERSION_ERROR)) {
        return error(string `${ERROR_MESSAGE}`);
    }

    return chatResponseError;
}
