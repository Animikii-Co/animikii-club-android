package club.animikii.radio.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads HTTPS album art, caches it in memory, and ignores results for recycled views. */
public final class ImageLoader {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> cache;

    public ImageLoader() {
        int cacheKilobytes = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(Math.max(cacheKilobytes, 1024)) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
    }

    public void load(String imageUrl, ImageView imageView, int placeholderResource) {
        if (imageView == null) {
            return;
        }
        if (imageUrl == null || !imageUrl.startsWith("https://")) {
            imageView.setTag(null);
            imageView.setImageResource(placeholderResource);
            return;
        }
        if (imageUrl.equals(imageView.getTag())) {
            return;
        }

        imageView.setTag(imageUrl);
        imageView.setImageResource(placeholderResource);
        Bitmap cached = cache.get(imageUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(12_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "AnimikiiRadio-Android/1.0");
                if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                    try (InputStream input = connection.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(input);
                    }
                }
            } catch (Exception ignored) {
                // Keep the station artwork placeholder when cover art is unavailable.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            final Bitmap result = bitmap;
            if (result != null) {
                cache.put(imageUrl, result);
            }
            mainHandler.post(() -> {
                if (imageUrl.equals(imageView.getTag())) {
                    if (result != null) {
                        imageView.setImageBitmap(result);
                    } else {
                        imageView.setTag(null);
                    }
                }
            });
        });
    }

    public void close() {
        executor.shutdownNow();
        cache.evictAll();
    }
}
