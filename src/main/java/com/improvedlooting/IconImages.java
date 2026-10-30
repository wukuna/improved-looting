package com.improvedlooting;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import net.runelite.client.util.Filepath;
final class IconImages {
 static String read(Filepath path) throws IOException {
  try(InputStream in=path.openInputStream()) {
   byte[] data=in.readNBytes(2097153);
   if(data.length>2097152) throw new IllegalArgumentException("Image exceeds 2 MB: "+path.getFileName());
   return encode(normalize(data));
  }
 }
 static BufferedImage normalize(byte[] data) throws IOException {
  try(MemoryCacheImageInputStream in=new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
   Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
   if(!readers.hasNext()) throw new IllegalArgumentException("Unsupported image. Use PNG, JPEG or GIF.");
   ImageReader r=readers.next();
   try {
    String format=r.getFormatName(); if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("gif")) throw new IllegalArgumentException("Use PNG, JPEG or GIF.");
    r.setInput(in); int w=r.getWidth(0),h=r.getHeight(0);
    if(w<1 || h<1 || w>2048 || h>2048 || (long)w*h>4194304) throw new IllegalArgumentException("Image dimensions exceed 2048 x 2048.");
    BufferedImage source=r.read(0),result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g=result.createGraphics();
    try { g.drawImage(source,0,0,null); } finally { g.dispose(); }
    return result;
   } finally { r.dispose(); }
  }
 }
 static String encode(BufferedImage image) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return Base64.getEncoder().encodeToString(out.toByteArray()); }
 static BufferedImage decode(String value) { try { if(value==null || value.length()>2800000) throw new IllegalArgumentException("Invalid saved icon"); return normalize(Base64.getDecoder().decode(value)); } catch(IOException e) { throw new IllegalArgumentException("Invalid saved icon",e); } }
 static String safeName(String name) {
  if(name==null || name.length()>240 || name.isBlank() || name.chars().anyMatch(Character::isISOControl) || name.startsWith("/") || name.startsWith("\\") || name.contains(":")) throw new IllegalArgumentException("Invalid image filename");
  String normalized=name.replace('\\','/');
  for(String part:normalized.split("/",-1)) if(part.equals("..") || part.equals(".") || part.isEmpty()) throw new IllegalArgumentException("Image filename must stay inside the selected icon folder");
  return normalized;
 }
}
