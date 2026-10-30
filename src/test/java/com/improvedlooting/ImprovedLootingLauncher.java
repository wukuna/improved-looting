package com.improvedlooting;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ImprovedLootingLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ImprovedLootingPlugin.class);
        RuneLite.main(args);
    }
}
