package com.improvedlooting;

import java.lang.reflect.*;
import java.util.*;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImprovedLootingConfigProxyTest
{

    static ImprovedLootingConfig proxy(Map<String,String> saved) throws Exception
    {
        ConfigManager manager=mock(ConfigManager.class,CALLS_REAL_METHODS);
        doAnswer(call -> saved.get(call.getArgument(1))).when(manager).getConfiguration(eq("improvedlooting"),anyString());
        Constructor<?> constructor=Class.forName("net.runelite.client.config.ConfigInvocationHandler")
            .getDeclaredConstructor(ConfigManager.class);
        constructor.setAccessible(true);
        InvocationHandler handler=(InvocationHandler)constructor.newInstance(manager);
        return (ImprovedLootingConfig)Proxy.newProxyInstance(ImprovedLootingConfig.class.getClassLoader(),
            new Class<?>[]{ImprovedLootingConfig.class},handler);
    }

    @Test public void everyConfigDefaultCanBeReadThroughRunelitesProxy() throws Exception
    {
        ImprovedLootingConfig actual=proxy(Collections.emptyMap()),defaults=new ImprovedLootingConfig(){};
        for(Method method:ImprovedLootingConfig.class.getMethods()) if(method.isAnnotationPresent(ConfigItem.class))
            assertEquals(method.getName(),method.invoke(defaults),method.invoke(actual));
    }

    @Test public void savedLayoutEnumsRemainReadable() throws Exception
    {
        for(ImprovedLootingConfig.PriceSource source:ImprovedLootingConfig.PriceSource.values())assertSame(source,proxy(Collections.singletonMap("priceSource",source.name())).priceSource());
        for(ImprovedLootingConfig.LabelOrder order:ImprovedLootingConfig.LabelOrder.values())assertSame(order,proxy(Collections.singletonMap("labelOrder",order.name())).labelOrder());
        for(ImprovedLootingConfig.GroundStyle style:ImprovedLootingConfig.GroundStyle.values())assertSame(style,proxy(Collections.singletonMap("groundStyle",style.name())).groundStyle());
        for(ImprovedLootingConfig.LabelAlignment alignment:ImprovedLootingConfig.LabelAlignment.values())assertSame(alignment,proxy(Collections.singletonMap("labelAlignment",alignment.name())).labelAlignment());
    }
}
