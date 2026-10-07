package club.animikii.radio.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import club.animikii.radio.core.TrackProgress;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

@UnstableApi
public class NowPlayingMediaSessionPlayerTest {
    @Test
    public void exposesTrackDurationAndElapsedPositionWithoutSeeking() {
        TrackProgress progress = new TrackProgress();
        progress.update(44L, 197d, 87d, 10_000L);
        AtomicLong clock = new AtomicLong(13_500L);

        NowPlayingMediaSessionPlayer player = new NowPlayingMediaSessionPlayer(
                emptyPlayer(), progress, clock::get);

        assertEquals(197_000L, player.getDuration());
        assertEquals(90_500L, player.getCurrentPosition());
        assertFalse(player.isCurrentMediaItemSeekable());
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM));
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION));
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_BACK));
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD));
        Player.Commands commands = player.getAvailableCommands();
        assertFalse(commands.contains(Player.COMMAND_SEEK_BACK));
        assertFalse(commands.contains(Player.COMMAND_SEEK_FORWARD));
        assertFalse(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM));
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_DEFAULT_POSITION));
    }

    @Test
    public void delegatesToTheStreamPlayerBeforeTrackMetadataArrives() {
        NowPlayingMediaSessionPlayer player = new NowPlayingMediaSessionPlayer(
                emptyPlayer(), new TrackProgress(), () -> 0L);

        assertEquals(123_000L, player.getDuration());
        assertEquals(12_000L, player.getCurrentPosition());
    }

    private static Player emptyPlayer() {
        Player.Commands delegateCommands = new Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .build();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAvailableCommands")) {
                        return delegateCommands;
                    }
                    if (method.getName().equals("getDuration")) {
                        return 123_000L;
                    }
                    if (method.getName().equals("getCurrentPosition")) {
                        return 12_000L;
                    }
                    if (method.getName().equals("getCurrentMediaItemSeekable")) {
                        return true;
                    }
                    if (method.getName().equals("getCurrentMediaItemLive")) {
                        return false;
                    }
                    if (method.getName().equals("isCommandAvailable")) {
                        return true;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return C.TIME_UNSET;
                    }
                    return null;
                });
    }
}
