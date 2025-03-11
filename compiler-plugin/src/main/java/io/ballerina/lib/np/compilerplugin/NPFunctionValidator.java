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
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.ExternalFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.IncludedRecordParameterNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeLocation;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.RestParameterNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.Location;

import java.util.Optional;

import static io.ballerina.lib.np.compilerplugin.Commons.CONTEXT_VAR;
import static io.ballerina.lib.np.compilerplugin.Commons.MODULE_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.PROMPT_VAR;
import static io.ballerina.lib.np.compilerplugin.Commons.findNPModule;
import static io.ballerina.lib.np.compilerplugin.Commons.hasNaturalFunctionAnnotation;

/**
 * Natural programming function signature validator.
 *
 * @since 0.3.0
 */
public class NPFunctionValidator implements AnalysisTask<SyntaxNodeAnalysisContext> {
    private static final String PROMPT_TYPE = "Prompt";
    private static final String CONTEXT_TYPE = "Context";

    private final CodeModifier.AnalysisData analysisData;

    NPFunctionValidator(CodeModifier.AnalysisData analysisData) {
        this.analysisData = analysisData;
    }

    @Override
    public void perform(SyntaxNodeAnalysisContext ctx) {
        SemanticModel semanticModel = ctx.semanticModel();
        TypeSymbol errorType = semanticModel.types().ERROR;
        TypeSymbol jsonType = semanticModel.types().JSON;
        ModulePartNode rootNode = (ModulePartNode) ctx.node();
        Optional<ModuleSymbol> npModule = findNPModule(semanticModel, rootNode);
        if (npModule.isEmpty()) {
            return;
        }

        ModuleSymbol npModuleSymbol = npModule.get();

        TypeSymbol promptType = ((TypeDefinitionSymbol) npModuleSymbol.allSymbols().stream()
                .filter(
                        symbol -> symbol instanceof TypeDefinitionSymbol typeDefinitionSymbol &&
                                typeDefinitionSymbol.moduleQualifiedName().equals(
                                        String.format("%s:%s", MODULE_NAME, PROMPT_TYPE)))
                .findFirst()
                .get()).typeDescriptor();
        TypeSymbol contextType = ((TypeDefinitionSymbol) npModuleSymbol.allSymbols().stream()
                .filter(
                        symbol -> symbol instanceof TypeDefinitionSymbol typeDefinitionSymbol &&
                                typeDefinitionSymbol.moduleQualifiedName().equals(
                                        String.format("%s:%s", MODULE_NAME, CONTEXT_TYPE)))
                .findFirst()
                .get()).typeDescriptor();

        String npModulePrefixStr = npModuleSymbol.id().modulePrefix();
        for (ModuleMemberDeclarationNode member : rootNode.members()) {
            if (member instanceof FunctionDefinitionNode functionDefinitionNode) {
                validatedFunctionParamsAndReturnType(semanticModel, functionDefinitionNode, errorType, jsonType, ctx,
                        npModulePrefixStr, promptType, contextType);
            }
        }
    }

    private void validatedFunctionParamsAndReturnType(SemanticModel semanticModel,
                                                      FunctionDefinitionNode functionDefinitionNode,
                                                      TypeSymbol errorType,
                                                      TypeSymbol jsonType, SyntaxNodeAnalysisContext ctx,
                                                      String npModulePrefix, TypeSymbol promptType,
                                                      TypeSymbol contextType) {
        if (!(functionDefinitionNode.functionBody() instanceof ExternalFunctionBodyNode externalFunctionBodyNode) ||
                !hasNaturalFunctionAnnotation(externalFunctionBodyNode, npModulePrefix)) {
            return;
        }

        FunctionSignatureNode functionSignatureNode = functionDefinitionNode.functionSignature();
        validateParameters(semanticModel, functionDefinitionNode, ctx, promptType, contextType, functionSignatureNode);
        validateReturnType(semanticModel, functionSignatureNode.returnTypeDesc(), errorType, jsonType, ctx,
                functionDefinitionNode.location());
    }

    private void validateParameters(SemanticModel semanticModel, FunctionDefinitionNode functionDefinitionNode,
                                    SyntaxNodeAnalysisContext ctx, TypeSymbol promptType, TypeSymbol contextType,
                                    FunctionSignatureNode functionSignatureNode) {
        boolean hasPromptParam = false;

        for (ParameterNode parameter : functionSignatureNode.parameters()) {
            SyntaxKind kind = parameter.kind();
            String parameterName = getParameterName(parameter, kind);

            if (PROMPT_VAR.equals(parameterName)) {
                hasPromptParam = true;
                validateParam(semanticModel, ctx, promptType, parameter, kind,
                        DiagnosticCode.PROMPT_PARAM_MUST_BE_REQUIRED_OR_DEFAULTABLE,
                        DiagnosticCode.TYPE_OF_PROMPT_PARAM_MUST_BE_A_SUBTYPE_OF_NP_PROMPT);
            }

            if (CONTEXT_VAR.equals(parameterName)) {
                validateParam(semanticModel, ctx, contextType, parameter, kind,
                        DiagnosticCode.CONTEXT_PARAM_MUST_BE_REQUIRED_OR_DEFAULTABLE,
                        DiagnosticCode.TYPE_OF_CONTEXT_PARAM_MUST_BE_A_SUBTYPE_OF_NP_CONTEXT);
            }
        }

        if (!hasPromptParam) {
            reportDiagnostic(ctx, functionDefinitionNode.location(), DiagnosticCode.PROMPT_PARAM_IS_REQUIRED);
        }
    }

    private void validateParam(SemanticModel semanticModel, SyntaxNodeAnalysisContext ctx, TypeSymbol targetType,
                               ParameterNode parameter, SyntaxKind kind,
                               DiagnosticCode mustBeRequiredOrDefaultableDiagCode,
                               DiagnosticCode invalidParamTypeCodeDiagCode) {
        validateRequiredOrDefaultableParam(ctx, kind, parameter.location(), mustBeRequiredOrDefaultableDiagCode);
        Node parameterType = getParameterType(parameter, kind);
        Optional<TypeSymbol> symbol = semanticModel.type(parameterType.lineRange());
        if (symbol.isEmpty()) {
            return;
        }

        if (!symbol.get().subtypeOf(targetType)) {
            reportDiagnostic(ctx, parameterType.location(), invalidParamTypeCodeDiagCode);
        }
    }

    private void validateRequiredOrDefaultableParam(SyntaxNodeAnalysisContext ctx,
                                                    SyntaxKind kind, Location location,
                                                    DiagnosticCode diagnosticCode) {
        if (kind != SyntaxKind.REQUIRED_PARAM && kind != SyntaxKind.DEFAULTABLE_PARAM) {
            reportDiagnostic(ctx, location, diagnosticCode);
        }
    }

    private String getParameterName(ParameterNode parameter, SyntaxKind kind) {
        return switch (kind) {
            case REQUIRED_PARAM -> ((RequiredParameterNode) parameter).paramName().get().text();
            case DEFAULTABLE_PARAM -> ((DefaultableParameterNode) parameter).paramName().get().text();
            case INCLUDED_RECORD_PARAM -> ((IncludedRecordParameterNode) parameter).paramName().get().text();
            default -> ((RestParameterNode) parameter).paramName().get().text();
        };
    }

    private Node getParameterType(ParameterNode parameter, SyntaxKind kind) {
        return switch (kind) {
            case REQUIRED_PARAM -> ((RequiredParameterNode) parameter).typeName();
            case DEFAULTABLE_PARAM -> ((DefaultableParameterNode) parameter).typeName();
            case INCLUDED_RECORD_PARAM -> ((IncludedRecordParameterNode) parameter).typeName();
            default -> ((RestParameterNode) parameter).typeName();
        };
    }

    private void validateReturnType(SemanticModel semanticModel,
                                    Optional<ReturnTypeDescriptorNode> returnTypeDescriptorNode,
                                    TypeSymbol errorType,
                                    TypeSymbol jsonType,
                                    SyntaxNodeAnalysisContext ctx, NodeLocation functionDefLocation) {
        Location location = functionDefLocation;
        if (returnTypeDescriptorNode.isEmpty()) {
            reportDiagnostic(ctx, location, DiagnosticCode.RETURN_TYPE_MUST_CONTAIN_ERROR);
            return;
        }
        ReturnTypeDescriptorNode returnTypeDescriptor = returnTypeDescriptorNode.get();
        location = returnTypeDescriptor.location();
        Optional<TypeSymbol> typeSymbol = semanticModel.type(returnTypeDescriptor.type().lineRange());
        if (typeSymbol.isEmpty()) {
            reportDiagnostic(ctx, location, DiagnosticCode.RETURN_TYPE_MUST_CONTAIN_ERROR);
            return;
        }

        TypeSymbol returnTypeSymbol = typeSymbol.get();
        if (!errorType.subtypeOf(returnTypeSymbol)) {
            reportDiagnostic(ctx, location, DiagnosticCode.RETURN_TYPE_MUST_CONTAIN_ERROR);
        } else if (returnTypeSymbol.subtypeOf(errorType)) {
            reportDiagnostic(ctx, location, DiagnosticCode.RETURN_TYPE_MUST_CONTAIN_A_UNION_OF_NON_ERROR_AND_ERROR);
            return;
        }

        if (returnTypeSymbol instanceof TypeReferenceTypeSymbol typeReferenceTypeSymbol) {
            returnTypeSymbol = typeReferenceTypeSymbol.typeDescriptor();
        }

        if (!(returnTypeSymbol instanceof UnionTypeSymbol unionTypeSymbol)) {
            if (returnTypeSymbol.typeKind() != TypeDescKind.ERROR && returnTypeSymbol.subtypeOf(jsonType)) {
                reportDiagnostic(ctx, location, DiagnosticCode.NON_ERROR_RETURN_TYPE_MUST_BE_A_SUBTYPE_OF_JSON);
            }
            return;
        }

        for (TypeSymbol memberTypeDescriptor : unionTypeSymbol.memberTypeDescriptors()) {
            if (memberTypeDescriptor instanceof TypeReferenceTypeSymbol typeReferenceTypeSymbol) {
                memberTypeDescriptor = typeReferenceTypeSymbol.typeDescriptor();
            }

            if (memberTypeDescriptor.typeKind() != TypeDescKind.ERROR && !memberTypeDescriptor.subtypeOf(jsonType)) {
                reportDiagnostic(ctx, location, DiagnosticCode.NON_ERROR_RETURN_TYPE_MUST_BE_A_SUBTYPE_OF_JSON);
            }
        }
    }



    private void reportDiagnostic(SyntaxNodeAnalysisContext ctx, Location location,
                                  DiagnosticCode diagnosticsCode) {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnosticsCode.getCode(),
                diagnosticsCode.getMessage(), diagnosticsCode.getSeverity());
        this.analysisData.analysisTaskErrored = true;
        ctx.reportDiagnostic(DiagnosticFactory.createDiagnostic(diagnosticInfo, location));
    }
}
