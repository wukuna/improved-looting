package com.improvedlooting;

/** An item override wins over its matching rule, then the global default. */
enum LootMenuChoice
{
    DEFAULT("Follow default",null), HIDE("Hide when tag hidden",true), KEEP("Keep in menu",false);
    final Boolean hide;
    private final String label;
    LootMenuChoice(String label,Boolean hide){this.label=label;this.hide=hide;}
    static LootMenuChoice of(Boolean hide){return hide==null?DEFAULT:hide?HIDE:KEEP;}
    @Override public String toString(){return label;}
}
