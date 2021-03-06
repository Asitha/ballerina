/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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
package org.ballerinalang.langserver.command;

import com.google.gson.internal.LinkedTreeMap;
import org.ballerinalang.langserver.DocumentServiceKeys;
import org.ballerinalang.langserver.TextDocumentServiceUtil;
import org.ballerinalang.langserver.WorkspaceServiceContext;
import org.ballerinalang.langserver.common.LSCustomErrorStrategy;
import org.ballerinalang.langserver.common.UtilSymbolKeys;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageClient;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command Executor for Ballerina Workspace service execute command operation.
 * @since v0.964.0
 */
public class CommandExecutor {
    private static final String ARG_KEY = "argumentK";
    private static final String ARG_VALUE = "argumentV";
    private static final String RUNTIME_PKG_ALIAS = ".runtime";

    /**
     * Command Execution router.
     * @param params    Parameters for the command
     * @param context   Workspace service context
     */
    public static void executeCommand(ExecuteCommandParams params, WorkspaceServiceContext context) {
        switch (params.getCommand()) {
            case CommandConstants.CMD_IMPORT_PACKAGE:
                executeImportPackage(context);
                break;
            case CommandConstants.CMD_ADD_DOCUMENTATION:
                executeAddDocumentation(context);
                break;
            default:
                // Do Nothing
                break;
        }
    }

    /**
     * Execute the command, import package.
     * @param context   Workspace service context
     */
    private static void executeImportPackage(WorkspaceServiceContext context) {
        String documentUri = null;
        VersionedTextDocumentIdentifier textDocumentIdentifier = new VersionedTextDocumentIdentifier();
        
        for (Object arg : context.get(ExecuteCommandKeys.COMMAND_ARGUMENTS_KEY)) {
            if (((LinkedTreeMap) arg).get(ARG_KEY).equals(CommandConstants.ARG_KEY_DOC_URI)) {
                documentUri = (String) ((LinkedTreeMap) arg).get(ARG_VALUE);
                textDocumentIdentifier.setUri(documentUri);
                context.put(DocumentServiceKeys.FILE_URI_KEY, documentUri);
            } else if (((LinkedTreeMap) arg).get(ARG_KEY).equals(CommandConstants.ARG_KEY_PKG_NAME)) {
                context.put(ExecuteCommandKeys.PKG_NAME_KEY, (String) ((LinkedTreeMap) arg).get(ARG_VALUE));
            }
        }
        
        if (documentUri != null && context.get(ExecuteCommandKeys.PKG_NAME_KEY) != null) {
            String fileContent = context.get(ExecuteCommandKeys.DOCUMENT_MANAGER_KEY)
                        .getFileContent(Paths.get(URI.create(documentUri)));
            String[] contentComponents = fileContent.split("\\n|\\r\\n|\\r");
            int totalLines = contentComponents.length;
            int lastNewLineCharIndex = Math.max(fileContent.lastIndexOf("\n"), fileContent.lastIndexOf("\r"));
            int lastCharCol = fileContent.substring(lastNewLineCharIndex + 1).length();
            BLangPackage bLangPackage = TextDocumentServiceUtil.getBLangPackage(context,
                    context.get(ExecuteCommandKeys.DOCUMENT_MANAGER_KEY), false,
                    LSCustomErrorStrategy.class, false).get(0);
            context.put(DocumentServiceKeys.CURRENT_PACKAGE_NAME_KEY,
                    bLangPackage.symbol.getName().getValue());
            String pkgName = context.get(ExecuteCommandKeys.PKG_NAME_KEY);
            DiagnosticPos pos;

            // Filter the imports except the runtime import
            List<BLangImportPackage> imports = bLangPackage.getImports().stream()
                    .filter(bLangImportPackage -> !bLangImportPackage.getAlias().toString().equals(RUNTIME_PKG_ALIAS))
                    .collect(Collectors.toList());

            if (!imports.isEmpty()) {
                BLangImportPackage lastImport = bLangPackage.getImports().get(bLangPackage.getImports().size() - 1);
                pos = lastImport.getPosition();
            } else if (imports.isEmpty() && bLangPackage.getPackageDeclaration() != null) {
                pos = (DiagnosticPos) bLangPackage.getPackageDeclaration().getPosition();
            } else {
                pos = null;
            }

            int endCol = pos == null ? -1 : pos.getEndColumn() - 1;
            int endLine = pos == null ? 0 : pos.getEndLine() - 1;

            String remainingTextToReplace;

            if (endCol != -1) {
                int contentLengthToReplaceStart = fileContent.substring(0,
                        fileContent.indexOf(contentComponents[endLine])).length() + endCol + 1;
                remainingTextToReplace = fileContent.substring(contentLengthToReplaceStart);
            } else {
                remainingTextToReplace = fileContent;
            }

            String editText = (pos != null ? "\r\n" : "") + "import " + pkgName + ";"
                    + (remainingTextToReplace.startsWith("\n") || remainingTextToReplace.startsWith("\r") ? "" : "\r\n")
                    + remainingTextToReplace;
            Range range = new Range(new Position(endLine, endCol + 1), new Position(totalLines + 1, lastCharCol));

            applySingleTextEdit(editText, range, textDocumentIdentifier,
                    context.get(ExecuteCommandKeys.LANGUAGE_SERVER_KEY).getClient());
        }
    }

    /**
     * Execute the add documentation command.
     * @param context   Workspace service context
     */
    private static void executeAddDocumentation(WorkspaceServiceContext context) {
        String topLevelNodeType = "";
        String documentUri = "";
        String documentationContent = null;
        int line = 0;
        VersionedTextDocumentIdentifier textDocumentIdentifier = new VersionedTextDocumentIdentifier();
        for (Object arg : context.get(ExecuteCommandKeys.COMMAND_ARGUMENTS_KEY)) {
            if (((LinkedTreeMap) arg).get(ARG_KEY).equals(CommandConstants.ARG_KEY_DOC_URI)) {
                documentUri = (String) ((LinkedTreeMap) arg).get(ARG_VALUE);
                textDocumentIdentifier.setUri(documentUri);
                context.put(DocumentServiceKeys.FILE_URI_KEY, documentUri);
            } else if (((LinkedTreeMap) arg).get(ARG_KEY).equals(CommandConstants.ARG_KEY_NODE_TYPE)) {
                topLevelNodeType = (String) ((LinkedTreeMap) arg).get(ARG_VALUE);
            } else if (((LinkedTreeMap) arg).get(ARG_KEY).equals(CommandConstants.ARG_KEY_NODE_LINE)) {
                line = Integer.parseInt((String) ((LinkedTreeMap) arg).get(ARG_VALUE));
            }
        }

        BLangPackage bLangPackage = TextDocumentServiceUtil.getBLangPackage(context,
                context.get(ExecuteCommandKeys.DOCUMENT_MANAGER_KEY), false,
                LSCustomErrorStrategy.class, false).get(0);

        switch (topLevelNodeType) {
            case UtilSymbolKeys.FUNCTION_KEYWORD_KEY:
                documentationContent = CommandUtil.getFunctionDocumentation(bLangPackage, line);
                break;
            case UtilSymbolKeys.STRUCT_KEYWORD_KEY:
                documentationContent = CommandUtil.getStructDocumentation(bLangPackage, line);
                break;
            case UtilSymbolKeys.ENUM_KEYWORD_KEY:
                documentationContent = CommandUtil.getEnumDocumentation(bLangPackage, line);
                break;
            case UtilSymbolKeys.TRANSFORMER_KEYWORD_KEY:
                documentationContent = CommandUtil.getTransformerDocumentation(bLangPackage, line);
                break;
            case UtilSymbolKeys.RESOURCE_KEYWORD_KEY:
                CommandUtil.DocAttachmentInfo attachInfo = CommandUtil.getResourceDocumentation(bLangPackage, line);
                if (attachInfo != null) {
                    documentationContent = attachInfo.getDocAttachment();
                    line = attachInfo.getReplaceStartFrom();
                }
                break;
            case UtilSymbolKeys.SERVICE_KEYWORD_KEY:
                CommandUtil.DocAttachmentInfo serviceInfo = CommandUtil.getServiceDocumentation(bLangPackage, line);
                if (serviceInfo != null) {
                    documentationContent = serviceInfo.getDocAttachment();
                    line = serviceInfo.getReplaceStartFrom();
                }
                break;
            default:
                break;
        }

        if (documentationContent != null) {
            String fileContent = context.get(ExecuteCommandKeys.DOCUMENT_MANAGER_KEY)
                    .getFileContent(Paths.get(URI.create(documentUri)));
            String[] contentComponents = fileContent.split("\\n|\\r\\n|\\r");
            int replaceEndCol = contentComponents[line - 1].length();
            String replaceText = String.join(System.lineSeparator(),
                    Arrays.asList(Arrays.copyOfRange(contentComponents, 0, line))) + documentationContent;
            Range range = new Range(new Position(0, 0), new Position(line - 1, replaceEndCol));

            applySingleTextEdit(replaceText, range, textDocumentIdentifier,
                    context.get(ExecuteCommandKeys.LANGUAGE_SERVER_KEY).getClient());
        }
    }

    private static void applySingleTextEdit(String editText, Range range, VersionedTextDocumentIdentifier identifier,
                                            LanguageClient client) {
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams();
        TextEdit textEdit = new TextEdit(range, editText);
        TextDocumentEdit textDocumentEdit = new TextDocumentEdit(identifier,
                Collections.singletonList(textEdit));
        workspaceEdit.setDocumentChanges(Collections.singletonList(textDocumentEdit));
        applyWorkspaceEditParams.setEdit(workspaceEdit);
        client.applyEdit(applyWorkspaceEditParams);
    }
}
