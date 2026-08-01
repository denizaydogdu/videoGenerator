package com.videogenerator.channel;

import com.google.gson.Gson;

/**
 * Test helper: builds valid channel profiles with overrides.
 */
public final class TestProfiles {
    private TestProfiles() {
    }

    public static ChannelProfile withSceneCount(int n) {
        ChannelProfile p = new Gson().fromJson(
                ChannelStoreTest.VALID.replace("\"sceneCount\":6", "\"sceneCount\":" + n),
                ChannelProfile.class);
        p.validate();
        return p;
    }
}
