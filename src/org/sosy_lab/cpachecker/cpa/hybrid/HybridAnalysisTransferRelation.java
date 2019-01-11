/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.hybrid;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.hybrid.abstraction.HybridStrengthenOperator;
import org.sosy_lab.cpachecker.cpa.hybrid.abstraction.HybridValueProvider;
import org.sosy_lab.cpachecker.cpa.hybrid.exception.InvalidAssumptionException;
import org.sosy_lab.cpachecker.cpa.hybrid.util.ExpressionUtils;
import org.sosy_lab.cpachecker.cpa.hybrid.util.StrengthenOperatorFactory;
import org.sosy_lab.cpachecker.cpa.hybrid.value.HybridValue;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

@Options(prefix = "cpa.hybrid.transfer")
public class HybridAnalysisTransferRelation
    extends ForwardingTransferRelation<HybridAnalysisState, HybridAnalysisState, VariableTrackingPrecision> {


  @Option(secure = true,
          name = "removeValueOnAssumption",
          description = "Whether to remove a tracked value, if an assumption is handled by the transfer relation, containing the variable.")
  private boolean removeValueOnAssumption = false;

  private final CFA cfa;
  private final LogManager logger;

  private final AssumptionGenerator assumptionGenerator;

  private final StrengthenOperatorFactory strengthenOperatorFactory;
  private final HybridAnalysisStatistics statistics;

  public HybridAnalysisTransferRelation(
      CFA pCfa,
      LogManager pLogger,
      HybridValueProvider pValueProvider,
      Configuration pConfig,
      HybridAnalysisStatistics pStatistics) throws InvalidConfigurationException
  {
    pConfig.inject(this);
    this.cfa = pCfa;
    this.logger = pLogger;
    this.assumptionGenerator = new AssumptionGenerator(
        cfa.getMachineModel(),
        pLogger,
        pValueProvider);
    this.strengthenOperatorFactory = new StrengthenOperatorFactory(
        assumptionGenerator,
        logger,
        pConfig);

    statistics = pStatistics;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pState,
      List<AbstractState> otherStates,
      @Nullable CFAEdge cfaEdge,
      Precision pPrecision)
      throws CPATransferException, InterruptedException {
    // make sure the state to strengthen is of the correct domain
    assert pState instanceof HybridAnalysisState;

    // the correct operator will be generated by the factory
    HybridStrengthenOperator operator;
    HybridAnalysisState stateToStrengthen = (HybridAnalysisState) pState;

    for(AbstractState otherState : otherStates) {
      try {
        operator = strengthenOperatorFactory.provideStrengthenOperator(otherState);
        stateToStrengthen = operator.strengthen(stateToStrengthen, otherState, cfaEdge);
        super.setInfo(stateToStrengthen, pPrecision, cfaEdge);
      } catch (InvalidConfigurationException e) {
        throw new CPATransferException(
          String.format("Strengthening operator for %s cannot be created due to invalid configuration.",
            otherState),
          e);
      }
    }

    super.resetInfo();
    return Collections.singleton(stateToStrengthen);
  }


  // ----- AssumeEdge -----

  @Override
  protected @Nullable HybridAnalysisState handleAssumption(
      CAssumeEdge pCfaEdge, CExpression pExpression, boolean pTruthAssumption) {

    // if there is a new assumption for a tracked variable, we remove it
    if(removeValueOnAssumption) {
      Collection<CIdExpression> variables = ExpressionUtils.extractAllVariableIdentifiers(pExpression);
      Collection<CLeftHandSide> removeableVars = Sets.newHashSet(); 
      for(CIdExpression variable : variables) {
        if(state.tracksVariable(variable)) {
          removeableVars.add(variable);
          statistics.incrementRemovedOnAssumption();
        }
      }
      return HybridAnalysisState.removeOnAssignments(state, removeableVars);
    }

    return simpleCopy();
  }

  // ----- FunctionCallEdge -----

  @Override
  protected HybridAnalysisState handleFunctionCallEdge(
      CFunctionCallEdge cfaEdge,
      List<CExpression> arguments, List<CParameterDeclaration> parameters,
      String calledFunctionName) {

    statistics.incrementEmptyTransfer();
    return simpleCopy();
  }

  // ----- FunctionReturnEdge -----

  @Override
  protected HybridAnalysisState handleFunctionReturnEdge(CFunctionReturnEdge cfaEdge,
      CFunctionSummaryEdge fnkCall, CFunctionCall summaryExpr, String callerFunctionName) {

    statistics.incrementEmptyTransfer();
    return simpleCopy();
  }

  // ----- DeclarationEdge -----

  @Override
  protected HybridAnalysisState handleDeclarationEdge(
      CDeclarationEdge cfaEdge,
      CDeclaration pCDeclaration)
      throws CPATransferException {

    statistics.incrementEmptyTransfer();
    if(pCDeclaration instanceof CFunctionDeclaration
      || pCDeclaration instanceof CTypeDeclaration) {
      return simpleCopy();
    }

    // add new declaration to the hybrid analysis state
    return HybridAnalysisState.copyWithNewDeclaration(state, pCDeclaration);
  }

  // ----- ReturnStatementEdge -----

  @Override
  protected HybridAnalysisState handleReturnStatementEdge(CReturnStatementEdge cfaEdge)
      throws CPATransferException {
    
    statistics.incrementEmptyTransfer();
    return simpleCopy();
  }

  // ----- CallToReturnEdge -----

  @Override
  protected HybridAnalysisState handleFunctionSummaryEdge(CFunctionSummaryEdge cfaEdge) throws CPATransferException {
    statistics.incrementEmptyTransfer();
    return simpleCopy();
  }

  // ----- StatementEdge -----

  @Override
  protected HybridAnalysisState handleStatementEdge(
      CStatementEdge pCStatementEdge,
      CStatement pCStatement)
    throws  CPATransferException {

    // simple assignment
    if(pCStatement instanceof CExpressionAssignmentStatement) {

      // if an assumption for the target-variable exists, remove it
      CExpressionAssignmentStatement assignmentStatement = (CExpressionAssignmentStatement) pCStatement;
      CLeftHandSide leftHandSide = assignmentStatement.getLeftHandSide();

      @Nullable final String variableName = ExpressionUtils.extractVariableIdentifier(leftHandSide);

      if(variableName == null) {
        return simpleCopy();
      }

      // we need to remove the current assignment
      statistics.incrementRemovedOnAssignment();
      return HybridAnalysisState.removeOnAssignments(state, Collections.singleton(leftHandSide));

    }

    // function call assignment
    if(pCStatement instanceof CFunctionCallAssignmentStatement) {

      CFunctionCallAssignmentStatement statement = (CFunctionCallAssignmentStatement) pCStatement;
      return handleFunctionCallAssignment(statement);
    }

    return simpleCopy();
  }

  private HybridAnalysisState handleFunctionCallAssignment(
      CFunctionCallAssignmentStatement pFunctionCallAssignmentStatement)
      throws CPATransferException {

    CFunctionCallExpression functionCallExpression = pFunctionCallAssignmentStatement
        .getFunctionCallExpression();

    // variable carrying expression
    CLeftHandSide leftHandSide = pFunctionCallAssignmentStatement.getLeftHandSide();

    if(ExpressionUtils.isVerifierNondet(functionCallExpression)) {

      logger.log(Level.INFO, "Found nondet function call assignment: " , functionCallExpression);

      // function call is actually nondet
      try {

        @Nullable
        HybridValue newAssumption =
            assumptionGenerator.generateAssumption(leftHandSide);
        if(newAssumption == null) {
          statistics.incrementUnableGeneration();
          return simpleCopy();
        }

        statistics.incrementGeneratedNondet();
        return HybridAnalysisState.copyWithNewAssumptions(state, newAssumption);
      } catch (InvalidAssumptionException iae) {
        throw new CPATransferException(
            String.format(
                "Unable to generate assumption for function call assignment %s",
                pFunctionCallAssignmentStatement),
            iae);
      }

    } else {
      statistics.incrementRemovedOnAssignment();
      return HybridAnalysisState.removeOnAssignments(state, Collections.singleton(leftHandSide));
    }
  }

  private HybridAnalysisState simpleCopy() {
    return HybridAnalysisState.copyOf(state);
  }
}