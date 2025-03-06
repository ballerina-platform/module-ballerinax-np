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

import io.ballerina.openapi.service.mapper.type.TypeMapperImpl;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;

public class TypeMapperImplInitializer implements AnalysisTask<SyntaxNodeAnalysisContext> {
    CodeModifier.AnalysisData analysisData;

    TypeMapperImplInitializer(CodeModifier.AnalysisData analysisData) {
        this.analysisData = analysisData;
    }
    @Override
    public void perform(SyntaxNodeAnalysisContext ctx) {
        this.analysisData.typeMapper = new TypeMapperImpl(ctx);
    }
}
