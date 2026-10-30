package com.improvedlooting.rs2f.model;
import lombok.Value;
/** Icon descriptions only: no disk access or image decoding in the evaluator. */
public abstract class BufferedImageProvider {
 @lombok.EqualsAndHashCode(callSuper=false) @Value public static class Sprite extends BufferedImageProvider { int spriteId,index; }
 @lombok.EqualsAndHashCode(callSuper=false) @Value public static class Item extends BufferedImageProvider { int itemId; }
 @lombok.EqualsAndHashCode(callSuper=false) @Value public static class File extends BufferedImageProvider { String filename; }
 @lombok.EqualsAndHashCode(callSuper=false) @Value public static class CurrentItem extends BufferedImageProvider {}
}
