# Animikii Club for Android privacy policy

Last updated: October 4, 2026

This policy covers the Android app and the station services it connects to. Animikii-Co maintains the app; the station runs at animikii.club.

## In the app

The app has no account system and does not ask for your name, email, contacts, or device location. It has no advertising or analytics SDKs. Searches in the request list are filtered on your device, and the app does not save a profile, search history, or location.

The app fetches current-track details, recent song history, aggregate listener counts, and requestable songs from Animikii Club's public AzuraCast API. It streams audio from the station and loads HTTPS album art from URLs supplied by the API. Current album art is served from animikii.club, but an API-provided image URL could point to another host.

If you tap "Send request," the app sends the selected song's request ID to the station's public API. The app does not attach your name or an account ID. The request appears in the station's request queue.

## What the station can see

Connections to the API and audio stream expose your IP address and ordinary connection details to the services handling them. Icecast access logs can include the client IP, timestamp, requested stream, response status and size, referrer, user-agent, and connection duration. Which logs are kept and for how long depends on server settings.[5][11]

AzuraCast listener history can store an IP address, user-agent, connection start and end times, stream mount, a hash derived from the IP, user-agent, and mount, plus browser/device details and approximate location derived from the IP.[1][2] If GeoLite2 is enabled, the AzuraCast dashboard can show an estimated country, region, or city on a map. This is based on the network IP, not GPS, and can be inaccurate.[9]

The app calls the public Now Playing API and displays an aggregate listener count. It does not request the station's detailed listener list or map.[10]

## Retention and service providers

AzuraCast's playback-history setting controls how long song and detailed listener history is kept. It can be set to a number of days or to keep history indefinitely; cleanup follows that setting.[3][4] The station's current setting has not been checked, so this policy does not promise a specific retention period. Icecast logs have separate rotation settings.[5]

Station traffic is routed through Cloudflare Tunnel. Cloudflare may process connection metadata to route and protect requests. Whether additional Cloudflare logs are enabled, and how long they are kept, depends on the account settings; those settings have not been checked.[6][7][8]

## Contact

For privacy questions or requests about station listener records, email [lunarroze@animikii.co](mailto:lunarroze@animikii.co) (Animikii-Co).

## Sources

[1] https://github.com/AzuraCast/AzuraCast/blob/main/backend/src/Entity/Listener.php — AzuraCast listener record  
[2] https://github.com/AzuraCast/AzuraCast/blob/main/backend/src/Entity/Repository/ListenerRepository.php — AzuraCast listener collection and retention code  
[3] https://www.azuracast.com/docs/help/optimizing — AzuraCast playback-history retention  
[4] https://www.azuracast.com/docs/administration/sync-tasks — AzuraCast cleanup tasks  
[5] https://www.icecast.org/docs/icecast-trunk/config_file — Icecast logging configuration  
[6] https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel — Cloudflare Tunnel  
[7] https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/monitor-tunnels/logs — Cloudflare Tunnel logs  
[8] https://www.cloudflare.com/privacypolicy — Cloudflare Privacy Policy  
[9] https://www.maxmind.com/en/geoip/ip-geolocation-accuracy — MaxMind IP geolocation accuracy  
[10] https://www.azuracast.com/docs/developers/now-playing-data — AzuraCast Now Playing API  
[11] https://github.com/xiph/Icecast-Server/blob/master/src/logging.c — Icecast access log fields
