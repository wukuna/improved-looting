package com.improvedlooting;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.ui.FontManager;
import static com.improvedlooting.ImprovedLootingConfig.CardTheme;

final class LootCardPainter
{
    static final Color BACKGROUND=new Color(23,27,32,235),TEXT=new Color(236,239,243),VALUE=new Color(177,187,199);
    static Color background(CardTheme theme,Color chosen){return chosen!=null?chosen:theme==CardTheme.OSRS?new Color(55,44,28):BACKGROUND;}
    static Font font(CardTheme theme,int size,boolean bold)
    {
        if(theme==CardTheme.OSRS){Font base=bold?FontManager.getRunescapeBoldFont():FontManager.getRunescapeFont();return size==0?base:base.deriveFont((float)Math.max(8,Math.min(32,size)));}
        return new Font(Font.SANS_SERIF,bold?Font.BOLD:Font.PLAIN,size==0?12:Math.max(8,Math.min(32,size)));
    }
    static int height(CardTheme theme,FontMetrics metrics,int requestedHeight){return Math.max(metrics.getHeight()+(theme==CardTheme.OSRS?6:2),Math.max(0,Math.min(96,requestedHeight)));}
    static int iconSize(int height){return Math.max(1,height-2);}
    static int width(FontMetrics metrics,int iconSize,String name,long quantity,long price,boolean shortPrices,int seconds)
    {
        return iconSize+38+metrics.stringWidth(name+quantity(quantity))+metrics.stringWidth(details(price,shortPrices,seconds));
    }
    static String shortNumber(long value)
    {
        if(value<1000)return Long.toString(Math.max(0,value));
        double number=value;String[] suffix={"","k","m","b","t","q"};int unit=0;
        while(number>=999.95&&unit<suffix.length-1){number/=1000;unit++;}
        return String.format(Locale.ROOT,"%.1f",number).replaceAll("\\.0$","")+suffix[unit];
    }
    static double remaining(int firstSeen,int expiry,double now)
    {
        if(expiry<=0 || expiry==Integer.MAX_VALUE)return 1;
        if(expiry<=firstSeen)return now>=expiry?0:1;
        return Math.max(0,Math.min(1,(expiry-now)/(expiry-firstSeen)));
    }
    static String fit(String text,FontMetrics metrics,int width)
    {
        if(metrics.stringWidth(text)<=width)return text;
        String ellipsis="…";if(width<metrics.stringWidth(ellipsis))return "";
        int end=text.length();while(end>0&&metrics.stringWidth(text.substring(0,end)+ellipsis)>width)end--;
        return text.substring(0,end)+ellipsis;
    }
    static BufferedImage icon(BufferedImage original,int size,double remaining)
    { return icon(original,size,remaining,false); }
    static BufferedImage icon(BufferedImage original,int size,double remaining,boolean avoidEnlarging)
    {
        BufferedImage copy=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);if(original==null)return copy;
        Graphics2D g=copy.createGraphics();
        try{
            double scale=Math.min((double)size/original.getWidth(),(double)size/original.getHeight());if(avoidEnlarging)scale=Math.min(1,scale);int w=Math.max(1,(int)(original.getWidth()*scale)),h=Math.max(1,(int)(original.getHeight()*scale));
            g.drawImage(original,(size-w)/2,(size-h)/2,w,h,null);
            if(remaining<1){float feather=Math.max(2,size*.2f),front=(float)((1-Math.max(0,remaining))*(size+feather)-feather);
                g.setComposite(AlphaComposite.DstIn);g.setPaint(new LinearGradientPaint(0,front,0,front+feather,new float[]{0,1},new Color[]{new Color(255,255,255,0),Color.WHITE}));g.fillRect(0,0,size,size);}
        }finally{g.dispose();}return copy;
    }
    static String quantity(long quantity) { return quantity>1 ? " ("+String.format(Locale.ROOT,"%,d",quantity)+")" : ""; }
    static String details(long price,boolean shortPrices,int seconds)
    {
        return (price<=0?"":"("+(shortPrices?shortNumber(price):String.format(Locale.ROOT,"%,d",price))+" gp)")
            +(seconds<0?"":(price>0?" ":"")+"["+seconds+"s]");
    }
    static void surface(Graphics2D g,Rectangle bounds,boolean background,boolean border,int opacity,CardTheme theme,Color backgroundColor)
    {
        boolean osrs=theme==CardTheme.OSRS;
        if(background){Color color=background(theme,backgroundColor);g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),Math.round(Math.max(0,Math.min(100,opacity))*2.55f)));
            if(osrs)g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);else g.fillRoundRect(bounds.x,bounds.y,bounds.width,bounds.height,7,7);}
        if(border){g.setStroke(new BasicStroke(1));
            if(osrs){
                g.setColor(new Color(20,16,10,230));g.drawRect(bounds.x,bounds.y,bounds.width-1,bounds.height-1);
                g.setColor(new Color(155,130,81,230));g.drawLine(bounds.x+1,bounds.y+1,bounds.x+bounds.width-2,bounds.y+1);g.drawLine(bounds.x+1,bounds.y+1,bounds.x+1,bounds.y+bounds.height-2);
                g.setColor(new Color(90,69,41,230));g.drawLine(bounds.x+1,bounds.y+bounds.height-2,bounds.x+bounds.width-2,bounds.y+bounds.height-2);g.drawLine(bounds.x+bounds.width-2,bounds.y+1,bounds.x+bounds.width-2,bounds.y+bounds.height-2);
            }else{g.setColor(new Color(88,99,112,180));g.drawRoundRect(bounds.x,bounds.y,bounds.width-1,bounds.height-1,7,7);}
        }
    }
    static void row(Graphics2D original,Rectangle bounds,Paint rail,BufferedImage icon,String name,long quantity,long price,boolean hidden,boolean background,boolean border,int opacity,boolean shortPrices,int seconds,CardTheme theme,Color backgroundColor)
    {
        row(original,bounds,rail,icon,name,quantity,price,hidden,background,border,opacity,shortPrices,seconds,theme,backgroundColor,1);
    }
    static void row(Graphics2D original,Rectangle bounds,Paint rail,BufferedImage icon,String name,long quantity,long price,boolean hidden,boolean background,boolean border,int opacity,boolean shortPrices,int seconds,CardTheme theme,Color backgroundColor,double railRemaining)
    {
        Graphics2D g=(Graphics2D)original.create();
        try{
            g.clip(bounds);
            boolean osrs=theme==CardTheme.OSRS;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,osrs?RenderingHints.VALUE_ANTIALIAS_OFF:RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,osrs?RenderingHints.VALUE_TEXT_ANTIALIAS_OFF:RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            surface(g,bounds,background,border,opacity,theme,backgroundColor);
            Shape clip=g.getClip();if(!osrs)g.clip(new RoundRectangle2D.Double(bounds.x,bounds.y,bounds.width,bounds.height,7,7));g.setPaint(hidden?Color.GRAY:rail);
            int inset=osrs&&border?2:0,railHeight=bounds.height-inset*2;
            int drained=(int)Math.round(railHeight*(1-Math.max(0,Math.min(1,railRemaining))));
            g.fillRect(bounds.x+inset,bounds.y+inset+drained,osrs?3:4,railHeight-drained);g.setClip(clip);
            int size=icon==null?18:icon.getWidth(),left=bounds.x+10;
            if(icon!=null)g.drawImage(icon,left,bounds.y+(bounds.height-size)/2,null);
            FontMetrics metrics=g.getFontMetrics();String amount=details(price,shortPrices,seconds);int amountX=bounds.x+bounds.width-9-metrics.stringWidth(amount);
            int nameX=left+size+7;String suffix=quantity(quantity);
            String label=fit(name,metrics,Math.max(0,amountX-nameX-12-metrics.stringWidth(suffix)))+suffix;
            int baseline=bounds.y+(bounds.height-metrics.getHeight())/2+metrics.getAscent();
            Shape textClip=g.getClip();g.clipRect(nameX,bounds.y,Math.max(0,amountX-nameX-8),bounds.height);
            if(osrs){g.setColor(Color.BLACK);g.drawString(label,nameX+1,baseline+1);}
            g.setPaint(hidden?Color.GRAY:rail);g.drawString(label,nameX,baseline);g.setClip(textClip);
            if(osrs){g.setColor(Color.BLACK);g.drawString(amount,amountX+1,baseline+1);}
            g.setPaint(hidden?Color.GRAY:rail);g.drawString(amount,amountX,baseline);
        }finally{g.dispose();}
    }
    static Rectangle place(int anchorX,int anchorY,int width,int height,Rectangle viewport,java.util.List<Rectangle> occupied)
    {
        if(width>viewport.width || height>viewport.height)return null;
        int x=Math.max(viewport.x,Math.min(anchorX-width/2,viewport.x+viewport.width-width));
        int y=Math.max(viewport.y,Math.min(anchorY-height,viewport.y+viewport.height-height));
        Rectangle candidate=new Rectangle(x,y,width,height);
        for(int tries=0;tries<32;tries++){
            Rectangle hit=null;for(Rectangle box:occupied)if(candidate.intersects(box)){hit=box;break;}
            if(hit==null)return candidate;
            int above=hit.y-height-3;
            if(above>=viewport.y)candidate.y=above;
            else {candidate.x=hit.x+hit.width+4;candidate.y=y;if(candidate.x+width>viewport.x+viewport.width){candidate.x=hit.x-width-4;if(candidate.x<viewport.x)return null;}}
        }return null;
    }
}
