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

import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.ExpressionFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.ExternalFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionCallExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportOrgNameNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TreeModifier;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.plugins.ModifierTask;
import io.ballerina.projects.plugins.SourceModifierContext;
import io.ballerina.tools.text.TextDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createToken;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.CLOSE_PAREN_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.DEFAULTABLE_PARAM;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.OPEN_PAREN_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.REQUIRED_PARAM;
import static io.ballerina.lib.np.compilerplugin.Commons.MODEL_VAR;
import static io.ballerina.lib.np.compilerplugin.Commons.MODULE_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.ORG_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.PROMPT_VAR;
import static io.ballerina.lib.np.compilerplugin.Commons.hasLlmCallAnnotation;

/**
 * Code modification task to replace runtime prompt as code external functions with np:call.
 *
 * @since 0.3.0
 */
public class PromptAsCodeCodeModificationTask implements ModifierTask<SourceModifierContext> {

    private static final Token OPEN_PAREN = createToken(OPEN_PAREN_TOKEN);
    private static final Token CLOSE_PAREN = createToken(CLOSE_PAREN_TOKEN);
    private static final Token SEMICOLON = createToken(SyntaxKind.SEMICOLON_TOKEN);
    private static final Token COLON = createToken(SyntaxKind.COLON_TOKEN);
    private static final Token RIGHT_DOUBLE_ARROW = createToken(SyntaxKind.RIGHT_DOUBLE_ARROW_TOKEN);
    private static final Token COMMA = createToken(SyntaxKind.COMMA_TOKEN);

    private static final SimpleNameReferenceNode PROMPT_NAME_REF_NODE =
            NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(PROMPT_VAR));
    private static final SimpleNameReferenceNode MODEL_NAME_REF_NODE =
            NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(MODEL_VAR));

    private final CodeModifier.AnalysisTaskStatus analysisTaskStatus;

    PromptAsCodeCodeModificationTask(CodeModifier.AnalysisTaskStatus analysisTaskStatus) {
        this.analysisTaskStatus = analysisTaskStatus;
    }

    @Override
    public void modify(SourceModifierContext modifierContext) {
        Package currentPackage = modifierContext.currentPackage();

        if (this.analysisTaskStatus.errored) {
            return;
        }

        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);

            for (DocumentId documentId: module.documentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifySourceFile(
                        modifyDocument(document), documentId);
            }

            for (DocumentId documentId: module.testDocumentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifyTestSourceFile(
                        modifyDocument(document), documentId);
            }
        }
    }

    private static TextDocument modifyDocument(Document document) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();
        List<ModuleMemberDeclarationNode> newMembers = new ArrayList<>();
        FunctionModifier functionModifier = new FunctionModifier();
        ModulePartNode newRoot = (ModulePartNode) modulePartNode.apply(functionModifier);
        newRoot = newRoot.modify(newRoot.imports(), newRoot.members().addAll(newMembers), newRoot.eofToken());
        return document.syntaxTree().modifyWith(newRoot).textDocument();
    }

    private static class FunctionModifier extends TreeModifier {

        private Optional<String> npPrefixIfImported = Optional.empty();

        @Override
        public ImportDeclarationNode transform(ImportDeclarationNode importDeclarationNode) {
            Optional<ImportOrgNameNode> importOrgNameNode = importDeclarationNode.orgName();
            // Allow the not present case for module tests.
            if (importOrgNameNode.isPresent() && !ORG_NAME.equals(importOrgNameNode.get().orgName().text())) {
                return importDeclarationNode;
            }

            SeparatedNodeList<IdentifierToken> moduleName = importDeclarationNode.moduleName();
            if (moduleName.size() > 1 || !MODULE_NAME.equals(moduleName.iterator().next().text())) {
                return importDeclarationNode;
            }

            Optional<ImportPrefixNode> prefix = importDeclarationNode.prefix();
            this.npPrefixIfImported = Optional.of(prefix.isEmpty() ? MODULE_NAME : prefix.get().prefix().text());
            return importDeclarationNode;
        }

        @Override
        public FunctionDefinitionNode transform(FunctionDefinitionNode functionDefinition) {
            if (this.npPrefixIfImported.isEmpty()) {
                return functionDefinition;
            }

            String npPrefix = this.npPrefixIfImported.get();

            FunctionBodyNode functionBodyNode = functionDefinition.functionBody();

            if (!(functionBodyNode instanceof ExternalFunctionBodyNode functionBody)) {
                return functionDefinition;
            }

            if (hasLlmCallAnnotation(functionBody, npPrefix)) {
                ExpressionFunctionBodyNode expressionFunctionBody =
                        NodeFactory.createExpressionFunctionBodyNode(
                                RIGHT_DOUBLE_ARROW,
                                createNPCallFunctionCallExpression(npPrefix, hasModelParam(functionDefinition)),
                                SEMICOLON);
                return functionDefinition.modify().withFunctionBody(expressionFunctionBody).apply();
            }

            return functionDefinition;
        }

        private boolean hasModelParam(FunctionDefinitionNode functionDefinition) {
            for (ParameterNode parameter : functionDefinition.functionSignature().parameters()) {
                SyntaxKind kind = parameter.kind();
                if (kind == REQUIRED_PARAM &&
                        Commons.MODEL_VAR.equals(((RequiredParameterNode) parameter).paramName().get().text())) {
                    return true;
                }

                if (kind == DEFAULTABLE_PARAM &&
                        Commons.MODEL_VAR.equals(((DefaultableParameterNode) parameter).paramName().get().text())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static FunctionCallExpressionNode createNPCallFunctionCallExpression(String npPrefix,
                                                                                 boolean hasModelParam) {
        SeparatedNodeList<FunctionArgumentNode> arguments =
                hasModelParam ?
                        NodeFactory.createSeparatedNodeList(
                                NodeFactory.createPositionalArgumentNode(PROMPT_NAME_REF_NODE),
                                COMMA,
                                NodeFactory.createPositionalArgumentNode(MODEL_NAME_REF_NODE)
                        ) :
                        NodeFactory.createSeparatedNodeList(
                                NodeFactory.createPositionalArgumentNode(PROMPT_NAME_REF_NODE)
                        );
        return NodeFactory.createFunctionCallExpressionNode(
                createNPCallQualifiedNameReferenceNode(npPrefix),
                OPEN_PAREN,
                arguments,
                CLOSE_PAREN
        );
    }

    private static QualifiedNameReferenceNode createNPCallQualifiedNameReferenceNode(String npPrefix) {
        return NodeFactory.createQualifiedNameReferenceNode(
                NodeFactory.createIdentifierToken(npPrefix),
                COLON,
                NodeFactory.createIdentifierToken("callLlm")
        );
    }
}
