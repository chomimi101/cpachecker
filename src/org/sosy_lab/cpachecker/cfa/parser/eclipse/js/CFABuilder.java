/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cfa.parser.eclipse.js;

import com.google.common.collect.Lists;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.eclipse.wst.jsdt.core.dom.ASTVisitor;
import org.eclipse.wst.jsdt.core.dom.VariableDeclarationFragment;
import org.eclipse.wst.jsdt.core.dom.VariableDeclarationStatement;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.js.JSFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.js.JSVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.js.JSDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.js.JSFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.types.js.JSAnyType;
import org.sosy_lab.cpachecker.cfa.types.js.JSFunctionType;
import org.sosy_lab.cpachecker.util.Pair;

class CFABuilder extends ASTVisitor {
  private final Scope scope;
  private final LogManager logger;
  private final ASTConverter astConverter;

  private SortedMap<String, FunctionEntryNode> cfas = new TreeMap<>();
  private final SortedSetMultimap<String, CFANode> cfaNodes = TreeMultimap.create();
  private final List<Pair<ADeclaration, String>> globalDeclarations = Lists.newArrayList();

  final String functionName = "main";
  final JSFunctionDeclaration functionDeclaration =
      new JSFunctionDeclaration(
          FileLocation.DUMMY,
          new JSFunctionType(JSAnyType.ANY, Collections.emptyList()),
          functionName,
          Collections.emptyList());
  final FunctionExitNode exitNode = new FunctionExitNode(functionName);
  final JSFunctionEntryNode entryNode =
      new JSFunctionEntryNode(FileLocation.DUMMY, functionDeclaration, exitNode, Optional.empty());
  private JSDeclarationEdge edge;

  CFABuilder(Scope pScope, LogManager pLogger) {
    scope = pScope;
    logger = pLogger;
    astConverter = new ASTConverter(scope, logger);
  }

  @Override
  public boolean visit(VariableDeclarationStatement node) {
    @SuppressWarnings("unchecked")
    final List<VariableDeclarationFragment> variableDeclarationFragments = node.fragments();
    final CFANode declarationNode = new CFANode(functionName);
    cfaNodes.put(functionName, declarationNode);

    final VariableDeclarationFragment variableDeclarationFragment =
        variableDeclarationFragments.get(0);
    final JSVariableDeclaration variableDeclaration =
        astConverter.convert(variableDeclarationFragment);
    edge =
        new JSDeclarationEdge(
            variableDeclaration.toASTString(),
            astConverter.getFileLocation(variableDeclarationFragment),
            entryNode,
            declarationNode,
            variableDeclaration);
    CFACreationUtils.addEdgeToCFA(edge, logger);

    final VariableDeclarationFragment variableDeclarationFragment2 =
        variableDeclarationFragments.get(1);
    final JSVariableDeclaration variableDeclaration2 =
        astConverter.convert(variableDeclarationFragment2);
    final JSDeclarationEdge edge2 =
        new JSDeclarationEdge(
            variableDeclaration2.toASTString(),
            astConverter.getFileLocation(variableDeclarationFragment2),
            declarationNode,
            exitNode,
            variableDeclaration2);
    CFACreationUtils.addEdgeToCFA(edge2, logger);
    return super.visit(node);
  }

  public ParseResult createCFA() {
    exitNode.setEntryNode(entryNode);
    cfas.put(functionName, entryNode);
    cfaNodes.put(functionName, entryNode);
    cfaNodes.put(functionName, exitNode);
    return new ParseResult(
        cfas,
        cfaNodes,
        globalDeclarations,
        Language.JAVASCRIPT);
  }
}
