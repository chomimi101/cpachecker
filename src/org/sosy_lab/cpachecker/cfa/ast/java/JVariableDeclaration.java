/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.ast.java;

import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.CFileLocation;
import org.sosy_lab.cpachecker.cfa.ast.CInitializer;
import org.sosy_lab.cpachecker.cfa.types.Type;


public class JVariableDeclaration extends AVariableDeclaration {

  private final VisibilityModifier visibility;
  private final boolean isFinal;
  private final boolean isStatic;
  private final boolean isTransient;
  private final boolean isVolatile;



  public JVariableDeclaration(CFileLocation pFileLocation, boolean pIsGlobal, Type pType, String pName,
      String pOrigName, CInitializer pInitializer, boolean pIsFinal,
      boolean pIsStatic , boolean pisTransient , boolean pisVolatile) {
    super(pFileLocation, pIsGlobal, pType, pName, pOrigName, pInitializer);

    isTransient = pisTransient;
    isVolatile =  pisVolatile;
    isFinal = pIsFinal;
    isStatic = pIsStatic;
    visibility = null;
  }



  public JVariableDeclaration(CFileLocation pFileLocation, boolean pIsGlobal, Type pType, String pName,
      String pOrigName, CInitializer pInitializer, boolean pIsFinal,
      boolean pIsStatic , boolean pisTransient , boolean pisVolatile,
      VisibilityModifier pVisibility) {
    super(pFileLocation, pIsGlobal, pType, pName, pOrigName, pInitializer);

    isTransient = pisTransient;
    isVolatile =  pisVolatile;
    isFinal = pIsFinal;
    isStatic = pIsStatic;
    visibility = pVisibility;
  }

  @Override
  public String toASTString() {
    StringBuilder lASTString = new StringBuilder();

    if(visibility != null){
    lASTString.append(visibility.getModifierString() + " ");
    }

    if(isFinal){
    lASTString.append("final ");
    }

    if(isStatic){
    lASTString.append("static ");
    }

    lASTString.append(getType().toASTString(getName()));

    if (initializer != null) {
      lASTString.append(" = ");
      lASTString.append(initializer.toASTString());
    }

    lASTString.append(";");
    return lASTString.toString();
  }

  public boolean isFinal() {
    return isFinal;
  }

  public boolean isStatic() {
    return isStatic;
  }

  public boolean isTransient() {
    return isTransient;
  }

  public boolean isVolatile() {
    return isVolatile;
  }

  public VisibilityModifier getVisibility(){
    return visibility;
  }
}