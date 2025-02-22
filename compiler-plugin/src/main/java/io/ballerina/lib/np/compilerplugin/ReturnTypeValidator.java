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

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;

import java.util.HashMap;
import java.util.List;

/**
 * Natural programming return type validator.
 *
 * @since 0.3.0
 */
public class ReturnTypeValidator implements AnalysisTask<SyntaxNodeAnalysisContext> {

    private static final String NP_MODULE_PREFIX = "np";

    private SemanticModel semanticModel;
    private final HashMap<Location, DiagnosticInfo> allDiagnosticInfo = new HashMap<>();
    private Location currentLocation;
    private static String modulePrefix = NP_MODULE_PREFIX;

    @Override
    public void perform(SyntaxNodeAnalysisContext ctx) {
        semanticModel = ctx.semanticModel();
        List<Diagnostic> diagnostics = semanticModel.diagnostics();
        boolean erroneousCompilation = diagnostics.stream()
                .anyMatch(d -> d.diagnosticInfo().severity().equals(DiagnosticSeverity.ERROR));
        if (erroneousCompilation) {
            reset();
            return;
        }

        ModulePartNode rootNode = (ModulePartNode) ctx.node();
        // TODO
        reset();
    }

    private void reset() {
        semanticModel = null;
        allDiagnosticInfo.clear();
        currentLocation = null;
        modulePrefix = NP_MODULE_PREFIX;
    }
}
