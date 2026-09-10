package com.m00v13.tv;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NativeProviderDefinition {
    public final String name;
    public final List<String> mirrors;
    public final String searchPath;
    public final String rowSelector;
    public final String titleSelector;
    public final String detailsAttribute;
    public final String seedersSelector;
    public final String sizeSelector;
    public final String magnetSelector;
    public final int maxResults;

    public NativeProviderDefinition(String name, List<String> mirrors, String searchPath,
                                    String rowSelector, String titleSelector, String detailsAttribute,
                                    String seedersSelector, String sizeSelector, String magnetSelector,
                                    int maxResults) {
        this.name = name;
        this.mirrors = mirrors == null ? Collections.emptyList() : Collections.unmodifiableList(mirrors);
        this.searchPath = searchPath;
        this.rowSelector = rowSelector;
        this.titleSelector = titleSelector;
        this.detailsAttribute = detailsAttribute;
        this.seedersSelector = seedersSelector;
        this.sizeSelector = sizeSelector;
        this.magnetSelector = magnetSelector;
        this.maxResults = Math.max(1, maxResults);
    }

    public static List<NativeProviderDefinition> builtIns() {
        // Selectors and mirrors are intentionally data, not provider-specific code. The
        // first built-in mirrors current Prowlarr/Cardigann v11 behavior for 1337x.
        NativeProviderDefinition x1337 = new NativeProviderDefinition(
            "1337x",
            Arrays.asList(
                "https://1337x.to/",
                "https://1337x.st/",
                "https://x1337x.ws/",
                "https://x1337x.eu/",
                "https://x1337x.cc/"
            ),
            "search/{query}/1/",
            "tr:has(a[href^=/torrent/])",
            "td[class^=coll-1] a[href^=/torrent/]",
            "href",
            "td[class^=coll-2]",
            "td[class^=coll-4]",
            "ul li a[href^=magnet:]",
            16
        );
        return Collections.singletonList(x1337);
    }
}
