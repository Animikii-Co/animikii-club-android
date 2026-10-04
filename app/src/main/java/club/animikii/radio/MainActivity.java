package club.animikii.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import club.animikii.radio.core.AzuraCastParser;
import club.animikii.radio.core.NowPlayingMetadata;
import club.animikii.radio.core.PlaybackButtonState;
import club.animikii.radio.core.RequestPagination;
import club.animikii.radio.core.RequestSearch;
import club.animikii.radio.core.StationRoutes;
import club.animikii.radio.data.AzuraCastRepository;
import club.animikii.radio.playback.RadioPlaybackService;
import club.animikii.radio.playback.StationMediaItemFactory;
import club.animikii.radio.ui.ImageLoader;
import com.google.common.util.concurrent.ListenableFuture;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Main Animikii Club screen: live player, recent history, and listener requests. */
public final class MainActivity extends Activity {
    private static final int COLOR_BACKGROUND = Color.rgb(17, 19, 24);
    private static final int COLOR_SURFACE = Color.rgb(26, 31, 39);
    private static final int COLOR_SURFACE_HIGH = Color.rgb(36, 43, 53);
    private static final int COLOR_ACCENT = Color.rgb(229, 0, 0);
    private static final int COLOR_TEXT = Color.rgb(244, 246, 250);
    private static final int COLOR_MUTED = Color.rgb(155, 166, 180);
    private static final int COLOR_GREEN = Color.rgb(101, 214, 160);
    private static final int COLOR_RED = Color.rgb(255, 133, 133);
    private static final int REQUEST_PAGE_SIZE = 20;
    private static final int REQUEST_PREFETCH_DISTANCE_DP = 240;
    private static final long NOW_PLAYING_REFRESH_MS = 30_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(2);
    private final AzuraCastRepository repository = new AzuraCastRepository();
    private final RequestPagination requestPagination = new RequestPagination(REQUEST_PAGE_SIZE);
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlaybackButton();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updatePlaybackButton();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            updatePlaybackButton();
        }
    };

    private final Runnable nowPlayingPoll = new Runnable() {
        @Override
        public void run() {
            if (!activityStarted) {
                return;
            }
            fetchNowPlaying();
            mainHandler.postDelayed(this, NOW_PLAYING_REFRESH_MS);
        }
    };

    private ImageLoader imageLoader;
    private FrameLayout screenHost;
    private ImageView[] navIcons;
    private TextView[] navLabels;
    private View[] navIndicators;
    private int selectedTab;
    private boolean activityStarted;
    private boolean nowPlayingFetchInFlight;
    private boolean requestFetchInFlight;
    private boolean requestsLoaded;
    private List<AzuraCastParser.RequestableSong> requestableSongs = Collections.emptyList();
    private AzuraCastParser.NowPlaying nowPlaying;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;

    private TextView liveBadge;
    private TextView listenersBadge;
    private TextView djLine;
    private TextView playingTitle;
    private TextView playingArtist;
    private TextView playingAlbum;
    private ImageView playingArtwork;
    private ImageButton playButton;
    private ProgressBar playSpinner;
    private EditText requestSearch;
    private TextView requestStatus;
    private LinearLayout requestRows;
    private ScrollView requestScrollView;
    private List<AzuraCastParser.RequestableSong> filteredRequestableSongs = Collections.emptyList();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageLoader = new ImageLoader();
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        buildLayout();
        showTab(0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStarted = true;
        connectMediaController();
        updatePlaybackButton();
        fetchNowPlaying();
        mainHandler.removeCallbacks(nowPlayingPoll);
        mainHandler.postDelayed(nowPlayingPoll, NOW_PLAYING_REFRESH_MS);
    }

    @Override
    protected void onStop() {
        activityStarted = false;
        mainHandler.removeCallbacks(nowPlayingPoll);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(nowPlayingPoll);
        networkExecutor.shutdownNow();
        if (imageLoader != null) {
            imageLoader.close();
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
            mediaController = null;
        }
        super.onDestroy();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int leftInset;
            int topInset;
            int rightInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                leftInset = systemBars.left;
                topInset = systemBars.top;
                rightInset = systemBars.right;
                bottomInset = systemBars.bottom;
            } else {
                leftInset = insets.getSystemWindowInsetLeft();
                topInset = insets.getSystemWindowInsetTop();
                rightInset = insets.getSystemWindowInsetRight();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(leftInset, topInset, rightInset, bottomInset);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(18), dp(10));
        header.setBackgroundColor(COLOR_BACKGROUND);

        ImageView stationIcon = new ImageView(this);
        stationIcon.setImageResource(R.drawable.station_icon_header);
        stationIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stationIcon.setBackground(rounded(Color.BLACK, 12));
        stationIcon.setClipToOutline(true);
        stationIcon.setContentDescription("Animikii Club station icon");
        header.addView(stationIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandParams.setMargins(dp(12), 0, dp(8), 0);
        header.addView(brandText, brandParams);

        TextView brandName = text("Animikii Club", 14, COLOR_TEXT, true, Gravity.CENTER_VERTICAL);
        brandName.setLetterSpacing(0.02f);
        brandText.addView(brandName);
        TextView brandSubtitle = text("Eclectic001 Turtle Island Ojibwe Edition", 9, COLOR_MUTED,
                true, Gravity.CENTER_VERTICAL);
        brandSubtitle.setLetterSpacing(0f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        brandText.addView(brandSubtitle, subtitleParams);

        ImageButton shareButton = iconButton(R.drawable.ic_share, "Share Animikii Radio",
                44, COLOR_MUTED, COLOR_SURFACE);
        shareButton.setOnClickListener(view -> shareStation());
        header.addView(shareButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        screenHost = new FrameLayout(this);
        root.addView(screenHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(createBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        setContentView(root);
    }

    private LinearLayout createBottomNavigation() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(COLOR_SURFACE);
        int[] icons = {R.drawable.ic_nav_listen, R.drawable.ic_nav_history,
                R.drawable.ic_nav_request};
        String[] labels = {"LISTEN", "HISTORY", "REQUESTS"};
        navIcons = new ImageView[labels.length];
        navLabels = new TextView[labels.length];
        navIndicators = new View[labels.length];

        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(labels[i].toLowerCase(Locale.ROOT) + " tab");

            View indicator = new View(this);
            navIndicators[i] = indicator;
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(30), dp(2));
            item.addView(indicator, indicatorParams);

            ImageView icon = new ImageView(this);
            navIcons[i] = icon;
            icon.setImageResource(icons[i]);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            iconParams.topMargin = dp(7);
            item.addView(icon, iconParams);

            TextView label = text(labels[i], 9, COLOR_MUTED, true, Gravity.CENTER);
            label.setLetterSpacing(0.07f);
            navLabels[i] = label;
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(3);
            item.addView(label, labelParams);
            item.setOnClickListener(view -> showTab(tab));

            bar.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return bar;
    }

    private void updateBottomNavigation() {
        if (navIcons == null) {
            return;
        }
        for (int i = 0; i < navIcons.length; i++) {
            boolean selected = i == selectedTab;
            navIcons[i].setColorFilter(selected ? COLOR_ACCENT : COLOR_MUTED);
            navLabels[i].setTextColor(selected ? COLOR_TEXT : COLOR_MUTED);
            navIndicators[i].setBackgroundColor(selected ? COLOR_ACCENT : COLOR_SURFACE);
        }
    }

    private void showTab(int tab) {
        selectedTab = tab;
        updateBottomNavigation();
        screenHost.removeAllViews();
        clearScreenReferences();
        View screen;
        if (tab == 1) {
            screen = createHistoryScreen();
        } else if (tab == 2) {
            screen = createRequestsScreen();
        } else {
            screen = createPlayerScreen();
        }
        screenHost.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (tab == 0) {
            updatePlayerUi();
            updatePlaybackButton();
        } else if (tab == 2 && !requestsLoaded) {
            loadRequestableSongs();
        }
    }

    private void clearScreenReferences() {
        liveBadge = null;
        listenersBadge = null;
        djLine = null;
        playingTitle = null;
        playingArtist = null;
        playingAlbum = null;
        playingArtwork = null;
        playButton = null;
        playSpinner = null;
        requestSearch = null;
        requestStatus = null;
        requestRows = null;
        requestScrollView = null;
        filteredRequestableSongs = Collections.emptyList();
        requestPagination.reset();
    }

    private View createPlayerScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(page);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        liveBadge = badge("●  ON AIR", COLOR_GREEN, 0x1E65D6A0);
        statusRow.addView(liveBadge);
        View flexible = new View(this);
        statusRow.addView(flexible, new LinearLayout.LayoutParams(0, 1, 1f));
        listenersBadge = text("0 LISTENING", 10, COLOR_MUTED, true, Gravity.CENTER_VERTICAL);
        listenersBadge.setLetterSpacing(0.06f);
        statusRow.addView(listenersBadge);
        page.addView(statusRow);
        addSpace(page, 20);

        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int artworkSize = dp(Math.max(180, Math.min(260, widthDp - 56)));
        LinearLayout artworkHolder = new LinearLayout(this);
        artworkHolder.setGravity(Gravity.CENTER);
        FrameLayout artworkFrame = new FrameLayout(this);
        artworkFrame.setBackground(rounded(COLOR_SURFACE_HIGH, 22));
        artworkFrame.setElevation(dp(10));
        artworkFrame.setOutlineProvider(roundOutline(22));
        artworkFrame.setClipToOutline(true);
        playingArtwork = new ImageView(this);
        playingArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        playingArtwork.setContentDescription("Current song artwork");
        artworkFrame.addView(playingArtwork, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        artworkHolder.addView(artworkFrame, new LinearLayout.LayoutParams(artworkSize, artworkSize));
        page.addView(artworkHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addSpace(page, 24);

        TextView nowPlayingLabel = text("NOW PLAYING", 10, COLOR_ACCENT, true, Gravity.CENTER);
        nowPlayingLabel.setLetterSpacing(0.18f);
        page.addView(nowPlayingLabel);
        addSpace(page, 8);

        playingTitle = text("Tune in to Animikii Club", 23, COLOR_TEXT, true, Gravity.CENTER);
        playingTitle.setMaxLines(2);
        playingTitle.setEllipsize(TextUtils.TruncateAt.END);
        page.addView(playingTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addSpace(page, 6);

        playingArtist = text(NowPlayingMetadata.DEFAULT_ARTIST, 15,
                COLOR_MUTED, false, Gravity.CENTER);
        playingArtist.setMaxLines(2);
        playingArtist.setEllipsize(TextUtils.TruncateAt.END);
        page.addView(playingArtist);
        addSpace(page, 4);

        playingAlbum = text(NowPlayingMetadata.DEFAULT_ALBUM, 12,
                COLOR_MUTED, false, Gravity.CENTER);
        playingAlbum.setMaxLines(1);
        playingAlbum.setEllipsize(TextUtils.TruncateAt.END);
        page.addView(playingAlbum);

        TextView streamQuality = badge("MP3 128k", COLOR_MUTED, COLOR_SURFACE_HIGH);
        LinearLayout.LayoutParams streamQualityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        streamQualityParams.gravity = Gravity.CENTER_HORIZONTAL;
        streamQualityParams.topMargin = dp(8);
        page.addView(streamQuality, streamQualityParams);

        djLine = text("", 10, COLOR_GREEN, true, Gravity.CENTER);
        djLine.setLetterSpacing(0.06f);
        djLine.setVisibility(View.GONE);
        LinearLayout.LayoutParams djParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        djParams.topMargin = dp(13);
        page.addView(djLine, djParams);
        addSpace(page, 8);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        ImageButton shareControl = iconButton(R.drawable.ic_share, "Share the station",
                52, COLOR_TEXT, COLOR_SURFACE);
        shareControl.setOnClickListener(view -> shareStation());
        controls.addView(shareControl, new LinearLayout.LayoutParams(dp(52), dp(52)));

        View spacerLeft = new View(this);
        controls.addView(spacerLeft, new LinearLayout.LayoutParams(dp(26), 1));

        FrameLayout playControl = new FrameLayout(this);
        playButton = new ImageButton(this);
        playButton.setImageResource(R.drawable.ic_play);
        playButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        playButton.setPadding(dp(22), dp(22), dp(22), dp(22));
        playButton.setBackground(rounded(COLOR_ACCENT, 40));
        playButton.setContentDescription("Play live radio");
        playButton.setOnClickListener(view -> togglePlayback());
        playControl.addView(playButton, new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER));
        playSpinner = new ProgressBar(this);
        playSpinner.setIndeterminate(true);
        playSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        playSpinner.setVisibility(View.GONE);
        playControl.addView(playSpinner, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        controls.addView(playControl, new LinearLayout.LayoutParams(dp(76), dp(76)));

        View spacerRight = new View(this);
        controls.addView(spacerRight, new LinearLayout.LayoutParams(dp(26), 1));

        ImageButton websiteControl = iconButton(R.drawable.ic_web, "Open the Animikii website",
                52, COLOR_TEXT, COLOR_SURFACE);
        websiteControl.setOnClickListener(view -> openPublicPlayer());
        controls.addView(websiteControl, new LinearLayout.LayoutParams(dp(52), dp(52)));
        page.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSpace(page, 24);

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView recentTitle = text("RECENTLY PLAYED", 11, COLOR_TEXT, true, Gravity.CENTER_VERTICAL);
        recentTitle.setLetterSpacing(0.09f);
        sectionHeader.addView(recentTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView seeAll = text("SEE ALL  ›", 10, COLOR_ACCENT, true, Gravity.CENTER_VERTICAL);
        seeAll.setOnClickListener(view -> showTab(1));
        seeAll.setContentDescription("View full song history");
        sectionHeader.addView(seeAll);
        page.addView(sectionHeader);
        addSpace(page, 10);

        LinearLayout recentRows = new LinearLayout(this);
        recentRows.setOrientation(LinearLayout.VERTICAL);
        page.addView(recentRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        populateHomeHistory(recentRows);

        return scroll;
    }

    private void populateHomeHistory(LinearLayout parent) {
        parent.removeAllViews();
        if (nowPlaying == null || nowPlaying.getHistory().isEmpty()) {
            TextView empty = text("Song history will appear here when the station data loads.",
                    13, COLOR_MUTED, false, Gravity.START);
            empty.setPadding(0, dp(8), 0, dp(8));
            parent.addView(empty);
            return;
        }
        int count = Math.min(3, nowPlaying.getHistory().size());
        for (int i = 0; i < count; i++) {
            addSongRow(parent, nowPlaying.getHistory().get(i), true);
        }
    }

    private View createHistoryScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = pageColumn();
        scroll.addView(page);

        addPageHeading(page, "Recently played", "Recent tracks from the station.");
        if (nowPlaying == null || nowPlaying.getHistory().isEmpty()) {
            TextView empty = text("Loading the latest song history…",
                    14, COLOR_MUTED, false, Gravity.START);
            empty.setPadding(dp(2), dp(12), dp(2), dp(12));
            page.addView(empty);
            return scroll;
        }

        TextView count = text(nowPlaying.getHistory().size() + " recent tracks",
                11, COLOR_ACCENT, true, Gravity.START);
        count.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.bottomMargin = dp(12);
        page.addView(count, countParams);
        for (AzuraCastParser.Song song : nowPlaying.getHistory()) {
            addSongRow(page, song, false);
        }
        return scroll;
    }

    private View createRequestsScreen() {
        ScrollView scroll = new ScrollView(this);
        requestScrollView = scroll;
        LinearLayout page = pageColumn();
        scroll.addView(page);
        addPageHeading(page, "Request a track", "Search the station's library and send a request.");

        if (nowPlaying != null && !nowPlaying.isRequestsEnabled()) {
            TextView unavailable = text("Song requests are turned off at the station right now.",
                    14, COLOR_MUTED, false, Gravity.START);
            unavailable.setPadding(dp(2), dp(8), dp(2), dp(8));
            page.addView(unavailable);
            return scroll;
        }

        requestSearch = new EditText(this);
        requestSearch.setSingleLine(true);
        requestSearch.setTextSize(14);
        requestSearch.setTextColor(COLOR_TEXT);
        requestSearch.setHintTextColor(COLOR_MUTED);
        requestSearch.setHint("Search title, artist, or album");
        requestSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        requestSearch.setPadding(dp(15), dp(13), dp(15), dp(13));
        requestSearch.setBackground(rounded(COLOR_SURFACE, 14));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.bottomMargin = dp(14);
        page.addView(requestSearch, searchParams);

        requestStatus = text("Loading requestable tracks…", 12, COLOR_MUTED,
                false, Gravity.START);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dp(10);
        page.addView(requestStatus, statusParams);

        requestRows = new LinearLayout(this);
        requestRows.setOrientation(LinearLayout.VERTICAL);
        page.addView(requestRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY == oldScrollY) {
                return;
            }
            View content = scroll.getChildAt(0);
            boolean nearEnd = content != null
                    && scrollY + scroll.getHeight()
                    >= content.getHeight() - dp(REQUEST_PREFETCH_DISTANCE_DP);
            if (requestPagination.onScrollNearEnd(nearEnd, filteredRequestableSongs.size())) {
                appendNextRequestPage();
            }
        });

        requestStatus.setOnClickListener(view -> {
            if (!requestsLoaded && !requestFetchInFlight) {
                loadRequestableSongs();
            }
        });
        requestSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateRequestRows();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        updateRequestRows();
        return scroll;
    }

    private LinearLayout pageColumn() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(28));
        return page;
    }

    private void addPageHeading(LinearLayout page, String title, String subtitle) {
        TextView heading = text(title, 25, COLOR_TEXT, true, Gravity.START);
        page.addView(heading);
        TextView subheading = text(subtitle, 13, COLOR_MUTED, false, Gravity.START);
        subheading.setMaxLines(2);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(7);
        subtitleParams.bottomMargin = dp(22);
        page.addView(subheading, subtitleParams);
    }

    private void addSongRow(LinearLayout parent, AzuraCastParser.Song song, boolean compact) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(11), dp(9));
        row.setBackground(rounded(COLOR_SURFACE, 14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        parent.addView(row, rowParams);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(rounded(COLOR_SURFACE_HIGH, 10));
        art.setOutlineProvider(roundOutline(10));
        art.setClipToOutline(true);
        art.setContentDescription("Album artwork");
        row.addView(art, new LinearLayout.LayoutParams(dp(compact ? 46 : 54), dp(compact ? 46 : 54)));
        imageLoader.load(song.getArtUrl(), art, R.drawable.station_icon);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailsParams.setMargins(dp(11), 0, dp(8), 0);
        row.addView(details, detailsParams);

        TextView title = text(emptyFallback(song.getTitle(), "Untitled track"),
                compact ? 13 : 14, COLOR_TEXT, true, Gravity.START);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(title);
        String detailLine = song.getArtist();
        if (!song.getAlbum().isEmpty() && !compact) {
            detailLine = detailLine.isEmpty() ? song.getAlbum() : detailLine + " · " + song.getAlbum();
        }
        TextView artist = text(emptyFallback(detailLine, "Animikii Club"),
                11, COLOR_MUTED, false, Gravity.START);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        artistParams.topMargin = dp(4);
        details.addView(artist, artistParams);

        if (!compact && song.getPlayedAt() > 0) {
            TextView playedTime = text(formatTime(song.getPlayedAt()), 10, COLOR_MUTED,
                    false, Gravity.CENTER_VERTICAL);
            row.addView(playedTime);
        }
    }

    private LinearLayout createRequestRow(AzuraCastParser.RequestableSong song) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(9), dp(9));
        row.setBackground(rounded(COLOR_SURFACE, 14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(rounded(COLOR_SURFACE_HIGH, 10));
        art.setOutlineProvider(roundOutline(10));
        art.setClipToOutline(true);
        art.setContentDescription("Album artwork for " + song.getTitle());
        row.addView(art, new LinearLayout.LayoutParams(dp(50), dp(50)));
        imageLoader.load(song.getArtUrl(), art, R.drawable.station_icon);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailParams.setMargins(dp(10), 0, dp(8), 0);
        row.addView(details, detailParams);
        TextView title = text(emptyFallback(song.getTitle(), "Untitled track"),
                13, COLOR_TEXT, true, Gravity.START);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(title);
        TextView artist = text(emptyFallback(song.getArtist(), song.getAlbum()),
                11, COLOR_MUTED, false, Gravity.START);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        artistParams.topMargin = dp(4);
        details.addView(artist, artistParams);

        TextView request = text("REQUEST", 9, Color.WHITE, true, Gravity.CENTER);
        request.setLetterSpacing(0.05f);
        request.setPadding(dp(10), dp(10), dp(10), dp(10));
        request.setBackground(rounded(COLOR_ACCENT, 11));
        request.setClickable(true);
        request.setFocusable(true);
        request.setContentDescription("Request " + song.getTitle() + " by " + song.getArtist());
        request.setOnClickListener(view -> confirmSongRequest(song));
        row.addView(request, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        return row;
    }

    private void updateRequestRows() {
        if (requestRows == null || requestStatus == null) {
            return;
        }
        requestRows.removeAllViews();
        requestPagination.reset();
        filteredRequestableSongs = Collections.emptyList();
        if (requestScrollView != null) {
            requestScrollView.scrollTo(0, 0);
        }
        if (!requestsLoaded) {
            requestStatus.setText(requestFetchInFlight
                    ? "Loading requestable tracks…"
                    : "Tap here to retry loading the station library.");
            return;
        }
        if (requestableSongs.isEmpty()) {
            requestStatus.setText("No tracks are currently available for requests.");
            return;
        }

        String query = requestSearch == null ? "" : requestSearch.getText().toString();
        filteredRequestableSongs = RequestSearch.filter(
                requestableSongs, query, requestableSongs.size());
        if (filteredRequestableSongs.isEmpty()) {
            requestStatus.setText("No tracks match that search.");
            return;
        }

        appendNextRequestPage();
    }

    private void appendNextRequestPage() {
        if (requestRows == null || requestStatus == null
                || filteredRequestableSongs.isEmpty()) {
            return;
        }
        int startIndex = requestPagination.appendNextPage(filteredRequestableSongs.size());
        if (startIndex < 0) {
            return;
        }
        int displayedRequestCount = requestPagination.getDisplayedCount();
        List<AzuraCastParser.RequestableSong> page = RequestSearch.page(
                filteredRequestableSongs, startIndex, displayedRequestCount - startIndex);
        for (AzuraCastParser.RequestableSong song : page) {
            requestRows.addView(createRequestRow(song), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        String query = requestSearch == null ? "" : requestSearch.getText().toString();
        String resultType = query.trim().isEmpty() ? "songs" : "matches";
        if (displayedRequestCount < filteredRequestableSongs.size()) {
            requestStatus.setText(String.format(Locale.getDefault(),
                    "Showing %d of %d %s · scroll for more", displayedRequestCount,
                    filteredRequestableSongs.size(), resultType));
        } else {
            requestStatus.setText(String.format(Locale.getDefault(),
                    "Showing all %d %s", filteredRequestableSongs.size(), resultType));
        }
    }

    private void loadRequestableSongs() {
        if (requestFetchInFlight || requestsLoaded || !activityStarted) {
            return;
        }
        requestFetchInFlight = true;
        if (requestStatus != null) {
            requestStatus.setText("Loading requestable tracks…");
            requestStatus.setTextColor(COLOR_MUTED);
        }
        networkExecutor.execute(() -> {
            List<AzuraCastParser.RequestableSong> loaded = null;
            Exception failure = null;
            try {
                loaded = new ArrayList<>(repository.fetchRequestableSongs());
                loaded.sort(Comparator
                        .comparing(AzuraCastParser.RequestableSong::getArtist,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AzuraCastParser.RequestableSong::getTitle,
                                String.CASE_INSENSITIVE_ORDER));
            } catch (Exception exception) {
                failure = exception;
            }
            final List<AzuraCastParser.RequestableSong> result = loaded;
            final Exception error = failure;
            mainHandler.post(() -> {
                requestFetchInFlight = false;
                if (error == null && result != null) {
                    requestableSongs = result;
                    requestsLoaded = true;
                }
                if (requestStatus != null) {
                    if (error != null) {
                        requestStatus.setText("Couldn’t load the station library. Tap to retry.");
                        requestStatus.setTextColor(COLOR_RED);
                    } else {
                        requestStatus.setTextColor(COLOR_MUTED);
                    }
                }
                updateRequestRows();
            });
        });
    }

    private void confirmSongRequest(AzuraCastParser.RequestableSong song) {
        String title = emptyFallback(song.getTitle(), "this track");
        String artist = song.getArtist().isEmpty() ? "" : " by " + song.getArtist();
        new AlertDialog.Builder(this)
                .setTitle("Request this track?")
                .setMessage(title + artist + " will be sent to Animikii Club’s request queue.")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Send request", (dialog, which) -> sendSongRequest(song))
                .show();
    }

    private void sendSongRequest(AzuraCastParser.RequestableSong song) {
        Toast.makeText(this, "Sending request…", Toast.LENGTH_SHORT).show();
        networkExecutor.execute(() -> {
            Exception failure = null;
            try {
                repository.submitSongRequest(song.getRequestId());
            } catch (Exception exception) {
                failure = exception;
            }
            final Exception error = failure;
            mainHandler.post(() -> {
                if (error == null) {
                    Toast.makeText(this, "Request sent to Animikii Club.", Toast.LENGTH_LONG).show();
                    requestableSongs = Collections.emptyList();
                    requestsLoaded = false;
                    if (requestStatus != null) {
                        requestStatus.setText("Refreshing the request list…");
                        requestStatus.setTextColor(COLOR_MUTED);
                    }
                    loadRequestableSongs();
                } else {
                    Toast.makeText(this, "Couldn’t send that request. Please try again.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void fetchNowPlaying() {
        if (nowPlayingFetchInFlight || !activityStarted) {
            return;
        }
        nowPlayingFetchInFlight = true;
        networkExecutor.execute(() -> {
            AzuraCastParser.NowPlaying loaded = null;
            Exception failure = null;
            try {
                loaded = repository.fetchNowPlaying();
            } catch (Exception exception) {
                failure = exception;
            }
            final AzuraCastParser.NowPlaying result = loaded;
            final Exception error = failure;
            mainHandler.post(() -> {
                nowPlayingFetchInFlight = false;
                if (error == null && result != null) {
                    nowPlaying = result;
                    updateMediaSessionMetadata();
                    if (selectedTab == 0) {
                        updatePlayerUi();
                        LinearLayout historyContainer = findHomeHistoryContainer();
                        if (historyContainer != null) {
                            populateHomeHistory(historyContainer);
                        }
                    } else if (selectedTab == 1) {
                        showTab(1);
                    } else if (selectedTab == 2 && !result.isRequestsEnabled()) {
                        showTab(2);
                    }
                } else if (selectedTab == 0) {
                    updatePlayerUi();
                }
            });
        });
    }

    private LinearLayout findHomeHistoryContainer() {
        if (screenHost == null || screenHost.getChildCount() == 0) {
            return null;
        }
        View root = screenHost.getChildAt(0);
        if (!(root instanceof ScrollView)) {
            return null;
        }
        View child = ((ScrollView) root).getChildAt(0);
        if (!(child instanceof LinearLayout)) {
            return null;
        }
        LinearLayout page = (LinearLayout) child;
        View last = page.getChildAt(page.getChildCount() - 1);
        return last instanceof LinearLayout ? (LinearLayout) last : null;
    }

    private void updatePlayerUi() {
        if (playingTitle == null) {
            return;
        }
        if (nowPlaying == null) {
            playingTitle.setText("Tune in to Animikii Club");
            playingArtist.setText(NowPlayingMetadata.DEFAULT_ARTIST);
            playingAlbum.setText(NowPlayingMetadata.DEFAULT_ALBUM);
            liveBadge.setText("●  CONNECTING");
            liveBadge.setTextColor(COLOR_MUTED);
            listenersBadge.setText("LIVE RADIO");
            imageLoader.load(null, playingArtwork, R.drawable.station_icon);
            return;
        }

        boolean online = nowPlaying.isOnline();
        NowPlayingMetadata metadata = NowPlayingMetadata.fromNowPlaying(nowPlaying);
        String title = metadata.getTitle();
        playingTitle.setText(title);
        playingArtist.setText(metadata.getArtist());
        playingAlbum.setText(metadata.getAlbum());
        liveBadge.setText(online ? "●  ON AIR" : "●  OFF AIR");
        liveBadge.setTextColor(online ? COLOR_GREEN : COLOR_RED);
        liveBadge.setBackground(rounded(online ? 0x1E65D6A0 : 0x24FF8585, 16));
        int listenerCount = Math.max(0, nowPlaying.getListenerCount());
        listenersBadge.setText(listenerCount + (listenerCount == 1 ? " LISTENER" : " LISTENERS"));
        if (nowPlaying.isLive()) {
            djLine.setText("LIVE DJ  ·  " + emptyFallback(nowPlaying.getStreamerName(), "ON AIR"));
            djLine.setTextColor(COLOR_GREEN);
            djLine.setVisibility(View.VISIBLE);
        } else {
            djLine.setVisibility(View.GONE);
        }
        playingArtwork.setContentDescription("Artwork for " + title);
        imageLoader.load(metadata.getArtworkUrl(), playingArtwork, R.drawable.station_icon);
    }

    private void connectMediaController() {
        if (controllerFuture != null) {
            return;
        }
        SessionToken token = new SessionToken(this,
                new ComponentName(this, RadioPlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                MediaController connected = controllerFuture.get();
                if (isFinishing()) {
                    connected.release();
                    return;
                }
                mediaController = connected;
                mediaController.addListener(playerListener);
                updateMediaSessionMetadata();
                updatePlaybackButton();
            } catch (Exception ignored) {
                Toast.makeText(this, "Radio controls are still connecting.", Toast.LENGTH_SHORT).show();
            }
        }, this::runOnUiThread);
    }

    private void togglePlayback() {
        if (mediaController == null) {
            Toast.makeText(this, "Connecting to the player…", Toast.LENGTH_SHORT).show();
            connectMediaController();
            return;
        }
        if (mediaController.isPlaying()) {
            mediaController.pause();
            return;
        }
        if (mediaController.getMediaItemCount() == 0) {
            mediaController.setMediaItem(StationMediaItemFactory.create(
                    NowPlayingMetadata.fromNowPlaying(nowPlaying)));
            mediaController.prepare();
        }
        mediaController.play();
    }

    private void updateMediaSessionMetadata() {
        if (mediaController == null || mediaController.getMediaItemCount() == 0) {
            return;
        }
        int index = mediaController.getCurrentMediaItemIndex();
        MediaItem currentItem = mediaController.getCurrentMediaItem();
        NowPlayingMetadata metadata = NowPlayingMetadata.fromNowPlaying(nowPlaying);
        if (index >= 0 && !StationMediaItemFactory.matches(currentItem, metadata)) {
            mediaController.replaceMediaItem(index, StationMediaItemFactory.create(metadata));
        }
    }

    private void updatePlaybackButton() {
        if (playButton == null) {
            return;
        }
        int playbackState = mediaController == null
                ? Player.STATE_IDLE : mediaController.getPlaybackState();
        PlaybackButtonState buttonState = PlaybackButtonState.from(
                mediaController != null && mediaController.isPlaying(),
                mediaController != null && mediaController.getPlayWhenReady(),
                mediaController != null && mediaController.getMediaItemCount() > 0,
                playbackState);
        boolean reconnecting = buttonState.shouldShowReconnectSpinner();
        playSpinner.setVisibility(reconnecting ? View.VISIBLE : View.GONE);
        playSpinner.setContentDescription("Reconnecting to live radio");
        playButton.setVisibility(reconnecting ? View.INVISIBLE : View.VISIBLE);
        playButton.setImageResource(buttonState.shouldShowPauseIcon()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        playButton.setContentDescription(buttonState.getContentDescription());
    }

    private void shareStation() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Animikii Club — Eclectic001");
        send.putExtra(Intent.EXTRA_TEXT,
                "Listen to Animikii Club — Eclectic001 Turtle Island Ojibwe Edition: "
                        + StationRoutes.PUBLIC_PLAYER_URL);
        startActivity(Intent.createChooser(send, "Share Animikii Radio"));
    }

    private void openPublicPlayer() {
        String url = nowPlaying != null && !nowPlaying.getPublicPlayerUrl().isEmpty()
                ? nowPlaying.getPublicPlayerUrl() : StationRoutes.PUBLIC_PLAYER_URL;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            Toast.makeText(this, "No browser is available to open the station page.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTime(long epochSeconds) {
        return DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(epochSeconds * 1000L));
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private TextView badge(String label, int textColor, int backgroundColor) {
        TextView view = text(label, 10, textColor, true, Gravity.CENTER);
        view.setLetterSpacing(0.04f);
        view.setPadding(dp(11), dp(8), dp(11), dp(8));
        view.setBackground(rounded(backgroundColor, 16));
        return view;
    }

    private ImageButton iconButton(int resource, String description, int size,
                                   int tint, int backgroundColor) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(resource);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(size / 3), dp(size / 3), dp(size / 3), dp(size / 3));
        button.setBackground(rounded(backgroundColor, size));
        button.setColorFilter(tint);
        button.setContentDescription(description);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold, int gravity) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(gravity);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private ViewOutlineProvider roundOutline(int radiusDp) {
        final float radius = dp(radiusDp);
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        };
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
