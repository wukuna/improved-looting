package com.improvedlooting.rs2f;
import com.improvedlooting.rs2f.ast.Condition;
import lombok.Value;
@Value public class FilterRule { Condition cond; DisplayConfig display; boolean terminal; int sourceLine; }
