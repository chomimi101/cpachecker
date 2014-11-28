package org.sosy_lab.cpachecker.cpa.stator.policy;

import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.util.rationals.LinearExpression;

/**
 * Wrapper for a template.
 *
 * TODO: use a type for formula creation.
 */
public class Template {

  final LinearExpression linearExpression;
  final CVariableDeclaration declaration;

  public Template(LinearExpression pLinearExpression,
      CVariableDeclaration pDeclaration) {
    linearExpression = pLinearExpression;
    declaration = pDeclaration;
  }

  @Override
  public boolean equals(Object o) {
    return o != null &&
          o.getClass() == getClass() &&
        linearExpression.equals(((Template)o).linearExpression);
  }

  @Override
  public int hashCode() {
    return linearExpression.hashCode();
  }

  public String toString() {
    return linearExpression.toString();
  }
}
