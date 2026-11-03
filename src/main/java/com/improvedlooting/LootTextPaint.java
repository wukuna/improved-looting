package com.improvedlooting;

import java.awt.*;

final class LootTextPaint
{
    private static final float[] STOPS={0,1f/6,2f/6,3f/6,4f/6,5f/6,1};
    private static final Color[] COLORS={Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED};
    private static final Color[] CYCLE=new Color[256];
    static {for(int i=0;i<CYCLE.length;i++)CYCLE[i]=Color.getHSBColor(i/256f,.85f,1f);}
    static double phase(long nanos,int seconds){long period=Math.max(2,Math.min(30,seconds))*1000000000L;return Math.floorMod(nanos,period)/(double)period;}
    static float start(PriceTiers.Effect effect,float x,float width,double phase){return x+(effect==PriceTiers.Effect.WAVE_RIGHT?1:effect==PriceTiers.Effect.WAVE_LEFT?-1:0)*(float)phase*width;}
    static Paint paint(PriceTiers.Tier tier,int seconds,long nanos,int x,int width) {
        if(tier.effect==PriceTiers.Effect.FILTER)return null;
        if(tier.effect==PriceTiers.Effect.SOLID)return new Color(tier.color);
        double phase=phase(nanos,seconds);
        if(tier.effect==PriceTiers.Effect.RGB)return CYCLE[(int)(phase*256)&255];
        float span=Math.max(80,width);float start=start(tier.effect,x,span,phase);
        return new LinearGradientPaint(start,0,start+span,0,STOPS,COLORS,MultipleGradientPaint.CycleMethod.REPEAT);
    }
}
