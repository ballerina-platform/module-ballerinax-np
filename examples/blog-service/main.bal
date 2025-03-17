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
import ballerina/log;
import ballerinax/mysql;
import ballerinax/mysql.driver as _;

type DbConfig record {|
    string host;
    string user;
    string password;
    string database;
    int port = 3306;
|};

configurable DbConfig dbConfig = ?;
configurable string apiKey = ?;
configurable string serviceUrl = ?;
configurable string deploymentId = ?;
configurable string apiVersion = ?;

final mysql:Client db = check new (...dbConfig);

final readonly & string[] categories = [
    "Tech Innovations & Software Development",
    "Programming Languages & Frameworks",
    "DevOps, Cloud Computing & Automation",
    "Career Growth in Tech",
    "Open Source & Community-Driven Development"
];

service on new http:Listener(8080) {

    resource function post blog(Blog blog) returns http:Created|http:BadRequest|http:InternalServerError {
        do {
            Blog {title, content} = blog;
            Review {suggestedCategory, rating} = check reviewBlog(blog);

            if suggestedCategory is () || rating < 4 {
                return <http:BadRequest>{
                    body: "Blog rejected due to low rating or no matching category"};
            }

            _ = check db->execute(`INSERT INTO Blog (title, content, rating, category) VALUES (${
                                            title}, ${content}, ${rating}, ${suggestedCategory})`);
            return <http:Created> {body: "Blog accepted"};            
        } on fail error e {
            log:printError("Blog submission failed", e);
            return <http:InternalServerError>{body: "Blog submission failed"};
        }
    }

    resource function get blogs/[string category]() returns Blog[]|http:InternalServerError {
        do {
            stream<BlogRecord, error?> result = 
                db->query(
                    `SELECT title, content, rating, category FROM Blog WHERE category = ${
                        category} ORDER BY rating DESC LIMIT 10`);
            return check from BlogRecord blog in result select {title: blog.title, content: blog.content};
        } on fail {
            return <http:InternalServerError>{body: "Failed to retrieve blogs"};
        }
    }    
}
