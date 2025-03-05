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
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.ExternalFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.openapi.service.mapper.type.TypeMapper;
import io.ballerina.openapi.service.mapper.type.TypeMapperImpl;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.OpenAPISchema2JsonSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.ballerina.lib.np.compilerplugin.Commons.findNPModule;
import static io.ballerina.lib.np.compilerplugin.Commons.hasLlmCallAnnotation;
import static io.ballerina.projects.util.ProjectConstants.EMPTY_STRING;

public class FunctionReturnTypeSchemaExtractor implements AnalysisTask<SyntaxNodeAnalysisContext> {
    CodeModifier.AnalysisData analysisData;
    private static final String STRING = "string";
    private static final String BYTE = "byte";
    private static final String NUMBER = "number";

    FunctionReturnTypeSchemaExtractor(CodeModifier.AnalysisData analysisData) {
        this.analysisData = analysisData;
    }
    @Override
    public void perform(SyntaxNodeAnalysisContext ctx) {
        SemanticModel semanticModel = ctx.semanticModel();
        ModulePartNode rootNode = (ModulePartNode) ctx.node();
        Optional<ModuleSymbol> npModule = findNPModule(semanticModel, rootNode);

        if (npModule.isEmpty()) {
            return;
        }

        String npModulePrefixStr = npModule.get().id().modulePrefix();
        TypeMapper typeMapper = new TypeMapperImpl(ctx);

        for (ModuleMemberDeclarationNode memberNode : rootNode.members()) {
            if (!isExternalFunctionWithLlmCall(memberNode, npModulePrefixStr)) {
                continue;
            }

            FunctionDefinitionNode functionDefinition = (FunctionDefinitionNode) memberNode;
            extractAndStoreSchemas(semanticModel, functionDefinition, this.analysisData.typeSchemas, typeMapper);
        }
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
            properties.values().forEach(FunctionReturnTypeSchemaExtractor::modifySchema);
        }

        List<Schema> allOf = schema.getAllOf();
        if (allOf != null) {
            schema.setType(null);
            allOf.forEach(FunctionReturnTypeSchemaExtractor::modifySchema);
        }

        List<Schema> anyOf = schema.getAnyOf();
        if (anyOf != null) {
            schema.setType(null);
            anyOf.forEach(FunctionReturnTypeSchemaExtractor::modifySchema);
        }

        List<Schema> oneOf = schema.getOneOf();
        if (oneOf != null) {
            schema.setType(null);
            oneOf.forEach(FunctionReturnTypeSchemaExtractor::modifySchema);
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
