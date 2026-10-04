package club.animikii.radio.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.junit.Test;

public class AboutItemTest {
    @Test
    public void exposesLocalPolicyAndLicenseAndExternalSourceLink() {
        assertArrayEquals(new String[] {"Privacy policy", "License", "Source code"},
                Arrays.stream(AboutItem.values())
                        .map(AboutItem::getLabel)
                        .toArray(String[]::new));

        assertEquals("privacy_policy.md", AboutItem.PRIVACY_POLICY.getAssetName());
        assertEquals("license.md", AboutItem.LICENSE.getAssetName());
        assertNull(AboutItem.PRIVACY_POLICY.getExternalUrl());
        assertNull(AboutItem.LICENSE.getExternalUrl());
        assertNull(AboutItem.SOURCE_CODE.getAssetName());
        assertEquals("https://github.com/Animikii-Co/animikii-club-android",
                AboutItem.SOURCE_CODE.getExternalUrl());
    }
}
