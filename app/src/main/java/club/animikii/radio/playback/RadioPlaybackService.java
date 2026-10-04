package club.animikii.radio.playback;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** Owns live playback and reconnects the station stream after transient network failures. */
public final class RadioPlaybackService extends MediaSessionService {
    private static final String TAG = "RadioPlaybackService";
    private static final long BUFFERING_STALL_TIMEOUT_MS = 45_000L;
    private static final long NETWORK_RECOVERY_BUFFERING_MS = 10_000L;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private Handler playerHandler;
    private ConnectivityManager connectivityManager;
    private final ReconnectBackoff reconnectBackoff = new ReconnectBackoff();
    private boolean networkCallbackRegistered;
    private boolean internetValidated;
    private boolean defaultNetworkUnavailable;
    private boolean shouldAutoResume;
    private boolean reconnectScheduled;
    private boolean serviceDestroyed;
    private long bufferingStartedAtMs = -1L;

    private final Runnable reconnectRunnable = this::performReconnect;
    private final Runnable bufferingWatchdog = () -> {
        if (canAutoResume() && player.getPlaybackState() == Player.STATE_BUFFERING) {
            Log.w(TAG, "Stream is still buffering; scheduling a fresh connection attempt.");
            scheduleBackoffReconnect();
        }
    };

    private final Player.Listener recoveryListener = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                    || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE) {
                shouldAutoResume = playWhenReady;
                if (!playWhenReady) {
                    cancelRecovery();
                } else if (hasCurrentMediaItem()) {
                    if (player.getPlaybackState() == Player.STATE_IDLE
                            || player.getPlaybackState() == Player.STATE_ENDED) {
                        scheduleImmediateReconnect();
                    } else {
                        scheduleBufferingWatchdog();
                    }
                }
                return;
            }

            if (playWhenReady) {
                return;
            }
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                    || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG) {
                shouldAutoResume = false;
                cancelRecovery();
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                    && canAutoResume()) {
                scheduleBackoffReconnect();
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_BUFFERING) {
                bufferingStartedAtMs = SystemClock.elapsedRealtime();
                scheduleBufferingWatchdog();
            } else {
                cancelBufferingWatchdog();
            }
            if (playbackState == Player.STATE_ENDED) {
                scheduleBackoffReconnect();
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                reconnectBackoff.reset();
                cancelScheduledReconnect();
                cancelBufferingWatchdog();
            } else if (player.getPlaybackState() == Player.STATE_BUFFERING) {
                scheduleBufferingWatchdog();
            }
        }

        @Override
        public void onPlayerError(androidx.media3.common.PlaybackException error) {
            Log.w(TAG, "Live stream playback failed; retrying automatically.", error);
            scheduleBackoffReconnect();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    boolean networkReturned = defaultNetworkUnavailable;
                    defaultNetworkUnavailable = false;
                    refreshNetworkValidation(network);
                    if (networkReturned && canAutoResume()) {
                        scheduleImmediateReconnect();
                    } else {
                        maybeReconnectAfterNetworkRecovery();
                    }
                }

                @Override
                public void onCapabilitiesChanged(Network network,
                        NetworkCapabilities capabilities) {
                    boolean wasValidated = internetValidated;
                    internetValidated = capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    if (internetValidated && !wasValidated) {
                        maybeReconnectAfterNetworkRecovery();
                    }
                }

                @Override
                public void onLost(Network network) {
                    Network activeNetwork = connectivityManager == null
                            ? null : connectivityManager.getActiveNetwork();
                    if (activeNetwork == null) {
                        internetValidated = false;
                        defaultNetworkUnavailable = true;
                    } else {
                        defaultNetworkUnavailable = false;
                        refreshNetworkValidation(activeNetwork);
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        playerHandler = new Handler(Looper.getMainLooper());
        player = new ExoPlayer.Builder(this).build();
        player.addListener(recoveryListener);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(audioAttributes, true);
        player.setHandleAudioBecomingNoisy(true);
        mediaSession = new MediaSession.Builder(this, player).build();
        registerNetworkCallback();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (canAutoResume()) {
            // Keep recovery alive if the user dismisses the UI during a temporary outage.
            return;
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        serviceDestroyed = true;
        if (playerHandler != null) {
            playerHandler.removeCallbacks(reconnectRunnable);
            playerHandler.removeCallbacks(bufferingWatchdog);
        }
        if (networkCallbackRegistered && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // The callback may already have been unregistered by the system.
            }
            networkCallbackRegistered = false;
        }
        if (player != null) {
            player.removeListener(recoveryListener);
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback, playerHandler);
            networkCallbackRegistered = true;
            Network activeNetwork = connectivityManager.getActiveNetwork();
            refreshNetworkValidation(activeNetwork);
        } catch (RuntimeException error) {
            // Playback errors can still trigger retries without network monitoring.
            Log.w(TAG, "Could not register network-change monitoring.", error);
        }
    }

    private void refreshNetworkValidation(Network network) {
        if (connectivityManager == null || network == null) {
            internetValidated = false;
            defaultNetworkUnavailable = true;
            return;
        }
        defaultNetworkUnavailable = false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        internetValidated = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void maybeReconnectAfterNetworkRecovery() {
        if (!canAutoResume()) {
            return;
        }
        boolean stalledBuffering = player.getPlaybackState() == Player.STATE_BUFFERING
                && bufferingStartedAtMs >= 0L
                && SystemClock.elapsedRealtime() - bufferingStartedAtMs
                        >= NETWORK_RECOVERY_BUFFERING_MS;
        if (reconnectScheduled || stalledBuffering) {
            scheduleImmediateReconnect();
        }
    }

    private void scheduleBufferingWatchdog() {
        if (!canAutoResume() || player.getPlaybackState() != Player.STATE_BUFFERING) {
            return;
        }
        if (bufferingStartedAtMs < 0L) {
            bufferingStartedAtMs = SystemClock.elapsedRealtime();
        }
        playerHandler.removeCallbacks(bufferingWatchdog);
        playerHandler.postDelayed(bufferingWatchdog, BUFFERING_STALL_TIMEOUT_MS);
    }

    private void cancelBufferingWatchdog() {
        if (playerHandler != null) {
            playerHandler.removeCallbacks(bufferingWatchdog);
        }
        bufferingStartedAtMs = -1L;
    }

    private void scheduleBackoffReconnect() {
        if (!canAutoResume() || reconnectScheduled) {
            return;
        }
        long delayMs = reconnectBackoff.nextDelayMillis();
        Log.i(TAG, "Retrying the live stream in " + delayMs + " ms.");
        scheduleReconnect(delayMs);
    }

    private void scheduleImmediateReconnect() {
        if (!canAutoResume()) {
            return;
        }
        if (reconnectScheduled) {
            playerHandler.removeCallbacks(reconnectRunnable);
        }
        reconnectScheduled = true;
        playerHandler.post(reconnectRunnable);
    }

    private void scheduleReconnect(long delayMs) {
        reconnectScheduled = true;
        playerHandler.postDelayed(reconnectRunnable, delayMs);
    }

    private void performReconnect() {
        reconnectScheduled = false;
        if (!canAutoResume()) {
            return;
        }
        Log.i(TAG, "Preparing the live stream again.");
        if (player.getPlaybackState() != Player.STATE_IDLE) {
            player.stop();
        }
        player.seekToDefaultPosition();
        player.prepare();
        player.play();
        scheduleBufferingWatchdog();
    }

    private void cancelScheduledReconnect() {
        if (playerHandler != null) {
            playerHandler.removeCallbacks(reconnectRunnable);
        }
        reconnectScheduled = false;
    }

    private void cancelRecovery() {
        cancelScheduledReconnect();
        cancelBufferingWatchdog();
    }

    private boolean hasCurrentMediaItem() {
        return player != null && player.getMediaItemCount() > 0;
    }

    private boolean canAutoResume() {
        return !serviceDestroyed && shouldAutoResume && hasCurrentMediaItem();
    }
}
