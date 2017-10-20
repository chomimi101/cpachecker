/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.arg.witnessexport;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;
import static org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.SINK_NODE_ID;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CLabelNode;
import org.sosy_lab.cpachecker.cfa.postprocessing.global.CFACloner;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CVoidType;
import org.sosy_lab.cpachecker.core.counterexample.CExpressionToOrinalCodeVisitor;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingState;
import org.sosy_lab.cpachecker.cpa.threading.ThreadingTransferRelation;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.CFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.AssumeCase;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.ElementType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeFlag;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.automaton.VerificationTaskMetaData;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTree;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTreeFactory;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTrees;
import org.sosy_lab.cpachecker.util.expressions.LeafExpression;
import org.sosy_lab.cpachecker.util.expressions.Or;
import org.sosy_lab.cpachecker.util.expressions.Simplifier;
import org.w3c.dom.Element;

class WitnessWriter implements EdgeAppender {

  private static final EnumSet<KeyDef> INSUFFICIENT_KEYS =
      EnumSet.of(
          KeyDef.SOURCECODE,
          KeyDef.STARTLINE,
          KeyDef.ENDLINE,
          KeyDef.ORIGINFILE,
          KeyDef.OFFSET,
          KeyDef.ENDOFFSET,
          KeyDef.LINECOLS,
          KeyDef.ASSUMPTIONSCOPE,
          KeyDef.ASSUMPTIONRESULTFUNCTION);

  private static final Function<ARGState, ARGState> COVERED_TO_COVERING = new Function<ARGState, ARGState>() {

    @Override
    public ARGState apply(ARGState pChild) {
      ARGState child = pChild;
      // The child might be covered by another state
      // --> switch to the covering state
      if (child.isCovered()) {
        child = child.getCoveringState();
        assert !child.isCovered();
      }
      return child;
    }

  };

  static final Function<CFAEdgeWithAssumptions, CFAEdgeWithAssumptions> ASSUMPTION_FILTER =
      new Function<CFAEdgeWithAssumptions, CFAEdgeWithAssumptions>() {

        @Override
        @Nullable
        public CFAEdgeWithAssumptions apply(@Nullable CFAEdgeWithAssumptions pEdgeWithAssumptions) {
          int originalSize = pEdgeWithAssumptions.getExpStmts().size();
          List<AExpressionStatement> expressionStatements = Lists.newArrayListWithCapacity(originalSize);
          for (AExpressionStatement expressionStatement : pEdgeWithAssumptions.getExpStmts()) {
            AExpression assumption = expressionStatement.getExpression();
            if (!(assumption instanceof CBinaryExpression)) {
              expressionStatements.add(expressionStatement);
            } else {
              CBinaryExpression binExpAssumption = (CBinaryExpression) assumption;
              CExpression leftSide = binExpAssumption.getOperand1();
              CExpression rightSide = binExpAssumption.getOperand2();

              final CType leftType = leftSide.getExpressionType().getCanonicalType();
              final CType rightType = rightSide.getExpressionType().getCanonicalType();

              if (!(leftType instanceof CVoidType) || !(rightType instanceof CVoidType)) {

                boolean equalTypes = leftType.equals(rightType);

                FluentIterable<Class<? extends CType>> acceptedTypes =
                    FluentIterable.from(Collections.<Class<? extends CType>>singleton(CSimpleType.class));

                boolean leftIsAccepted = equalTypes || acceptedTypes.anyMatch(
                    pArg0 -> pArg0.isAssignableFrom(leftType.getClass()));

                boolean rightIsAccepted = equalTypes || acceptedTypes.anyMatch(
                    pArg0 -> pArg0.isAssignableFrom(rightType.getClass()));

                if (leftIsAccepted && rightIsAccepted) {
                  boolean leftIsConstant = isConstant(leftSide);
                  boolean leftIsPointer = !leftIsConstant && isEffectivelyPointer(leftSide);
                  boolean rightIsConstant = isConstant(rightSide);
                  boolean rightIsPointer = !rightIsConstant && isEffectivelyPointer(rightSide);
                  if (!(leftIsPointer && rightIsConstant) && !(leftIsConstant && rightIsPointer)) {
                    expressionStatements.add(expressionStatement);
                  }
                }
              }
            }
          }

          if (expressionStatements.size() == originalSize) {
            return pEdgeWithAssumptions;
          }
          return new CFAEdgeWithAssumptions(
              pEdgeWithAssumptions.getCFAEdge(),
              expressionStatements,
              pEdgeWithAssumptions.getComment());
        }

        private boolean isConstant(CExpression pLeftSide) {
          return pLeftSide.accept(new CExpressionVisitor<Boolean, RuntimeException>() {

            @Override
            public Boolean visit(CArraySubscriptExpression pIastArraySubscriptExpression)
                throws RuntimeException {
              return false;
            }

            @Override
            public Boolean visit(CFieldReference pIastFieldReference) throws RuntimeException {
              return false;
            }

            @Override
            public Boolean visit(CIdExpression pIastIdExpression) throws RuntimeException {
              return false;
            }

            @Override
            public Boolean visit(CPointerExpression pPointerExpression) throws RuntimeException {
              return false;
            }

            @Override
            public Boolean visit(CComplexCastExpression pComplexCastExpression)
                throws RuntimeException {
              return pComplexCastExpression.getOperand().accept(this);
            }

            @Override
            public Boolean visit(CBinaryExpression pIastBinaryExpression) throws RuntimeException {
              return pIastBinaryExpression.getOperand1().accept(this)
                  && pIastBinaryExpression.getOperand2().accept(this);
            }

            @Override
            public Boolean visit(CCastExpression pIastCastExpression) throws RuntimeException {
              return pIastCastExpression.getOperand().accept(this);
            }

            @Override
            public Boolean visit(CCharLiteralExpression pIastCharLiteralExpression)
                throws RuntimeException {
              return true;
            }

            @Override
            public Boolean visit(CFloatLiteralExpression pIastFloatLiteralExpression)
                throws RuntimeException {
              return true;
            }

            @Override
            public Boolean visit(CIntegerLiteralExpression pIastIntegerLiteralExpression)
                throws RuntimeException {
              return true;
            }

            @Override
            public Boolean visit(CStringLiteralExpression pIastStringLiteralExpression)
                throws RuntimeException {
              return true;
            }

            @Override
            public Boolean visit(CTypeIdExpression pIastTypeIdExpression) throws RuntimeException {
              return false;
            }

            @Override
            public Boolean visit(CUnaryExpression pIastUnaryExpression) throws RuntimeException {
              return pIastUnaryExpression.getOperand().accept(this);
            }

            @Override
            public Boolean visit(CImaginaryLiteralExpression PIastLiteralExpression)
                throws RuntimeException {
              return true;
            }

            @Override
            public Boolean visit(CAddressOfLabelExpression pAddressOfLabelExpression)
                throws RuntimeException {
              return false;
            }

          });
        }

        private boolean isEffectivelyPointer(CExpression pLeftSide) {
          return pLeftSide.accept(new DefaultCExpressionVisitor<Boolean, RuntimeException>() {

            @Override
            public Boolean visit(CComplexCastExpression pComplexCastExpression)
                throws RuntimeException {
              return pComplexCastExpression.getOperand().accept(this);
            }

            @Override
            public Boolean visit(CBinaryExpression pIastBinaryExpression) throws RuntimeException {
              return pIastBinaryExpression.getOperand1().accept(this)
                  || pIastBinaryExpression.getOperand2().accept(this);
            }

            @Override
            public Boolean visit(CCastExpression pIastCastExpression) throws RuntimeException {
              return pIastCastExpression.getOperand().accept(this);
            }

            @Override
            public Boolean visit(CUnaryExpression pIastUnaryExpression) throws RuntimeException {
              if (Arrays.asList(UnaryOperator.MINUS, UnaryOperator.TILDE).contains(pIastUnaryExpression.getOperator())) {
                return pIastUnaryExpression.getOperand().accept(this);
              }
              if (pIastUnaryExpression.getOperator().equals(UnaryOperator.AMPER)) {
                return true;
              }
              return visitDefault(pIastUnaryExpression);
            }

            @Override
            protected Boolean visitDefault(CExpression pExp) throws RuntimeException {
              return pExp.getExpressionType().getCanonicalType() instanceof CPointerType;
            }

          });
        }
  };

  private final WitnessOptions witnessOptions;
  private final CFA cfa;
  private final VerificationTaskMetaData verificationTaskMetaData;

  private final ExpressionTreeFactory<Object> factory;
  private final Simplifier<Object> simplifier;

  private final Multimap<String, NodeFlag> nodeFlags = LinkedHashMultimap.create();
  private final Multimap<String, Property> violatedProperties = HashMultimap.create();
  private final Map<DelayedAssignmentsKey, CFAEdgeWithAssumptions> delayedAssignments = Maps.newHashMap();

  private final Multimap<String, Edge> leavingEdges = LinkedHashMultimap.create();
  private final Multimap<String, Edge> enteringEdges = LinkedHashMultimap.create();

  private final Map<String, ExpressionTree<Object>> stateInvariants = Maps.newLinkedHashMap();
  private final Map<String, String> stateScopes = Maps.newLinkedHashMap();

  private final Map<Edge, CFANode> loopHeadEnteringEdges = Maps.newHashMap();

  private final String defaultSourcefileName;
  private final WitnessType graphType;

  private final InvariantProvider invariantProvider;

  private boolean isFunctionScope = false;

  WitnessWriter(
      WitnessOptions pOptions,
      CFA pCfa,
      VerificationTaskMetaData pMetaData,
      ExpressionTreeFactory<Object> pFactory,
      Simplifier<Object> pSimplifier,
      @Nullable String pDefaultSourceFileName,
      WitnessType pGraphType,
      InvariantProvider pInvariantProvider) {
    witnessOptions = pOptions;
    cfa = pCfa;
    verificationTaskMetaData = pMetaData;
    factory = pFactory;
    simplifier = pSimplifier;
    defaultSourcefileName = pDefaultSourceFileName;
    graphType = pGraphType;
    invariantProvider = pInvariantProvider;
  }

  @Override
  public void appendNewEdge(
      String pFrom,
      final String pTo,
      final CFAEdge pEdge,
      final Optional<Collection<ARGState>> pFromState,
      final Multimap<ARGState, CFAEdgeWithAssumptions> pValueMap) {

    attemptSwitchToFunctionScope(pEdge);

    TransitionCondition desc = constructTransitionCondition(pFrom, pTo, pEdge, pFromState, pValueMap);

    Edge edge = new Edge(pFrom, pTo, desc);
    if (desc.getMapping().containsKey(KeyDef.ENTERLOOPHEAD)) {
      Optional<CFANode> loopHead = entersLoop(pEdge);
      if (loopHead.isPresent()) {
        loopHeadEnteringEdges.put(edge, loopHead.get());
      }
    }

    putEdge(edge);
  }

  @Override
  public void appendNewEdgeToSink(
      String pFrom,
      CFAEdge pEdge,
      Optional<Collection<ARGState>> pFromState,
      Multimap<ARGState, CFAEdgeWithAssumptions> pValueMap) {
    appendNewEdge(pFrom, SINK_NODE_ID, pEdge, pFromState, pValueMap);
  }

  private void attemptSwitchToFunctionScope(CFAEdge pEdge) {
    if (isFunctionScope) {
      return;
    }
    if (AutomatonGraphmlCommon.isMainFunctionEntry(pEdge)) {
      isFunctionScope = true;
    }
  }

  /**
   * build a transition-condition for the given edge, i.e. collect all important data and store it
   * in the new transition-condition.
   */
  private TransitionCondition constructTransitionCondition(
      final String pFrom,
      final String pTo,
      final CFAEdge pEdge,
      final Optional<Collection<ARGState>> pFromState,
      final Multimap<ARGState, CFAEdgeWithAssumptions> pValueMap) {

    TransitionCondition result = TransitionCondition.empty();

    if (graphType != WitnessType.VIOLATION_WITNESS) {
      ExpressionTree<Object> invariant = ExpressionTrees.getTrue();
      if (exportInvariant(pEdge)) {
        invariant = simplifier.simplify(invariantProvider.provideInvariantFor(pEdge, pFromState));
      }
      putStateInvariant(pTo, invariant);
      String functionName = pEdge.getSuccessor().getFunctionName();
      stateScopes.put(pTo, isFunctionScope ? functionName : "");
    }

    if (!isFunctionScope || AutomatonGraphmlCommon.handleAsEpsilonEdge(pEdge)) {
      return result;
    }

    boolean goesToSink = pTo.equals(SINK_NODE_ID);
    boolean isDefaultCase = AutomatonGraphmlCommon.isDefaultCase(pEdge);

    if (entersLoop(pEdge, false).isPresent()) {
      result = result.putAndCopy(KeyDef.ENTERLOOPHEAD, "true");
    }

    if (witnessOptions.exportFunctionCallsAndReturns()) {
      if (pEdge.getSuccessor() instanceof FunctionEntryNode
          || AutomatonGraphmlCommon.isMainFunctionEntry(pEdge)) {
        String functionName = pEdge.getSuccessor().getFunctionName();
        result = result.putAndCopy(KeyDef.FUNCTIONENTRY, functionName);
      }
      if (pEdge.getSuccessor() instanceof FunctionExitNode) {
        FunctionExitNode out = (FunctionExitNode) pEdge.getSuccessor();
        result = result.putAndCopy(KeyDef.FUNCTIONEXIT, out.getFunctionName());
      }
    }

    if (pFromState.isPresent()) {
      result = extractTransitionForStates(pFrom, pTo, pEdge, pFromState.get(), pValueMap, result);
    }

    if (pEdge instanceof AssumeEdge) {
      AssumeEdge assumeEdge = (AssumeEdge) pEdge;
      // Check if the assume edge is an artificial edge introduced for pointer-calls
      if (CFAUtils.leavingEdges(assumeEdge.getPredecessor())
          .filter(AssumeEdge.class)
          .filter(a -> a.getTruthAssumption())
          .anyMatch(sibling ->
            CFAUtils.leavingEdges(sibling.getSuccessor())
                .filter(FunctionCallEdge.class)
                .filter(callEdge -> callEdge.getFileLocation().equals(sibling.getFileLocation()))
                .size() == 1)) {
        // If the assume edge is followed by a pointer call,
        // the assumption is artificial and should not be exported
        if (!goesToSink) {
          // remove all info from transitionCondition
          return TransitionCondition.empty();
        } else if (assumeEdge.getTruthAssumption() && witnessOptions.exportFunctionCallsAndReturns()) {
          // However, if we know that the function is not going to be called,
          // this information may be valuable and can be exported
          // by creating a transition for the function call, to the sink:
          FunctionCallEdge callEdge = Iterables.getOnlyElement(
              CFAUtils.leavingEdges(assumeEdge.getSuccessor()).filter(FunctionCallEdge.class));
          FunctionEntryNode in = callEdge.getSuccessor();
          result = result.putAndCopy(KeyDef.FUNCTIONENTRY, in.getFunctionName());
        }
      } else if (witnessOptions.exportAssumeCaseInfo()) {
        // Do not export assume-case information for the assume edges
        // representing continuations of switch-case chains
        if (assumeEdge.getTruthAssumption()
            || goesToSink
            || (isDefaultCase && !goesToSink)
            || !AutomatonGraphmlCommon.isPartOfSwitchStatement(assumeEdge)) {
          AssumeCase assumeCase = (assumeEdge.getTruthAssumption() != assumeEdge.isSwapped())
              ? AssumeCase.THEN
              : AssumeCase.ELSE;
          result = result.putAndCopy(KeyDef.CONTROLCASE, assumeCase.toString());
        } else {
          return TransitionCondition.empty();
        }
      }
    }

    Optional<FileLocation> minFileLocation = AutomatonGraphmlCommon.getMinFileLocation(pEdge, cfa.getMainFunction());
    Optional<FileLocation> maxFileLocation = AutomatonGraphmlCommon.getMaxFileLocation(pEdge, cfa.getMainFunction());
    if (witnessOptions.exportLineNumbers() && minFileLocation.isPresent()) {
      FileLocation min = minFileLocation.get();
      if (!min.getFileName().equals(defaultSourcefileName)) {
        result = result.putAndCopy(KeyDef.ORIGINFILE, min.getFileName());
      }
      result = result.putAndCopy(
          KeyDef.STARTLINE,
          Integer.toString(min.getStartingLineInOrigin()));
    }
    if (witnessOptions.exportLineNumbers() && maxFileLocation.isPresent()) {
      FileLocation max = maxFileLocation.get();
      result = result.putAndCopy(
          KeyDef.ENDLINE,
          Integer.toString(max.getEndingLineInOrigin()));
    }

    if (witnessOptions.exportOffset() && minFileLocation.isPresent()) {
      FileLocation min = minFileLocation.get();
      if (!min.getFileName().equals(defaultSourcefileName)) {
        result = result.putAndCopy(KeyDef.ORIGINFILE, min.getFileName());
      }
      result = result.putAndCopy(
          KeyDef.OFFSET,
          Integer.toString(min.getNodeOffset()));
    }
    if (witnessOptions.exportOffset() && maxFileLocation.isPresent()) {
      FileLocation max = maxFileLocation.get();
      result = result.putAndCopy(
          KeyDef.ENDOFFSET,
          Integer.toString(max.getNodeOffset() + max.getNodeLength() - 1));
    }

    if (witnessOptions.exportSourcecode()) {
      final String sourceCode;
      if (isDefaultCase && !goesToSink) {
        sourceCode = "default:";
      } else {
        sourceCode = pEdge.getRawStatement().trim();
      }
      if (!sourceCode.isEmpty()) {
        result = result.putAndCopy(KeyDef.SOURCECODE, sourceCode);
      }
    }

    return result;
  }

  private TransitionCondition extractTransitionForStates(
      final String pFrom,
      final String pTo,
      final CFAEdge pEdge,
      final Collection<ARGState> pFromStates,
      final Multimap<ARGState, CFAEdgeWithAssumptions> pValueMap,
      TransitionCondition result) {

    List<ExpressionTree<Object>> code = new ArrayList<>();
    Optional<AIdExpression> resultVariable = Optional.empty();
    Optional<String> resultFunction = Optional.empty();
    String functionName = pEdge.getPredecessor().getFunctionName();
    boolean isFunctionScope = this.isFunctionScope;

    for (ARGState state : pFromStates) {

      DelayedAssignmentsKey key = new DelayedAssignmentsKey(pFrom, pEdge, state);
      CFAEdgeWithAssumptions cfaEdgeWithAssignments = delayedAssignments.get(key);

      final CFAEdgeWithAssumptions currentEdgeWithAssignments;
      if (pValueMap != null
          && (currentEdgeWithAssignments = getFromValueMap(pValueMap, state, pEdge)) != null) {
        if (cfaEdgeWithAssignments == null) {
          cfaEdgeWithAssignments = currentEdgeWithAssignments;

        } else {
          Builder<AExpressionStatement> allAssignments = ImmutableList.builder();
          allAssignments.addAll(cfaEdgeWithAssignments.getExpStmts());
          allAssignments.addAll(currentEdgeWithAssignments.getExpStmts());
          cfaEdgeWithAssignments =
              new CFAEdgeWithAssumptions(
                  pEdge, allAssignments.build(), currentEdgeWithAssignments.getComment());
        }
      }

      if (cfaEdgeWithAssignments != null) {

        Collection<AExpressionStatement> assignments = cfaEdgeWithAssignments.getExpStmts();
        Predicate<AExpressionStatement> assignsParameterOfOtherFunction =
            new AssignsParameterOfOtherFunction(pEdge);
        Collection<AExpressionStatement> functionValidAssignments =
            Collections2.filter(assignments, assignsParameterOfOtherFunction);

        if (functionValidAssignments.size() < assignments.size()) {
          cfaEdgeWithAssignments =
              new CFAEdgeWithAssumptions(
                  pEdge, functionValidAssignments, cfaEdgeWithAssignments.getComment());
          FluentIterable<CFAEdge> nextEdges = CFAUtils.leavingEdges(pEdge.getSuccessor());

          if (nextEdges.size() == 1 && state.getChildren().size() == 1) {
            String keyFrom = pTo;
            CFAEdge keyEdge = Iterables.getOnlyElement(nextEdges);
            ARGState keyState = Iterables.getOnlyElement(state.getChildren());
            Collection<AExpressionStatement> valueAssignments =
                Collections2.filter(assignments, Predicates.not(assignsParameterOfOtherFunction));
            CFAEdgeWithAssumptions valueCFAEdgeWithAssignments =
                new CFAEdgeWithAssumptions(keyEdge, valueAssignments, "");
            delayedAssignments.put(
                new DelayedAssignmentsKey(keyFrom, keyEdge, keyState),
                valueCFAEdgeWithAssignments);
          }
        }

        // Determine the scope for static local variables
        for (AExpressionStatement functionValidAssignment : functionValidAssignments) {
          if (functionValidAssignment instanceof CExpressionStatement) {
            CExpression expression = (CExpression) functionValidAssignment.getExpression();
            for (CIdExpression idExpression :
                CFAUtils.getIdExpressionsOfExpression(expression).toSet()) {
              final CSimpleDeclaration declaration = idExpression.getDeclaration();
              final String qualified = declaration.getQualifiedName();
              if (declaration.getName().contains("static")
                  && !declaration.getOrigName().contains("static")
                  && qualified.contains("::")) {
                isFunctionScope = true;
                functionName = qualified.substring(0, qualified.indexOf("::"));
              }
            }
          }
        }

        // Do not export our own temporary variables
        Predicate<AIdExpression> isTmpVariable =
            idExpression ->
                idExpression
                    .getDeclaration()
                    .getQualifiedName()
                    .toUpperCase()
                    .contains("__CPACHECKER_TMP");
        assignments =
            Collections2.filter(
                cfaEdgeWithAssignments.getExpStmts(),
                statement ->
                    statement.getExpression() instanceof CExpression
                        && !CFAUtils.getIdExpressionsOfExpression(
                                (CExpression) statement.getExpression())
                            .anyMatch(isTmpVariable));

        // Export function return value for cases where it is not explicitly assigned to a variable
        if (pEdge instanceof AStatementEdge) {
          AStatementEdge edge = (AStatementEdge) pEdge;
          if (edge.getStatement() instanceof AFunctionCallAssignmentStatement) {
            AFunctionCallAssignmentStatement assignment =
                (AFunctionCallAssignmentStatement) edge.getStatement();
            if (assignment.getLeftHandSide() instanceof AIdExpression
                && assignment.getFunctionCallExpression().getFunctionNameExpression()
                    instanceof AIdExpression) {
              AIdExpression idExpression = (AIdExpression) assignment.getLeftHandSide();
              if (isTmpVariable.apply(idExpression)) {
                assignments =
                    Collections2.filter(
                        cfaEdgeWithAssignments.getExpStmts(),
                        statement ->
                            statement.getExpression() instanceof CExpression
                                && !CFAUtils.getIdExpressionsOfExpression(
                                        (CExpression) statement.getExpression())
                                    .anyMatch(
                                        id ->
                                            isTmpVariable.apply(id) && !id.equals(idExpression)));
                resultVariable = Optional.of(idExpression);
                AIdExpression resultFunctionName =
                    (AIdExpression)
                        assignment.getFunctionCallExpression().getFunctionNameExpression();
                if (resultFunctionName.getDeclaration() != null) {
                  resultFunction = Optional.of(resultFunctionName.getDeclaration().getOrigName());
                } else {
                  resultFunction = Optional.of(resultFunctionName.getName());
                }
              }
            }
          }
        }
        assert resultVariable.isPresent() == resultFunction.isPresent();

        if (!assignments.isEmpty()) {
          code.add(
              factory.and(
                  Collections2.transform(
                      assignments,
                      pExpressionStatement ->
                          LeafExpression.of(pExpressionStatement.getExpression()))));
        }
      }

      if (witnessOptions.exportThreadId()) {
        result = exportThreadId(result, pEdge, state);
      }
    }

    if (graphType != WitnessType.CORRECTNESS_WITNESS && witnessOptions.exportAssumptions() && !code.isEmpty()) {
      ExpressionTree<Object> invariant = factory.or(code);
      CExpressionToOrinalCodeVisitor transformer =
          resultVariable.isPresent()
              ? CExpressionToOrinalCodeVisitor.BASIC_TRANSFORMER.substitute(
                  (CIdExpression) resultVariable.get(), "\\result")
              : CExpressionToOrinalCodeVisitor.BASIC_TRANSFORMER;
      final Function<Object, String> converter =
          new Function<Object, String>() {

            @Override
            public String apply(Object pLeafExpression) {
              if (pLeafExpression instanceof CExpression) {
                return ((CExpression) pLeafExpression).accept(transformer);
              }
              if (pLeafExpression == null) {
                return "(0)";
              }
              return pLeafExpression.toString();
            }
          };
      final String assumptionCode;

      // If there are only conjunctions, use multiple statements
      // instead of the "&&" operator that is harder to parse.
      if (ExpressionTrees.isAnd(invariant)) {
        assumptionCode =
            Joiner.on("; ")
                .join(
                    ExpressionTrees.getChildren(invariant)
                        .transform(pTree -> ExpressionTrees.convert(pTree, converter)));
      } else {
        assumptionCode = ExpressionTrees.convert(invariant, converter).toString();
      }

      result = result.putAndCopy(KeyDef.ASSUMPTION, assumptionCode + ";");
      if (isFunctionScope) {
        if (witnessOptions.revertThreadFunctionRenaming()) {
          functionName = CFACloner.extractFunctionName(functionName);
        }
        result = result.putAndCopy(KeyDef.ASSUMPTIONSCOPE, functionName);
      }
      if (resultFunction.isPresent()) {
        result = result.putAndCopy(KeyDef.ASSUMPTIONRESULTFUNCTION, resultFunction.get());
      }
    }

    return result;
  }

  /** export the id of the executed thread into the witness.
   * We assume that the edge can be assigned to exactly one thread. */
  private TransitionCondition exportThreadId(TransitionCondition result, final CFAEdge pEdge,
      ARGState state) {
    ThreadingState threadingState = extractStateByType(state, ThreadingState.class);
    if (threadingState != null) {
      for (String threadId : threadingState.getThreadIds()) {
        if (threadingState.getThreadLocation(threadId).getLocationNode().equals(pEdge.getPredecessor())) {
          result = result.putAndCopy(KeyDef.THREADID, threadId);
          result = result.putAndCopy(KeyDef.THREAD, getUniqueThreadNum(threadId));
          result = exportThreadCreation(result, pEdge, state, threadingState);
          break;
        }
      }
    }
    return result;
  }

  private TransitionCondition exportThreadCreation(
      TransitionCondition result,
      final CFAEdge pEdge,
      ARGState state,
      ThreadingState threadingState) {
    if (pEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
      AStatement statement = ((AStatementEdge) pEdge).getStatement();
      if (statement instanceof AFunctionCall) {
        AExpression functionNameExp =
            ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          final String functionName = ((AIdExpression) functionNameExp).getName();
          if (ThreadingTransferRelation.THREAD_START.equals(functionName)) {
            // extract new thread-id from succeeding state. we assume there is only 'one' match
            ARGState child =
                from(state.getChildren()).firstMatch(c -> pEdge == state.getEdgeToChild(c)).get();
            // search the new created thread-id
            ThreadingState succThreadingState = extractStateByType(child, ThreadingState.class);
            for (String threadId : succThreadingState.getThreadIds()) {
              if (!threadingState.getThreadIds().contains(threadId)) {
                // we found the new created thread-id. we assume there is only 'one' match
                result = result.putAndCopy(KeyDef.CREATETHREAD, getUniqueThreadNum(threadId));
              }
            }
          }
        }
      }
    }
    return result;
  }

  private String getUniqueThreadNum(String threadId) {
    // TODO threadNum should be unique, hashCode might have collisions, but works for most cases.
    //      and as long as we do not support multiple LHS for thread-creation, it works.
    return threadId.hashCode() + "";
  }

  /**
   * Starting from the given initial ARG state, collects that state and all
   * transitive successors (as defined by the successor function) that are
   * children of their direct predecessor and are accepted by the path state
   * predicate.
   *
   * @param pInitialState the initial ARG state.
   * @param pSuccessorFunction the function defining the successors of a
   * state.
   * @param pPathStates a filter on the nodes.
   * @param pIsRelevantEdge a filter on the successor function.
   *
   * @return the parents with their children.
   */
  private Iterable<ARGState> collectPathNodes(
      final ARGState pInitialState,
      final Function<? super ARGState, ? extends Iterable<ARGState>> pSuccessorFunction,
      final Predicate<? super ARGState> pPathStates, Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge) {
    return Iterables.transform(
        collectPathEdges(pInitialState, pSuccessorFunction, pPathStates, pIsRelevantEdge), Pair::getFirst);
  }

  /**
   * Starting from the given initial ARG state, collects that state and all
   * transitive successors (as defined by the successor function) that are
   * children of their direct predecessor. Children are only computed for
   * nodes that are accepted by the path state predicate.
   *
   * @param pInitialState the initial ARG state.
   * @param pSuccessorFunction the function defining the successors of a
   * state.
   * @param pPathStates a filter on the parent nodes.
   * @param pIsRelevantEdge a filter on the successor function.
   *
   * @return the parents with their children.
   */
  private Iterable<Pair<ARGState, Iterable<ARGState>>> collectPathEdges(
      final ARGState pInitialState,
      final Function<? super ARGState, ? extends Iterable<ARGState>> pSuccessorFunction,
      final Predicate<? super ARGState> pPathStates,
      final Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge) {
    return new Iterable<Pair<ARGState, Iterable<ARGState>>>() {

      private final Set<ARGState> visited = new HashSet<>();

      private final Deque<ARGState> waitlist = new ArrayDeque<>();

      {
        waitlist.add(pInitialState);
        visited.add(pInitialState);
      }

      @Override
      public Iterator<Pair<ARGState, Iterable<ARGState>>> iterator() {
        return new Iterator<Pair<ARGState, Iterable<ARGState>>>() {

          @Override
          public boolean hasNext() {
            return !waitlist.isEmpty();
          }

          @Override
          public Pair<ARGState, Iterable<ARGState>> next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }
            assert !waitlist.isEmpty();
            final ARGState parent = waitlist.poll();

            // Get all children
            FluentIterable<ARGState> children =
                FluentIterable.from(pSuccessorFunction.apply(parent))
                    .transform(COVERED_TO_COVERING)
                    .filter(parent.getChildren()::contains);

            // Only the children on the path become parents themselves
            for (ARGState child : children.filter(pPathStates)) {
              if (pIsRelevantEdge.apply(Pair.of(parent, child)) && visited.add(child)) {
                waitlist.offer(child);
              }
            }

            return Pair.<ARGState, Iterable<ARGState>>of(parent, children);
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException("Removal not supported.");
          }
        };
      }
    };
  }

  public void writePath(
      Appendable pTarget,
      final ARGState pRootState,
      final Predicate<? super ARGState> pIsRelevantState,
      final Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
      Optional<CounterexampleInfo> pCounterExample,
      GraphBuilder pGraphBuilder)
      throws IOException {

    Predicate<? super Pair<ARGState, ARGState>> isRelevantEdge = pIsRelevantEdge;
    Multimap<ARGState, CFAEdgeWithAssumptions> valueMap = ImmutableMultimap.of();

    if (pCounterExample.isPresent()) {
      if (pCounterExample.get().isPreciseCounterExample()) {
        valueMap = Multimaps
            .transformValues(
                pCounterExample.get().getExactVariableValues(),
                ASSUMPTION_FILTER);
      } else {
        isRelevantEdge = edge -> pIsRelevantState.apply(edge.getFirst()) && pIsRelevantState.apply(edge.getSecond());
      }
    }

    final GraphMlBuilder doc;
    try {
      doc = new GraphMlBuilder(graphType, defaultSourcefileName, cfa, verificationTaskMetaData);
    } catch (ParserConfigurationException e) {
      throw new IOException(e);
    }

    final String entryStateNodeId = pGraphBuilder.getId(pRootState);

    // Collect node flags in advance
    for (ARGState s : collectPathNodes(pRootState, ARGState::getChildren, pIsRelevantState, isRelevantEdge)) {
      String sourceStateNodeId = pGraphBuilder.getId(s);
      EnumSet<NodeFlag> sourceNodeFlags = EnumSet.noneOf(NodeFlag.class);
      if (sourceStateNodeId.equals(entryStateNodeId)) {
        sourceNodeFlags.add(NodeFlag.ISENTRY);
      }
      sourceNodeFlags.addAll(extractNodeFlags(s));
      nodeFlags.putAll(sourceStateNodeId, sourceNodeFlags);
      violatedProperties.putAll(sourceStateNodeId, extractViolatedProperties(s));
    }
    // Write the sink node
    nodeFlags.put(SINK_NODE_ID, NodeFlag.ISSINKNODE);

    // Build the actual graph
    pGraphBuilder.buildGraph(
        pRootState,
        pIsRelevantState,
        isRelevantEdge,
        valueMap,
        doc,
        collectPathEdges(pRootState, ARGState::getChildren, pIsRelevantState, isRelevantEdge),
        this);

    // remove redundant edges leading to sink
    removeUnnecessarySinkEdges();

    // Merge nodes with empty or repeated edges
    Supplier<Iterator<Edge>> redundantEdgeIteratorSupplier =
        () -> FluentIterable.from(leavingEdges.values()).filter(isEdgeRedundant).iterator();

    Iterator<Edge> redundantEdgeIterator = redundantEdgeIteratorSupplier.get();
    while (redundantEdgeIterator.hasNext()) {
      Edge edge = redundantEdgeIterator.next();
      mergeNodes(edge);
      redundantEdgeIterator = redundantEdgeIteratorSupplier.get();
      assert leavingEdges.isEmpty() || leavingEdges.containsKey(entryStateNodeId);
    }

    // Write elements
    writeElementsOfGraphToDoc(doc, entryStateNodeId);
    doc.appendTo(pTarget);
  }

  /** Remove edges that lead to the sink but have a sibling edge that has the same label.
   *
   * <p>
   * We additionally remove redundant edges.
   * This is needed for concurrency witnesses at thread-creation.
   * </p>
   */
  private void removeUnnecessarySinkEdges() {
    final Collection<Edge> toRemove = Sets.newHashSet();
    for (Edge edge : leavingEdges.values()) {
      if (edge.target.equals(SINK_NODE_ID)) {
        for (Edge otherEdge : leavingEdges.get(edge.source)) {
          // ignore the edge itself, as well as already handled edges.
          if (!edge.equals(otherEdge) && !toRemove.contains(otherEdge)) {
            // remove edges with either identical labels or redundant edge-transition
            if (edge.label.equals(otherEdge.label) || isEdgeRedundant.apply(edge)) {
              toRemove.add(edge);
              break;
            }
          }
        }
      }
    }
    for (Edge edge : toRemove) {
      boolean removed = removeEdge(edge);
      assert removed;
    }
  }

  private void writeElementsOfGraphToDoc(GraphMlBuilder doc, String entryStateNodeId) {
    Map<String, Element> nodes = Maps.newHashMap();
    Deque<String> waitlist = Queues.newArrayDeque();
    waitlist.push(entryStateNodeId);
    Element entryNode = createNewNode(doc, entryStateNodeId);
    addInvariantsData(doc, entryNode, entryStateNodeId);
    nodes.put(entryStateNodeId, entryNode);
    while (!waitlist.isEmpty()) {
      String source = waitlist.pop();
      for (Edge edge : leavingEdges.get(source)) {
        setLoopHeadInvariantIfApplicable(edge.target);

        Element targetNode = nodes.get(edge.target);
        if (targetNode == null) {
          targetNode = createNewNode(doc, edge.target);
          if (!ExpressionTrees.getFalse()
              .equals(addInvariantsData(doc, targetNode, edge.target))) {
            waitlist.push(edge.target);
          }
          nodes.put(edge.target, targetNode);
        }
        createNewEdge(doc, edge, targetNode);
      }
    }
  }

  private void setLoopHeadInvariantIfApplicable(String pTarget) {
    if (!ExpressionTrees.getTrue().equals(getStateInvariant(pTarget))) {
      return;
    }
    ExpressionTree<Object> loopHeadInvariant = ExpressionTrees.getFalse();
    String scope = null;
    for (Edge enteringEdge : enteringEdges.get(pTarget)) {
      if (enteringEdge.label.getMapping().containsKey(KeyDef.ENTERLOOPHEAD)) {
        CFANode loopHead = loopHeadEnteringEdges.get(enteringEdge);
        if (loopHead != null) {
          String functionName = loopHead.getFunctionName();
          if (scope == null) {
            scope = functionName;
          } else if (!scope.equals(functionName)) {
            return;
          }
          for (CFAEdge enteringCFAEdge : CFAUtils.enteringEdges(loopHead)) {
            loopHeadInvariant =
                Or.of(
                    loopHeadInvariant,
                    invariantProvider.provideInvariantFor(enteringCFAEdge, Optional.empty()));
          }
        } else {
          return;
        }
      } else {
        return;
      }
    }
    stateInvariants.put(pTarget, loopHeadInvariant);
    if (scope != null) {
      stateScopes.put(pTarget, scope);
    }
  }

  private ExpressionTree<Object> addInvariantsData(
      GraphMlBuilder pDoc, Element pNode, String pStateId) {
    ExpressionTree<Object> tree = getStateInvariant(pStateId);
    if (!tree.equals(ExpressionTrees.getTrue())) {
      pDoc.addDataElementChild(pNode, KeyDef.INVARIANT, tree.toString());
      String scope = stateScopes.get(pStateId);
      if (scope != null && !scope.isEmpty() && !tree.equals(ExpressionTrees.getFalse())) {
        pDoc.addDataElementChild(pNode, KeyDef.INVARIANTSCOPE, scope);
      }
    }
    return tree;
  }

  private boolean hasFlagsOrProperties(String pNode) {
    return !nodeFlags.get(pNode).isEmpty() || !violatedProperties.get(pNode).isEmpty();
  }

  private final Predicate<String> isNodeRedundant =
      new Predicate<String>() {

        @Override
        public boolean apply(String pNode) {
          if (!ExpressionTrees.getTrue().equals(getStateInvariant(pNode))) {
            return false;
          }
          if (hasFlagsOrProperties(pNode)) {
            return false;
          }
          if (enteringEdges.get(pNode).isEmpty()) {
            return false;
          }
          for (Edge edge : enteringEdges.get(pNode)) {
            if (!edge.label.getMapping().isEmpty()) {
              return false;
            }
          }
          return true;
        }
      };

  private final Predicate<Edge> isEdgeRedundant =
      new Predicate<Edge>() {

        @Override
        public boolean apply(final Edge pEdge) {
          if (isNodeRedundant.apply(pEdge.target)) {
            return true;
          }

          if (pEdge.label.getMapping().isEmpty()) {
            return true;
          }

          if (pEdge.source.equals(pEdge.target)) {
            return false;
          }

          // An edge is never redundant if there are conflicting scopes
          ExpressionTree<Object> sourceTree = getStateInvariant(pEdge.source);
          if (sourceTree != null) {
            String sourceScope = stateScopes.get(pEdge.source);
            String targetScope = stateScopes.get(pEdge.target);
            if (sourceScope != null && targetScope != null && !sourceScope.equals(targetScope)) {
              return false;
            }
          }

          // An edge is redundant if it is the only leaving edge of a
          // node and it is empty or all its non-assumption contents
          // are summarized by a preceding edge
          boolean summarizedByPreceedingEdge =
              Iterables.any(
                  enteringEdges.get(pEdge.source),
                  pPrecedingEdge -> pPrecedingEdge.label.summarizes(pEdge.label));

          if ((!pEdge.label.hasTransitionRestrictions()
                  || summarizedByPreceedingEdge
                  || (pEdge.label.getMapping().size() == 1
                      && pEdge.label.getMapping().containsKey(KeyDef.FUNCTIONEXIT)))
              && (leavingEdges.get(pEdge.source).size() == 1)) {
            return true;
          }

          if (Iterables.all(
              leavingEdges.get(pEdge.source),
              pLeavingEdge -> pLeavingEdge.label.getMapping().isEmpty())) {
            return true;
          }

          if (witnessOptions.removeInsufficientEdges()) {
            if (INSUFFICIENT_KEYS.containsAll(pEdge.label.getMapping().keySet())) {
              return true;
            }
          }

          return false;
        }
      };

  /** Merge two consecutive nodes into one new node,
   * if the edge between the nodes is redundant.
   * The merge also merges the information of the nodes,
   * e.g. disjuncts their invariants. */
  private void mergeNodes(final Edge pEdge) {
    Preconditions.checkArgument(isEdgeRedundant.apply(pEdge));

    // Always merge into the predecessor, unless the successor is the sink
    boolean intoPredecessor = !nodeFlags.get(pEdge.target).equals(EnumSet.of(NodeFlag.ISSINKNODE));
    final String nodeToKeep = intoPredecessor ? pEdge.source : pEdge.target;
    final String nodeToRemove = intoPredecessor ? pEdge.target : pEdge.source;

    if (nodeToKeep.equals(nodeToRemove)) {
      removeEdge(pEdge);
      return;
    }

    // Merge the flags
    nodeFlags.putAll(nodeToKeep, nodeFlags.removeAll(nodeToRemove));

    // Merge the trees
    mergeExpressionTrees(nodeToKeep, nodeToRemove);

    // Merge the violated properties
    violatedProperties.putAll(nodeToKeep, violatedProperties.removeAll(nodeToRemove));

    // Move the leaving edges
    Collection<Edge> leavingEdgesToMove = ImmutableList.copyOf(this.leavingEdges.get(nodeToRemove));
    // Create the replacement edges,
    // Add them as leaving edges to the source node,
    // Add them as entering edges to their target nodes
    for (Edge leavingEdge : leavingEdgesToMove) {
      if (!pEdge.equals(leavingEdge)) {
        TransitionCondition label;
        // Don't merge "originfile" tag if leavingEdge corresponds to default originfile
        if (leavingEdge.label.getMapping().containsKey(KeyDef.SOURCECODE)) {
          label = pEdge.label.removeAndCopy(KeyDef.ORIGINFILE).putAllAndCopy(leavingEdge.label);
        } else {
          label = pEdge.label.putAllAndCopy(leavingEdge.label);
        }
        Edge replacementEdge = new Edge(nodeToKeep, leavingEdge.target, label);
        putEdge(replacementEdge);
        CFANode loopHead = loopHeadEnteringEdges.get(leavingEdge);
        if (loopHead != null) {
          loopHeadEnteringEdges.remove(leavingEdge);
          loopHeadEnteringEdges.put(replacementEdge, loopHead);
        }
      }
    }
    // Remove the old edges from their successors
    for (Edge leavingEdge : leavingEdgesToMove) {
      boolean removed = removeEdge(leavingEdge);
      assert removed;
    }

    // Move the entering edges
    Collection<Edge> enteringEdgesToMove = ImmutableList.copyOf(this.enteringEdges.get(nodeToRemove));
    // Create the replacement edges,
    // Add them as entering edges to the source node,
    // Add add them as leaving edges to their source nodes
    for (Edge enteringEdge : enteringEdgesToMove) {
      if (!pEdge.equals(enteringEdge)) {
        TransitionCondition label = pEdge.label.putAllAndCopy(enteringEdge.label);
        Edge replacementEdge = new Edge(enteringEdge.source, nodeToKeep, label);
        putEdge(replacementEdge);
        CFANode loopHead = loopHeadEnteringEdges.get(enteringEdge);
        if (loopHead != null) {
          loopHeadEnteringEdges.remove(enteringEdge);
          loopHeadEnteringEdges.put(replacementEdge, loopHead);
        }
      }
    }
    // Remove the old edges from their predecessors
    for (Edge enteringEdge : enteringEdgesToMove) {
      boolean removed = removeEdge(enteringEdge);
      assert removed : "could not remove edge: " + enteringEdge;
    }

  }

  private ExpressionTree<Object> getTargetStateInvariant(String pTargetState) {
    ExpressionTree<Object> targetStateInvariant = getStateInvariant(pTargetState);
    return targetStateInvariant;
  }

  /** Merge two expressionTrees for source and target.
   * We also perform some kind of simplification. */
  private void mergeExpressionTrees(final String source, final String target) {
    ExpressionTree<Object> sourceTree = getStateInvariant(source);
    ExpressionTree<Object> targetTree = getStateInvariant(target);
    String sourceScope = stateScopes.get(source);
    String targetScope = stateScopes.get(target);

    if (ExpressionTrees.getTrue().equals(targetTree)
        && !ExpressionTrees.getTrue().equals(sourceTree)
        && (targetScope == null || targetScope.equals(sourceScope))) {
      ExpressionTree<Object> newTargetTree = getTargetStateInvariant(target);
      newTargetTree = simplifier.simplify(factory.and(targetTree, newTargetTree));
      stateInvariants.put(target, newTargetTree);
      targetTree = newTargetTree;
    } else if (!ExpressionTrees.getTrue().equals(targetTree)
        && ExpressionTrees.getTrue().equals(sourceTree)
        && (sourceScope == null || sourceScope.equals(targetScope))
        && enteringEdges.get(source).size() <= 1) {
      ExpressionTree<Object> newSourceTree = ExpressionTrees.getFalse();
      for (Edge e : enteringEdges.get(source)) {
        newSourceTree = factory.or(newSourceTree, getStateInvariant(e.source));
      }
      newSourceTree = simplifier.simplify(factory.and(targetTree, newSourceTree));
      stateInvariants.put(source, newSourceTree);
      sourceTree = newSourceTree;
    }

    final String newScope;
    if (ExpressionTrees.isConstant(sourceTree)
        || Objects.equals(sourceScope, targetScope)) {
      newScope = targetScope;
    } else if (ExpressionTrees.isConstant(targetTree)) {
      newScope = sourceScope;
    } else {
      newScope = null;
    }
    ExpressionTree<Object> newTree = mergeStateInvariantsIntoFirst(source, target);
    if (newTree != null) {
      if (newScope == null && !ExpressionTrees.isConstant(newTree)) {
        putStateInvariant(source, ExpressionTrees.getTrue());
        stateScopes.remove(source);
      } else {
        stateScopes.put(source, newScope);
      }
    }
  }

  private void putEdge(Edge pEdge) {
    assert leavingEdges.size() == enteringEdges.size();
    assert !pEdge.source.equals(SINK_NODE_ID);
    leavingEdges.put(pEdge.source, pEdge);
    enteringEdges.put(pEdge.target, pEdge);
    assert leavingEdges.size() == enteringEdges.size();
  }

  private boolean removeEdge(Edge pEdge) {
    assert leavingEdges.size() == enteringEdges.size();
    if (leavingEdges.remove(pEdge.source, pEdge)) {
      boolean alsoRemoved = enteringEdges.remove(pEdge.target, pEdge);
      assert alsoRemoved : "edge was not removed: " + pEdge;
      assert leavingEdges.size() == enteringEdges.size();
      assert nodeFlags.get(pEdge.target).contains(NodeFlag.ISENTRY)
        || !enteringEdges.get(pEdge.target).isEmpty()
        || leavingEdges.get(pEdge.target).isEmpty();

      return true;
    }
    return false;
  }

  private Element createNewEdge(GraphMlBuilder pDoc, Edge pEdge, Element pTargetNode) {
    Element edge = pDoc.createEdgeElement(pEdge.source, pEdge.target);
    for (Map.Entry<KeyDef, String> entry : pEdge.label.getMapping().entrySet()) {
      KeyDef keyDef = entry.getKey();
      String value = entry.getValue();
      if (keyDef.keyFor.equals(ElementType.EDGE)) {
        pDoc.addDataElementChild(edge, keyDef, value);
      } else if (keyDef.keyFor.equals(ElementType.NODE)) {
        pDoc.addDataElementChild(pTargetNode, keyDef, value);
      }
    }
    return edge;
  }

  private Element createNewNode(GraphMlBuilder pDoc, String pEntryStateNodeId) {
    Element result = pDoc.createNodeElement(pEntryStateNodeId, NodeType.ONPATH);
    for (NodeFlag f : nodeFlags.get(pEntryStateNodeId)) {
      pDoc.addDataElementChild(result, f.key, "true");
    }
    for (Property violation : violatedProperties.get(pEntryStateNodeId)) {
      pDoc.addDataElementChild(result, KeyDef.VIOLATEDPROPERTY, violation.toString());
    }
    return result;
  }

  private Collection<NodeFlag> extractNodeFlags(ARGState pState) {
    if (pState.isTarget()) {
      return Collections.singleton(NodeFlag.ISVIOLATION);
    }
    return Collections.emptySet();
  }

  private Collection<Property> extractViolatedProperties(ARGState pState) {
    ArrayList<Property> result = Lists.newArrayList();
    if (pState.isTarget()) {
      result.addAll(pState.getViolatedProperties());
    }
    return result;
  }

  /**
   * Records the given invariant for the given state.
   *
   * If no invariant is present for this state, the given invariant is the new state invariant.
   * Otherwise, the new state invariant is a disjunction of the previous and the given invariant.
   *
   * However, if no invariants are ever added for a state, it is assumed to have the invariant "true".
   *
   * @param pStateId the state id.
   * @param pValue the invariant to be added.
   */
  private void putStateInvariant(String pStateId, ExpressionTree<Object> pValue) {
    ExpressionTree<Object> prev = stateInvariants.get(pStateId);
    if (prev == null) {
      stateInvariants.put(pStateId, simplifier.simplify(pValue));
      return;
    }
    ExpressionTree<Object> result = simplifier.simplify(factory.or(prev, pValue));
    stateInvariants.put(pStateId, result);
  }

  /**
   * Merges the invariants for the given state ids and stores it as the new invariant for the first of the given ids.
   *
   * @param pStateId the state id.
   * @param pOtherStateId the other state id.
   *
   * @return the merged invariant. {@code null} if neither state had an invariant.
   */
  private @Nullable ExpressionTree<Object> mergeStateInvariantsIntoFirst(
      String pStateId, String pOtherStateId) {
    ExpressionTree<Object> prev = stateInvariants.get(pStateId);
    ExpressionTree<Object> other = stateInvariants.get(pOtherStateId);
    if (prev == null) {
      stateInvariants.put(pStateId, other);
      return other;
    }
    if (other == null) {
      return prev;
    }
    ExpressionTree<Object> result = simplifier.simplify(factory.or(prev, other));
    stateInvariants.put(pStateId, result);
    return result;
  }

  private ExpressionTree<Object> getStateInvariant(String pStateId) {
    ExpressionTree<Object> result = stateInvariants.get(pStateId);
    if (result == null) {
      return ExpressionTrees.getTrue();
    }
    return result;
  }

  private boolean exportInvariant(CFAEdge pEdge) {
    if (AutomatonGraphmlCommon.handleAsEpsilonEdge(pEdge)) {
      return false;
    }
    if (entersLoop(pEdge).isPresent()) {
      return true;
    }

    CFANode referenceNode = pEdge.getSuccessor();
    Queue<CFANode> waitlist = Queues.newArrayDeque();
    Set<CFANode> visited = Sets.newHashSet();
    waitlist.offer(referenceNode);
    visited.add(referenceNode);
    for (CFAEdge assumeEdge : CFAUtils.enteringEdges(referenceNode).filter(AssumeEdge.class)) {
      if (visited.add(assumeEdge.getPredecessor())) {
        waitlist.offer(assumeEdge.getPredecessor());
      }
    }
    Predicate<CFAEdge> epsilonEdge = edge -> !(edge instanceof AssumeEdge);
    Predicate<CFANode> loopProximity =
        cfa.getAllLoopHeads().isPresent()
            ? pNode -> cfa.getAllLoopHeads().get().contains(pNode) || pNode.isLoopStart()
            : pNode -> pNode.isLoopStart();
    while (!waitlist.isEmpty()) {
      CFANode current = waitlist.poll();
      if (loopProximity.apply(current)) {
        return true;
      }
      for (CFAEdge enteringEdge : CFAUtils.enteringEdges(current).filter(epsilonEdge)) {
        if (visited.add(enteringEdge.getPredecessor())) {
          waitlist.offer(enteringEdge.getPredecessor());
        }
      }
    }
    return false;
  }

  private static Optional<CFANode> entersLoop(CFAEdge pEdge) {
    return entersLoop(pEdge, true);
  }

  private static Optional<CFANode> entersLoop(CFAEdge pEdge, boolean pAllowGoto) {
    class EnterLoopVisitor implements CFAVisitor {

      private CFANode loopHead;

      @Override
      public TraversalProcess visitNode(CFANode pNode) {
        if (pNode.isLoopStart()) {
          if (!pAllowGoto && pNode instanceof CLabelNode) {
            CLabelNode node = (CLabelNode) pNode;
            for (BlankEdge e : CFAUtils.enteringEdges(pNode).filter(BlankEdge.class)) {
              if (e.getDescription().equals("Goto: " + node.getLabel())) {
                return TraversalProcess.ABORT;
              }
            }
          }
          loopHead = pNode;
          return TraversalProcess.ABORT;
        }
        if (pNode.getNumLeavingEdges() > 1) {
          return TraversalProcess.SKIP;
        }
        return TraversalProcess.CONTINUE;
      }

      @Override
      public TraversalProcess visitEdge(CFAEdge pEdge) {
        return AutomatonGraphmlCommon.handleAsEpsilonEdge(pEdge)
            ? TraversalProcess.CONTINUE
            : TraversalProcess.SKIP;
      }
    }
    EnterLoopVisitor enterLoopVisitor = new EnterLoopVisitor();
    CFATraversal.dfs()
        .ignoreFunctionCalls()
        .ignoreSummaryEdges()
        .traverse(pEdge.getSuccessor(), enterLoopVisitor);
    return Optional.ofNullable(enterLoopVisitor.loopHead);
  }

  private static @Nullable CFAEdgeWithAssumptions getFromValueMap(
      Multimap<ARGState, CFAEdgeWithAssumptions> pValueMap, ARGState pState, CFAEdge pEdge) {
    Iterable<CFAEdgeWithAssumptions> assumptions = pValueMap.get(pState);
    assumptions = Iterables.filter(assumptions, a -> a.getCFAEdge().equals(pEdge));
    if (Iterables.isEmpty(assumptions)) {
      return null;
    }
    return Iterables.getOnlyElement(assumptions);
  }
}