/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.np.compilerplugin;

import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import static io.ballerina.tools.diagnostics.DiagnosticSeverity.ERROR;

/**
 * Diagnostic code for the natural programming compiler plugin.
 *
 * @since 0.3.0
 */
public enum DiagnosticCode {
    PROMPT_PARAM_IS_REQUIRED("NP_ERROR_001", "an ''LlmCall'' function must contain the ''prompt'' parameter", ERROR),
    PROMPT_PARAM_MUST_BE_REQUIRED_OR_DEFAULTABLE("NP_ERROR_002",
            "the ''prompt'' parameter must be a required or defaultable parameter", ERROR),
    MODEL_PARAM_MUST_BE_REQUIRED_OR_DEFAULTABLE("NP_ERROR_003",
            "the ''model'' parameter must be a required or defaultable parameter", ERROR),
    TYPE_OF_PROMPT_PARAM_MUST_BE_A_SUBTYPE_OF_NP_PROMPT("NP_ERROR_004",
            "the type of the ''prompt'' parameter must be a subtype of ''ballerinax/np:Prompt''", ERROR),
    TYPE_OF_MODEL_PARAM_MUST_BE_A_SUBTYPE_OF_NP_MODEL("NP_ERROR_005",
            "the type of the ''model'' parameter must be a subtype of ''ballerinax/np:Model''", ERROR),
    RETURN_TYPE_MUST_CONTAIN_ERROR("NP_ERROR_006",
            "the return type of an ''LlmCall'' function must contain ''error''", ERROR);

    private final String code;
    private final String message;
    private final DiagnosticSeverity severity;

    DiagnosticCode(String code, String message, DiagnosticSeverity severity) {
        this.code = code;
        this.message = message;
        this.severity = severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }
}
