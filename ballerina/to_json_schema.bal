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
    map<JsonSchema|JsonArraySchema|map<json>> properties?;
    string[] required?;
|};

type JsonArraySchema record {|
    string \$schema;
    string|string[] 'type = "array";
    JsonSchema items;
|};

isolated function generateJsonSchemaForTypedescAsJson(typedesc<json> targetType) returns map<json> =>
    let map<json>? ann = targetType.@Schema in 
        ann ?: generateJsonSchemaForTypedesc(targetType, containsNil(targetType));

isolated function generateJsonSchemaForTypedesc(typedesc<json> targetType, boolean nilableType) returns JsonSchema|JsonArraySchema|map<json> {
    if isSimpleType(targetType) {
        return <JsonSchema>{
            'type: getStringRepresentation(<typedesc<json>>targetType)
        };
    }

    boolean isArray = targetType is typedesc<json[]>;

    typedesc<map<json>?> recTd;

    if isArray {
        typedesc<json> arrayMemberType = getArrayMemberType(<typedesc<json[]>>targetType);
        map<json>? ann = arrayMemberType.@Schema;
        if ann !is () {
            return ann;
        }
        if isSimpleType(arrayMemberType) {
            return <JsonArraySchema>{
                \$schema: "https://json-schema.org/draft/2020-12/schema",
                items: {
                    'type: nilableType ?
                        [getStringRepresentation(<typedesc<json>>arrayMemberType), "null"] :
                        [getStringRepresentation(<typedesc<json>>arrayMemberType)]
                }
            };
        }
        recTd = <typedesc<map<json>?>>arrayMemberType;
    } else {
        recTd = <typedesc<map<json>?>>targetType;
    }

    string[] names = [];
    boolean[] required = [];
    typedesc<json>[] types = [];
    boolean[] nilable = [];
    populateFieldInfo(recTd, names, required, types, nilable);
    return generateJsonSchema(names, required, types, nilable, isArray, containsNil(recTd));
}

isolated function populateFieldInfo(typedesc<json> targetType, string[] names, boolean[] required,
        typedesc<json>[] types, boolean[] nilable) = @java:Method {
    name: "populateFieldInfo",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function getArrayMemberType(typedesc<json> targetType) returns typedesc<json> = @java:Method {
    name: "getArrayMemberType",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function containsNil(typedesc<json> targetType) returns boolean = @java:Method {
    name: "containsNil",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function generateJsonSchema(string[] names, boolean[] required,
        typedesc<json>[] types, boolean[] nilable, boolean isArray, boolean nilableType) returns JsonSchema|JsonArraySchema {
    map<JsonSchema|JsonArraySchema|map<json>> properties = {};
    string[] requiredSchema = [];

    JsonSchema schema = {
        \$schema: "https://json-schema.org/draft/2020-12/schema",
        'type: nilableType ? ["object", "null"] : "object",
        properties,
        required: requiredSchema
    };

    foreach int i in 0 ..< names.length() {
        string fieldName = names[i];
        map<json>? ann = types[i].@Schema;
        JsonSchema|JsonArraySchema|map<json> fieldSchema = ann is () ? getJsonSchemaType(types[i], nilable[i]) : ann;
        properties[fieldName] = fieldSchema;
        if required[i] {
            requiredSchema.push(fieldName);
        }
    }

    if isArray {
        return <JsonArraySchema>{
            \$schema: "https://json-schema.org/draft/2020-12/schema",
            items: schema,
            'type: nilableType ? ["array", "null"] : "array"
        };
    }

    return schema;
}

isolated function getJsonSchemaType(typedesc<json> fieldType, boolean nilable) returns JsonSchema|JsonArraySchema|map<json> {
    if isSimpleType(fieldType) {
        return nilable ?
            <JsonSchema>{
                'type: [getStringRepresentation(fieldType), "null"]
            } :
            <JsonSchema>{
                'type: getStringRepresentation(fieldType)
            };
    }

    return generateJsonSchemaForTypedesc(fieldType, nilable);
}

isolated function isSimpleType(typedesc<json> targetType) returns boolean =>
    targetType is typedesc<string|int|float|decimal|boolean|()>;

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

    panic error("JSON schema generation is not yet supported for type: " + fieldType.toString());
}
