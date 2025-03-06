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

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
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
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TreeModifier;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.openapi.service.mapper.type.TypeMapper;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.plugins.ModifierTask;
import io.ballerina.projects.plugins.SourceModifierContext;
import io.ballerina.tools.text.TextDocument;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.OpenAPISchema2JsonSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createToken;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.CLOSE_PAREN_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.DEFAULTABLE_PARAM;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.OPEN_PAREN_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.REQUIRED_PARAM;
import static io.ballerina.lib.np.compilerplugin.Constants.BYTE;
import static io.ballerina.lib.np.compilerplugin.Constants.CALL_LLM;
import static io.ballerina.lib.np.compilerplugin.Constants.MODEL_VAR;
import static io.ballerina.lib.np.compilerplugin.Constants.MODULE_NAME;
import static io.ballerina.lib.np.compilerplugin.Constants.NUMBER;
import static io.ballerina.lib.np.compilerplugin.Constants.ORG_NAME;
import static io.ballerina.lib.np.compilerplugin.Constants.PROMPT_VAR;
import static io.ballerina.lib.np.compilerplugin.Commons.hasLlmCallAnnotation;
import static io.ballerina.lib.np.compilerplugin.Constants.STRING;
import static io.ballerina.projects.util.ProjectConstants.EMPTY_STRING;

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

    private static Optional<String> npPrefixIfImported = Optional.empty();
    private static final SimpleNameReferenceNode PROMPT_NAME_REF_NODE =
            NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(PROMPT_VAR));
    private static final SimpleNameReferenceNode MODEL_NAME_REF_NODE =
            NodeFactory.createSimpleNameReferenceNode(NodeFactory.createIdentifierToken(MODEL_VAR));

    private final CodeModifier.AnalysisData analysisData;

    PromptAsCodeCodeModificationTask(CodeModifier.AnalysisData analysisData) {
        this.analysisData = analysisData;
    }

    @Override
    public void modify(SourceModifierContext modifierContext) {
        Package currentPackage = modifierContext.currentPackage();

        if (this.analysisData.analysisTaskErrored) {
            return;
        }

        Map<String, String> typeSchemas = new HashMap<>();
        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);

            for (DocumentId documentId: module.documentIds()) {
                Document document = module.document(documentId);
                processImportDeclarations(document);
                processExternalFunctions(document, module, modifierContext, typeSchemas);
            }

            for (DocumentId documentId: module.documentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifySourceFile(
                        modifyDocument(document, typeSchemas), documentId);
            }

            for (DocumentId documentId: module.testDocumentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifyTestSourceFile(
                        modifyDocument(document, typeSchemas), documentId);
            }
        }
    }

    private void processExternalFunctions(Document document, Module module,
                                          SourceModifierContext modifierContext, Map<String, String> typeSchemas) {
        if (npPrefixIfImported.isEmpty()) {
            return;
        }
        SyntaxTree syntaxTree = document.syntaxTree();
        ModulePartNode rootNode = syntaxTree.rootNode();
        SemanticModel semanticModel = modifierContext.compilation().getSemanticModel(module.moduleId());
        for (ModuleMemberDeclarationNode memberNode : rootNode.members()) {
            if (!isExternalFunctionWithLlmCall(memberNode, npPrefixIfImported.get())) {
                continue;
            }

            FunctionDefinitionNode functionDefinition = (FunctionDefinitionNode) memberNode;
            extractAndStoreSchemas(semanticModel, functionDefinition, typeSchemas, this.analysisData.typeMapper);
        }
    }

    private static void processImportDeclarations(Document document) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();
        ImportDeclarationModifier importDeclarationModifier = new ImportDeclarationModifier();
        modulePartNode.apply(importDeclarationModifier);
    }

    private static TextDocument modifyDocument(Document document, Map<String, String> typeSchemas) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();
        FunctionModifier functionModifier = new FunctionModifier();
        TypeDefinitionModifier typeDefinitionModifier = new TypeDefinitionModifier(typeSchemas);

        ModulePartNode modifiedRoot = (ModulePartNode) modulePartNode.apply(functionModifier);
        modifiedRoot = modifiedRoot.modify(modifiedRoot.imports(), modifiedRoot.members(), modifiedRoot.eofToken());

        ModulePartNode finalRoot = (ModulePartNode) modifiedRoot.apply(typeDefinitionModifier);
        finalRoot = finalRoot.modify(finalRoot.imports(), finalRoot.members(), finalRoot.eofToken());

        return document.syntaxTree().modifyWith(finalRoot).textDocument();
    }

    private static class ImportDeclarationModifier extends TreeModifier {

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
            npPrefixIfImported = Optional.of(prefix.isEmpty() ? MODULE_NAME : prefix.get().prefix().text());
            return importDeclarationNode;
        }
    }

    private static class FunctionModifier extends TreeModifier {

        @Override
        public FunctionDefinitionNode transform(FunctionDefinitionNode functionDefinition) {
            if (npPrefixIfImported.isEmpty()) {
                return functionDefinition;
            }

            String npPrefix = npPrefixIfImported.get();

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
                        Constants.MODEL_VAR.equals(((RequiredParameterNode) parameter).paramName().get().text())) {
                    return true;
                }

                if (kind == DEFAULTABLE_PARAM &&
                        Constants.MODEL_VAR.equals(((DefaultableParameterNode) parameter).paramName().get().text())) {
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
                NodeFactory.createIdentifierToken(CALL_LLM)
        );
    }

    private static class TypeDefinitionModifier extends TreeModifier {

        private final Map<String, String> typeSchemas;

        TypeDefinitionModifier(Map<String, String> typeSchemas) {
            this.typeSchemas = typeSchemas;
        }

        @Override
        public TypeDefinitionNode transform(TypeDefinitionNode typeDefinitionNode) {
            if (npPrefixIfImported.isEmpty()) {
                return typeDefinitionNode;
            }
            String typeName = typeDefinitionNode.typeName().text();

            if (!this.typeSchemas.containsKey(typeName)) {
                return typeDefinitionNode;
            }

            MetadataNode updatedMetadataNode =
                                        updateMetadata(typeDefinitionNode, typeSchemas.get(typeName));
            return typeDefinitionNode.modify().withMetadata(updatedMetadataNode).apply();
        }

        private MetadataNode updateMetadata(TypeDefinitionNode typeDefinitionNode, String schema) {
            MetadataNode metadataNode = getMetadataNode(typeDefinitionNode);
            NodeList<AnnotationNode> updatedAnnotations = updateAnnotations(metadataNode.annotations(), schema);
            return metadataNode.modify().withAnnotations(updatedAnnotations).apply();
        }
    }

    public static MetadataNode getMetadataNode(TypeDefinitionNode serviceNode) {
        return serviceNode.metadata().orElseGet(() -> {
            NodeList<AnnotationNode> annotations = NodeFactory.createNodeList();
            return NodeFactory.createMetadataNode(null, annotations);
        });
    }

    private static NodeList<AnnotationNode> updateAnnotations(NodeList<AnnotationNode> currentAnnotations,
                                                              String jsonSchema) {
        NodeList<AnnotationNode> updatedAnnotations = NodeFactory.createNodeList();

        if (currentAnnotations.isEmpty()) {
            updatedAnnotations = updatedAnnotations.add(getSchemaAnnotation(jsonSchema));
        }

        return updatedAnnotations;
    }

    public static AnnotationNode getSchemaAnnotation(String jsonSchema) {
        String configIdentifierString = Constants.NP + Constants.COLON + Constants.SCHEMA_ANNOTATION_IDENTIFIER;
        IdentifierToken identifierToken = NodeFactory.createIdentifierToken(configIdentifierString);

        return NodeFactory.createAnnotationNode(
                NodeFactory.createToken(SyntaxKind.AT_TOKEN),
                NodeFactory.createSimpleNameReferenceNode(identifierToken),
                getAnnotationExpression(jsonSchema)
        );
    }

    public static MappingConstructorExpressionNode getAnnotationExpression(String jsonSchema) {
        SeparatedNodeList<MappingFieldNode> separatedNodeList =
                                            NodeFactory.createSeparatedNodeList(createSchemaField(jsonSchema));

        return NodeFactory.createMappingConstructorExpressionNode(
                NodeFactory.createToken(SyntaxKind.OPEN_BRACE_TOKEN),
                separatedNodeList,
                NodeFactory.createToken(SyntaxKind.CLOSE_BRACE_TOKEN)
        );
    }

    public static SpecificFieldNode createSchemaField(String jsonSchema) {
        return NodeFactory.createSpecificFieldNode(
                null,
                AbstractNodeFactory.createIdentifierToken(Constants.SCHEMA),
                AbstractNodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                NodeParser.parseExpression(jsonSchema)
        );
    }

    private boolean isExternalFunctionWithLlmCall(ModuleMemberDeclarationNode memberNode, String npModulePrefixStr) {
        if (!(memberNode instanceof FunctionDefinitionNode functionDefinition)) {
            return false;
        }
        return functionDefinition.functionBody() instanceof ExternalFunctionBodyNode externalFunctionBodyNode
                && hasLlmCallAnnotation(externalFunctionBodyNode, npModulePrefixStr);
    }

    private void extractAndStoreSchemas(SemanticModel semanticModel, FunctionDefinitionNode functionDefinition,
                                        Map<String, String> typeSchemas, TypeMapper typeMapper) {
        Optional<ReturnTypeDescriptorNode> returnTypeNodeOpt = functionDefinition.functionSignature().returnTypeDesc();
        if (returnTypeNodeOpt.isEmpty()) {
            return;
        }

        ReturnTypeDescriptorNode returnTypeNode = returnTypeNodeOpt.get();
        Optional<TypeSymbol> typeSymbolOpt = semanticModel.type(returnTypeNode.type().lineRange());
        if (typeSymbolOpt.isEmpty()) {
            return;
        }

        TypeSymbol typeSymbol = typeSymbolOpt.get();
        if (typeSymbol instanceof UnionTypeSymbol unionTypeSymbol) {
            for (TypeSymbol memberType : unionTypeSymbol.memberTypeDescriptors()) {
                if (memberType instanceof TypeReferenceTypeSymbol typeReferenceTypeSymbol) {
                    Schema schema = typeMapper.getSchema(typeReferenceTypeSymbol);
                    typeSchemas.put(typeReferenceTypeSymbol.definition().getName().get(), getJsonSchema(schema));
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static String getJsonSchema(Schema schema) {
        modifySchema(schema);
        OpenAPISchema2JsonSchema openAPISchema2JsonSchema = new OpenAPISchema2JsonSchema();
        openAPISchema2JsonSchema.process(schema);
        String newLineRegex = "\\R";
        String jsonCompressionRegex = "\\s*([{}\\[\\]:,])\\s*";
        return Json.pretty(schema.getJsonSchema())
                .replaceAll(newLineRegex, EMPTY_STRING)
                .replaceAll(jsonCompressionRegex, "$1");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void modifySchema(Schema schema) {
        if (schema == null) {
            return;
        }
        modifySchema(schema.getItems());
        modifySchema(schema.getNot());

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.values().forEach(PromptAsCodeCodeModificationTask::modifySchema);
        }

        List<Schema> allOf = schema.getAllOf();
        if (allOf != null) {
            schema.setType(null);
            allOf.forEach(PromptAsCodeCodeModificationTask::modifySchema);
        }

        List<Schema> anyOf = schema.getAnyOf();
        if (anyOf != null) {
            schema.setType(null);
            anyOf.forEach(PromptAsCodeCodeModificationTask::modifySchema);
        }

        List<Schema> oneOf = schema.getOneOf();
        if (oneOf != null) {
            schema.setType(null);
            oneOf.forEach(PromptAsCodeCodeModificationTask::modifySchema);
        }

        // Override default ballerina byte to json schema mapping
        if (BYTE.equals(schema.getFormat()) && STRING.equals(schema.getType())) {
            schema.setFormat(null);
            schema.setType(NUMBER);
        }
        removeUnwantedFields(schema);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeUnwantedFields(Schema schema) {
        schema.setSpecVersion(null);
        schema.setSpecVersion(null);
        schema.setContains(null);
        schema.set$id(null);
        schema.set$schema(null);
        schema.set$anchor(null);
        schema.setExclusiveMaximumValue(null);
        schema.setExclusiveMinimumValue(null);
        schema.setDiscriminator(null);
        schema.setTitle(null);
        schema.setMaximum(null);
        schema.setExclusiveMaximum(null);
        schema.setMinimum(null);
        schema.setExclusiveMinimum(null);
        schema.setMaxLength(null);
        schema.setMinLength(null);
        schema.setMaxItems(null);
        schema.setMinItems(null);
        schema.setMaxProperties(null);
        schema.setMinProperties(null);
        schema.setAdditionalProperties(null);
        schema.setAdditionalProperties(null);
        schema.set$ref(null);
        schema.set$ref(null);
        schema.setReadOnly(null);
        schema.setWriteOnly(null);
        schema.setExample(null);
        schema.setExample(null);
        schema.setExternalDocs(null);
        schema.setDeprecated(null);
        schema.setPrefixItems(null);
        schema.setContentEncoding(null);
        schema.setContentMediaType(null);
        schema.setContentSchema(null);
        schema.setPropertyNames(null);
        schema.setUnevaluatedProperties(null);
        schema.setMaxContains(null);
        schema.setMinContains(null);
        schema.setAdditionalItems(null);
        schema.setUnevaluatedItems(null);
        schema.setIf(null);
        schema.setElse(null);
        schema.setThen(null);
        schema.setDependentSchemas(null);
        schema.set$comment(null);
        schema.setExamples(null);
        schema.setExtensions(null);
        schema.setConst(null);
    }
}
