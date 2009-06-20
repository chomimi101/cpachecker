package cpa.pointeranalysis;

import static cpa.pointeranalysis.Memory.INVALID_POINTER;
import static cpa.pointeranalysis.Memory.NULL_POINTER;
import static cpa.pointeranalysis.Memory.UNKNOWN_POINTER;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import cpa.pointeranalysis.Memory.InvalidPointerException;
import cpa.pointeranalysis.Memory.MemoryAddress;
import cpa.pointeranalysis.Memory.PointerTarget;
import cpa.pointeranalysis.Memory.Variable;

/**
 * A pointer is a set of possible targets.
 */
public class Pointer {
  
  private int sizeOfTarget; 
  
  private Set<PointerTarget> targets;
  
  private int levelOfIndirection; // how many stars does this pointer have?
  
  public Pointer() {
    this(0);
  }

  public Pointer(PointerTarget target) {
    this();
    assign(target);
  }
  
  public Pointer(int levelOfIndirection) {
    this (-1, levelOfIndirection, new HashSet<PointerTarget>());
    
    // if uninitialized, pointer is null
    targets.add(NULL_POINTER);
  }
  
  private Pointer(int sizeOfTarget, int levelOfIndirection, Set<PointerTarget> targets) {
    this.sizeOfTarget = sizeOfTarget;
    this.levelOfIndirection = levelOfIndirection;
    this.targets = new HashSet<PointerTarget>(targets);
  }
  
  private Pointer deref(PointerTarget target, Memory memory)
                        throws InvalidPointerException {
    Pointer p;
    
    if (target instanceof Variable) {
      String varName = ((Variable)target).getVarName();
      p = memory.getPointer(varName);
      if (p == null) {
        // type error
        // (pointers with levelOfIndirection > 1 should always point to other pointers)
        throw new InvalidPointerException("The target of this pointer is not a pointer, but this is a pointer of pointer");
      }
    
    } else if (target instanceof MemoryAddress) {
      p = memory.getHeapPointer((MemoryAddress)target);
      if (p == null) {
        // assume, the heap is full of NULL_POINTERs where nothing has been
        // written
        // as this is a pointer of pointer, this is ok
        p = new Pointer(levelOfIndirection-1);
        memory.writeOnHeap((MemoryAddress)target, p);
      }
      
    } else if (target == NULL_POINTER || target == INVALID_POINTER) {
      // warning is printed elsewhere
      p = null;
      
    } else if (target == UNKNOWN_POINTER) {
      p = null;
      
    } else {
      throw new InvalidPointerException("Pointer to " + target + " cannot be dereferenced");
    }
    return p;
  }

  
  public void assign(Pointer rightHandSide, boolean dereferenceFirst, Memory memory)
                     throws InvalidPointerException {
    if (rightHandSide == null) {
      throw new IllegalArgumentException();
    }
    
    if (dereferenceFirst) {
      // calculate *this = rightHandSide
      for (PointerTarget target : targets) {
        Pointer p = deref(target, memory);
        
        if (p != null) {
          if (targets.size() == 1) {
            p.assign(rightHandSide);
          } else {
            p.join(rightHandSide);
          }
        } else {
          // p == null means the target was something like NULL or UNKNOWN
          // we cannot do more than ignore (warning will be printed elsewhere)
        }
      }

    } else {
      assign(rightHandSide);
    }
  }
  
  public void assign(Pointer rightHandSide) {
    if (rightHandSide == null) {
      throw new IllegalArgumentException();
    }
    // this adds all possible targets from the other pointer to this pointer
    targets.clear();
    targets.addAll(rightHandSide.targets);
  }

  public void assign(PointerTarget target) {
    if (target == null) {
      throw new IllegalArgumentException();
    }
    targets.clear();
    targets.add(target);
  }
  

  public void join(Pointer p) {
    if (p == null) {
      throw new IllegalArgumentException();
    }
    // this adds all targets from p to this pointer
    targets.addAll(p.targets);
  }
  
  public void addTarget(PointerTarget target) {
    if (target == null) {
      throw new IllegalArgumentException();
    }
    targets.add(target);
  }
  
  
  public void removeTarget(PointerTarget target) {
    if (target == null) {
      throw new IllegalArgumentException();
    }
    targets.remove(target);
  }
  
  public void removeAllTargets(Pointer other) {
    if (other == null) {
      throw new IllegalArgumentException();
    }
    targets.removeAll(other.targets);
  }
  

  public boolean isUnsafe() {
    return targets.contains(NULL_POINTER) || targets.contains(INVALID_POINTER);
  }
  
  public boolean isSafe() {
    return !(targets.contains(NULL_POINTER)
             || targets.contains(INVALID_POINTER)
             || targets.contains(UNKNOWN_POINTER));
  }
  
  public boolean isSubsetOf(Pointer other) {
    if (other == null) {
      throw new IllegalArgumentException();
    }
    return (this == other) || other.targets.containsAll(targets);
  }
  
  public boolean contains(PointerTarget target) {
    return targets.contains(target);
  }
  
  /**
   * This shifts the targets of the pointer.
   * The shift is given in elements, not in bytes (e.g. if this pointer is an
   * int*, shift==1 will shift 4 bytes). 
   */
  public void addOffset(int shift, boolean dereferenceFirst, Memory memory) throws InvalidPointerException {
    if (!hasSizeOfTarget()) {
      addUnknownOffset(dereferenceFirst, memory);
      
    } else {      
      if (dereferenceFirst) {
        // calculate (*this) += shift
        for (PointerTarget target : targets) {
          Pointer p = deref(target, memory);
          
          if (p != null) {
            p.addOffset(shift);
          } else {
            // p == null means the target was something like NULL or UNKNOWN
            // we cannot do more than ignore (warning will be printed elsewhere)
          }
        }
        
      } else {
        addOffset(shift);
      }
    }
  }
  
  public void addOffset(int shift) throws InvalidPointerException {
    Set<PointerTarget> newTargets = new HashSet<PointerTarget>();
    
    for (PointerTarget target : targets) {
      newTargets.add(target.addOffset(shift*sizeOfTarget));
    }
    targets = newTargets;
  }
  
  public void addUnknownOffset(boolean dereferenceFirst, Memory memory)
                               throws InvalidPointerException {
    if (dereferenceFirst) {
      // calculate (*this) += shift
      for (PointerTarget target : targets) {
        Pointer p = deref(target, memory);
        
        if (p != null) {
          p.addUnknownOffset();
        } else {
          // p == null means the target was something like NULL or UNKNOWN
          // we cannot do more than ignore (warning will be printed elsewhere)
        }
      }
      
    } else {
      addUnknownOffset();
    }
  }
  
  public void addUnknownOffset() throws InvalidPointerException {
    Set<PointerTarget> newTargets = new HashSet<PointerTarget>();
    
    for (PointerTarget target : targets) {
      newTargets.add(target.addUnknownOffset());
    }
    targets = newTargets;
  }

  public Pointer deref(Memory memory) throws InvalidPointerException {
    if (memory == null) {
      throw new IllegalArgumentException();
    }
    if (levelOfIndirection == 1) {
      throw new InvalidPointerException("The target of this pointer is not a pointer");
    }
    
    Pointer p = new Pointer(levelOfIndirection-1);
    p.targets.clear();
    for (PointerTarget target : targets) {
      
      if (target instanceof Variable) {
        String varName = ((Variable)target).getVarName();
        p.join(memory.getPointer(varName));
        if (p == null) {
          // type error
          // (pointers with levelOfIndirection > 1 should always point to other pointers)
          throw new InvalidPointerException("The target of this pointer is not a pointer, but this is a pointer of pointer");
        }
        
      } else if (target instanceof MemoryAddress) {
        p.join(memory.getHeapPointer((MemoryAddress)target));
        if (p == null) {
          // assume, the heap is full of NULL_POINTERs where nothing has been
          // written
          // as this is a pointer of pointer, this is ok
          p = new Pointer(levelOfIndirection-1);
          memory.writeOnHeap((MemoryAddress)target, p);
        }
      
      } else if (target == NULL_POINTER || target == INVALID_POINTER) {
        // warning is printed elsewhere
        p.addTarget(INVALID_POINTER);
        
      } else if (target == UNKNOWN_POINTER) {
        p.addTarget(UNKNOWN_POINTER);
        
      } else {
        throw new InvalidPointerException("Pointer to " + target + " cannot be dereferenced");
      }
      
    }
    return p;
  }
  
  public int getNumberOfTargets() {
    return targets.size();
  }
  
  /**
   * Checks if the size of the target of the pointer is known. 
   */
  public Set<PointerTarget> getTargets() {
    return Collections.unmodifiableSet(targets);
  }
  
  public boolean hasSizeOfTarget() {
    return sizeOfTarget != -1;
  }
  
  /**
   * Returns the size of the target of the pointer in bytes. The return value is
   * undefined, if the length is not known (i.e., if hasSizeOfTarget() returns false). 
   */
  public int getSizeOfTarget() {
    return sizeOfTarget;
  }

  /**
   * Set the size of the target of the pointer, if it was unknown before.
   */
  public void setSizeOfTarget(int sizeOfTarget) {
    // allow setting this value only once
    if (hasSizeOfTarget() && this.sizeOfTarget != sizeOfTarget) {
      throw new IllegalArgumentException();
    }
    if (sizeOfTarget <= 0) {
      throw new IllegalArgumentException();
    }
    this.sizeOfTarget = sizeOfTarget;
  }
  
  public boolean isPointerToPointer() {
    return levelOfIndirection > 1;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Pointer)) {
      return false;
    }
    return (other != null) && targets.equals(((Pointer)other).targets);
  }
  
  @Override
  public int hashCode() {
    return targets.hashCode();
  }
  
  @Override
  public Pointer clone() {
    return new Pointer(sizeOfTarget, levelOfIndirection, targets);
  }
  
  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < levelOfIndirection; i++) {
      sb.append("*");
    }
    sb.append("(");
    for (PointerTarget target : targets) {
      sb.append(" " + target + " ");
    }
    sb.append(")");
    return sb.toString();
  }
}