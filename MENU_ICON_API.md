# Local menu icon connection, version 1

Improved Looting optionally posts RuneLite `PluginMessage` events in namespace `improved-looting`, name `menu-icons-v1`, on the client thread before ordinary menu-open consumers.

Data contains `handled` (set of actual MenuEntry objects), `images` (entry-to-BufferedImage map), and `currentItem` (set identifying native-item pictures). A handled entry without an image means no visible custom icon. Images are read-only. Consumers must not save references across menu openings or use item ID alone as the identity. An empty message clears the snapshot on disable, profile/configuration change, or unavailable filters.

Improved Right-Clicks consumes this optional snapshot ahead of its legacy local assignments. Native ignores it; Custom shows only its visible image; Both adds a native item picture unless the supplied image is already that item. Both's existing visibility-following option suppresses the native picture when no custom icon is supplied. Colors and menu actions are unchanged.

This local prototype protocol is separate from the upstream Loot Filters PR's request/response protocol. No external service or plugin class dependency is involved.
