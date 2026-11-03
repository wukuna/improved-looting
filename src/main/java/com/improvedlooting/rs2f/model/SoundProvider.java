package com.improvedlooting.rs2f.model;
import com.improvedlooting.rs2f.lang.Token;

@lombok.Value
public final class SoundProvider {
 Integer effect;
 String file;
 public static SoundProvider fromExpr(Token t) {
  if(t.getType()==Token.Type.LITERAL_INT) { int id=t.expectInt(); if(id<0 || id>100000) throw new IllegalArgumentException("Invalid sound ID"); return new SoundProvider(id,null); }
  return new SoundProvider(null,t.expectString());
 }
}
