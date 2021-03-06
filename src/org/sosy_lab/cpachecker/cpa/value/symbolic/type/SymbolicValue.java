/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.value.symbolic.type;

import java.io.Serializable;
import java.util.Optional;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.cpa.value.type.ValueVisitor;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Marker interface for symbolic values.
 * <p/>
 * Each class implementing this interface should provide an <code>equals(Object)</code> method
 * that allows checks for equality of symbolic values.
 */
@SuppressWarnings("serial") // we cannot set a UID for an interface
public interface SymbolicValue extends Value, Serializable {

  <T> T accept(SymbolicValueVisitor<T> pVisitor);

  /**
   * Returns the memory location this symbolic value represents.
   */
  Optional<MemoryLocation> getRepresentedLocation();

  SymbolicValue copyForLocation(MemoryLocation pLocation);

  /**
   * Returns a string representation of this symbolic value with symbolic expressions representing
   * a certain memory locations replaced with these locations.
   */
  String getRepresentation();

  @Override
  default <T> T accept(ValueVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }
}
