package com.improvedlooting.rs2f.ast;
import java.util.List;
import com.improvedlooting.rs2f.FilterContext;
import com.improvedlooting.rs2f.model.ItemFacts;
import com.improvedlooting.rs2f.lang.ParseException;
/** Iterative expression evaluation avoids stack overflow on large generated OR lists. */
public final class PostfixCondition extends Condition {
 private final Condition[] code;
 private final int stackSize;
 public PostfixCondition(List<Condition> code) {
  if(code.size()>32768) throw new ParseException("Rule exceeds 32768 operations");
  this.code=code.toArray(new Condition[0]); int size=0,max=0;
  for(Condition c:this.code) {
   if(c instanceof LeafCondition) size++;
   else if(c instanceof NotCondition) { if(size<1) throw new ParseException("Invalid negation"); }
   else { if(size<2) throw new ParseException("Invalid boolean expression"); size--; }
   max=Math.max(max,size);
  }
  if(size!=1) throw new ParseException("Invalid expression"); stackSize=max;
 }
 @Override public boolean test(FilterContext context,ItemFacts item) {
  boolean[] stack=new boolean[stackSize]; int size=0;
  for(Condition c:code) {
   if(c instanceof LeafCondition) stack[size++]=c.test(context,item);
   else if(c instanceof NotCondition) stack[size-1]=!stack[size-1];
   else { boolean right=stack[--size]; stack[size-1]=c instanceof AndCondition ? stack[size-1] && right : stack[size-1] || right; }
  }
  return stack[0];
 }
}
