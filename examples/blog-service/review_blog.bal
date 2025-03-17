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

import ballerinax/np;

public isolated function reviewBlog(
    Blog blog,
    np:Prompt prompt = `You are an expert content reviewer for a blog site that 
        categorizes posts under the following categories: ${categories}

        Your tasks are:
        1. Suggest a suitable category for the blog from exactly the specified categories. 
           If there is no match, use null.

        2. Rate the blog post on a scale of 1 to 10 based on the following criteria:
        - **Relevance**: How well the content aligns with the chosen category.
        - **Depth**: The level of detail and insight in the content.
        - **Clarity**: How easy it is to read and understand.
        - **Originality**: Whether the content introduces fresh perspectives or ideas.
        - **Language Quality**: Grammar, spelling, and overall writing quality.

        Here is the blog post content:

        Title: ${blog.title}
        Content: ${blog.content}`) returns Review|error = @np:NaturalFunction external;
