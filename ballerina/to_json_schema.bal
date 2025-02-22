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

type JsonSchema record {|
    string \$schema?;
    string|string[] 'type;
    map<JsonSchema> properties?;
    string[] required?;
|};

type JsonArraySchema record {|
    string \$schema;
    string 'type = "array";
    JsonSchema items;
|};

isolated function generateJsonSchemaForTypedesc(typedesc<anydata> td) returns string {
    if isSimpleType(td) {
        return (<JsonSchema> {
            'type: getStringRepresentation(<typedesc<json>>td)
        }).toJsonString();
    }

    boolean isArray = td is typedesc<anydata[]>;

    typedesc<record {}> recTd;

    if isArray {
        typedesc<anydata> arrayMemberType = getArrayMemberType(<typedesc<anydata[]>>td);
        if isSimpleType(arrayMemberType) {
            return (<JsonArraySchema> {
                \$schema: "https://json-schema.org/draft/2020-12/schema",
                items: {
                    'type: getStringRepresentation(<typedesc<json>>arrayMemberType)
                }
            }).toJsonString();
        }
        recTd = <typedesc<record {}>>arrayMemberType;
    } else {
        recTd = <typedesc<record {}>>td;
    }

    string[] names = [];
    boolean[] required = [];
    typedesc<json>[] types = [];
    boolean[] nilable = [];
    populateFieldInfo(recTd, names, required, types, nilable);
    return generateJsonSchema(names, required, types, nilable, isArray).toJsonString();
}

isolated function populateFieldInfo(typedesc<anydata> td, string[] names, boolean[] required,
        typedesc<json>[] types, boolean[] nilable) = @java:Method {
    name: "populateFieldInfo",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function getArrayMemberType(typedesc<anydata> td) returns typedesc<anydata> = @java:Method {
    name: "getArrayMemberType",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function generateJsonSchema(string[] names, boolean[] required,
        typedesc<json>[] types, boolean[] nilable, boolean isArray) returns JsonSchema|JsonArraySchema {
    map<JsonSchema> properties = {};
    string[] requiredSchema = [];

    JsonSchema schema = {
        \$schema: "https://json-schema.org/draft/2020-12/schema",
        'type: "object",
        properties,
        required: requiredSchema
    };

    foreach int i in 0 ..< names.length() {
        string fieldName = names[i];
        JsonSchema fieldSchema = getJsonSchemaType(types[i], nilable[i]);
        properties[fieldName] = fieldSchema;
        if required[i] {
            requiredSchema.push(fieldName);
        }
    }

    if isArray {
        return {
            \$schema: "https://json-schema.org/draft/2020-12/schema",
            items: schema
        };
    }

    return schema;
}

isolated function getJsonSchemaType(typedesc<json> fieldType, boolean nilable) returns JsonSchema {
    if nilable {
        return {
            'type: [getStringRepresentation(fieldType), "null"]
        };
    }
    return {
        'type: getStringRepresentation(fieldType)
    };
}

isolated function isSimpleType(typedesc<anydata> td) returns boolean =>
    td is typedesc<string|int|float|decimal|boolean|()>;

isolated function getStringRepresentation(typedesc<json> fieldType) returns string {
    if fieldType is typedesc<()> {
        return "null";
    }
    if fieldType is typedesc<string> {
        return "string";
    }
    if fieldType is typedesc<int> {
        return "integer";
    }
    if fieldType is typedesc<float|decimal> {
        return "number";
    }
    if fieldType is typedesc<boolean> {
        return "boolean";
    }

    panic error("unimplemented");
}
