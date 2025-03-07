/*
 *  Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org).
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.lib.np.compilerplugin;

import io.ballerina.compiler.syntax.tree.ExternalFunctionBodyNode;

/**
 * Class containing common constants and functionality.
 *
 * @since 0.3.0
 */
class Commons {
    static final String ORG_NAME = "ballerinax";
    static final String MODULE_NAME = "np";
    static final String PROMPT_VAR = "prompt";
    static final String MODEL_VAR = "model";

    private static final String LLM_CALL_ANNOT = "LlmCall";

    static boolean hasLlmCallAnnotation(ExternalFunctionBodyNode functionBody, String modulePrefix) {
        return hasAnnotation(functionBody, modulePrefix, LLM_CALL_ANNOT);
    }

    static boolean hasAnnotation(ExternalFunctionBodyNode functionBody, String modulePrefix,
                                 String annotation) {
        final String annotationRef = modulePrefix + ":" + annotation;
        return functionBody.annotations().stream().
                anyMatch(annotationNode -> annotationNode.annotReference().toString().trim()
                        .equals(annotationRef));
    }
}
