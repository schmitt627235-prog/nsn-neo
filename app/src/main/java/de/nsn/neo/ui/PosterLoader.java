package de.nsn.neo.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PosterLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(40, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) { return size() > 40; }
    };
    private PosterLoader() {}
    static void load(ImageView target, String url) {
        if (url == null || url.isBlank()) return;
        target.setTag(url);
        synchronized (CACHE) { if (CACHE.containsKey(url)) { target.setImageBitmap(CACHE.get(url)); return; } }
        POOL.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL imageUrl=new URL(url);connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setConnectTimeout(10_000); connection.setReadTimeout(15_000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 Chrome/120 Safari/537.36");
                connection.setRequestProperty("Referer",imageUrl.getProtocol()+"://"+imageUrl.getAuthority()+"/");
                connection.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
                Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                if (bitmap == null) return;
                synchronized (CACHE) { CACHE.put(url, bitmap); }
                MAIN.post(() -> { if (url.equals(target.getTag())) target.setImageBitmap(bitmap); });
            } catch (Exception ignored) { } finally { if (connection != null) connection.disconnect(); }
        });
    }
}
