package club.animikii.radio.core;

public enum AboutItem {
    PRIVACY_POLICY("Privacy policy", "privacy_policy.md", null),
    LICENSE("License", "license.md", null),
    SOURCE_CODE("Source code", null,
            "https://github.com/Animikii-Co/animikii-club-android");

    private final String label;
    private final String assetName;
    private final String externalUrl;

    AboutItem(String label, String assetName, String externalUrl) {
        this.label = label;
        this.assetName = assetName;
        this.externalUrl = externalUrl;
    }

    public String getLabel() {
        return label;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getExternalUrl() {
        return externalUrl;
    }
}
