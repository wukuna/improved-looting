package com.improvedlooting;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

final class ProfileIdentity
{
    private ProfileIdentity() {}

    static Long current(ConfigManager configs)
    {
        // Background config saves replace the profile object without switching profiles.
        ConfigProfile profile=configs.getProfile();
        return profile==null?null:profile.getId();
    }
}
