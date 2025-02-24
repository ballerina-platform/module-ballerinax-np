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
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BTypedesc;

/**
 * Native implementation of natural programming functions.
 *
 * @since 0.3.0
 */
public class Native {
    public static Object callLlmCallBallerinaFunction(Environment env, BObject prompt, BTypedesc td) {
        return env.getRuntime().callFunction(
                new Module("ballerinax", "np", "0"), "callLlmBal", null, prompt, td);
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

    public static BTypedesc getArrayMemberType(BTypedesc td) {
        return ValueCreator.createTypedescValue(
                ((ArrayType) TypeUtils.getImpliedType(td.getDescribingType())).getElementType());
    }

    public static boolean containsNil(BTypedesc td) {
        return td.getDescribingType().isNilable();
    }
}
