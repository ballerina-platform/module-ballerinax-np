/*
 * Copyright (c) 2025, WSO2 LLC. (http://wso2.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ballerina.lib.np;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.JsonType;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

import java.util.Map;

import static io.ballerina.runtime.api.creators.ValueCreator.createMapValue;

/**
 * Native implementation of natural programming functions.
 *
 * @since 0.3.0
 */
public class Native {

    static Boolean isSchemaGeneratedAtCompileTime;

    public static Object callLlm(Environment env, BObject prompt, BMap context, BTypedesc targetType) {
        isSchemaGeneratedAtCompileTime = true;
        Object jsonSchema = generateJsonSchemaForType(targetType.getDescribingType());
        return env.getRuntime().callFunction(
                new Module("ballerinax", "np", "0"), "callLlmGeneric", null, prompt, context, targetType,
                isSchemaGeneratedAtCompileTime ? jsonSchema : null);
    }

    public static Object generateJsonSchemaForType(Type td) {
        Type type = TypeUtils.getReferredType(td);
        if (isSimpleType(type)) {
            return createSimpleTypeSchema(type);
        }

        return switch (type) {
            case RecordType recordType -> generateJsonSchemaForRecordType(recordType);
            case JsonType ignored -> generateJsonSchemaForJson();
            case ArrayType arrayType -> generateJsonSchemaForArrayType(arrayType);
            case TupleType tupleType -> generateJsonSchemaForTupleType(tupleType);
            case UnionType unionType -> generateJsonSchemaForUnionType(unionType);
            default -> null;
        };
    }

    private static BMap<BString, Object> createSimpleTypeSchema(Type type) {
        BMap<BString, Object> schemaMap = createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_JSON));
        schemaMap.put(StringUtils.fromString("type"), StringUtils.fromString(getStringRepresentation(type)));
        return schemaMap;
    }

    private static BMap<BString, Object> generateJsonSchemaForJson() {
        BString[] bStringValues = new BString[6];
        bStringValues[0] = StringUtils.fromString("object");
        bStringValues[1] = StringUtils.fromString("array");
        bStringValues[2] = StringUtils.fromString("string");
        bStringValues[3] = StringUtils.fromString("number");
        bStringValues[4] = StringUtils.fromString("boolean");
        bStringValues[5] = StringUtils.fromString("null");
        BMap<BString, Object> schemaMap = createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_JSON));
        schemaMap.put(StringUtils.fromString("type"), ValueCreator.createArrayValue(bStringValues));
        return schemaMap;
    }

    private static Object generateJsonSchemaForArrayType(ArrayType arrayType) {
        BMap<BString, Object> schemaMap = createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_JSON));
        Type elementType = TypeUtils.getReferredType(arrayType.getElementType());
        schemaMap.put(StringUtils.fromString("type"), StringUtils.fromString("array"));
        schemaMap.put(StringUtils.fromString("items"), generateJsonSchemaForType(elementType));
        return schemaMap;
    }

    private static Object generateJsonSchemaForTupleType(TupleType tupleType) {
        BMap<BString, Object> schemaMap = createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_JSON));
        schemaMap.put(StringUtils.fromString("type"), StringUtils.fromString("array"));
        BArray annotationArray = ValueCreator.createArrayValue(TypeCreator.createArrayType(PredefinedTypes.TYPE_JSON));
        int index = 0;
        for (Type type : tupleType.getTupleTypes()) {
            annotationArray.add(index++, generateJsonSchemaForType(type));
        }
        schemaMap.put(StringUtils.fromString("items"), annotationArray);
        return schemaMap;
    }

    private static boolean isSimpleType(Type type) {
        return type.getBasicType().all() <= 0b100000;
    }

    private static String getStringRepresentation(Type type) {
        return switch (type.getBasicType().all()) {
            case 0b000000 -> "null";
            case 0b000010 -> "boolean";
            case 0b000100 -> "integer";
            case 0b001000, 0b010000 -> "number";
            case 0b100000 -> "string";
            default -> null;
        };
    }

    private static Object generateJsonSchemaForRecordType(RecordType recordType) {
        for (Map.Entry<BString, Object> entry : recordType.getAnnotations().entrySet()) {
            if ("ballerinax/np:0:Schema".equals(entry.getKey().getValue())) {
                return entry.getValue();
            }
        }
        isSchemaGeneratedAtCompileTime = false;
        return null;
    }

    private static Object generateJsonSchemaForUnionType(UnionType unionType) {
        BMap<BString, Object> schemaMap = createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_JSON));
        schemaMap.put(StringUtils.fromString("type"), StringUtils.fromString("object"));
        BArray annotationArray = ValueCreator.createArrayValue(TypeCreator.createArrayType(PredefinedTypes.TYPE_JSON));

        int index = 0;
        for (Type type : unionType.getMemberTypes()) {
            annotationArray.add(index++, generateJsonSchemaForType(type));
        }

        schemaMap.put(StringUtils.fromString("anyOf"), annotationArray);
        return schemaMap;
    }

    // Simple, simple, SIMPLE implementation for now.
    public static void populateFieldInfo(BTypedesc typedesc, BArray names, BArray required,
                                         BArray types, BArray nilable) {
        RecordType recordType = (RecordType) TypeUtils.getImpliedType(typedesc.getDescribingType());
        for (Field field : recordType.getFields().values()) {
            names.append(StringUtils.fromString(field.getFieldName()));
            long flags = field.getFlags();
            required.append(SymbolFlags.isFlagOn(flags, SymbolFlags.REQUIRED) ||
                    !SymbolFlags.isFlagOn(flags, SymbolFlags.OPTIONAL));
            Type fieldType = TypeUtils.getImpliedType(field.getFieldType());
            nilable.append(fieldType.isNilable());
            // Naive implementation - temporary
            if (fieldType instanceof UnionType unionType) {
                for (Type memberType : unionType.getMemberTypes()) {
                    memberType = TypeUtils.getImpliedType(memberType);
                    if (memberType.getTag() != TypeTags.NULL_TAG) {
                        fieldType = memberType;
                        break;
                    }
                }
            }
            types.append(ValueCreator.createTypedescValue(fieldType));
        }
    }

    public static BTypedesc getArrayMemberType(BTypedesc targetType) {
        return ValueCreator.createTypedescValue(
                ((ArrayType) TypeUtils.getImpliedType(targetType.getDescribingType())).getElementType());
    }

    public static boolean containsNil(BTypedesc targetType) {
        return targetType.getDescribingType().isNilable();
    }
}
