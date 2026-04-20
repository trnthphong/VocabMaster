package com.example.vocabmaster.data.remote;

import android.util.Log;

import com.example.vocabmaster.BuildConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnsplashHelper {
    private static final String TAG = "UnsplashHelper";
    private static final String BASE_URL = "https://api.unsplash.com/search/photos";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ImageCallback {
        void onSuccess(String imageUrl);
        void onError();
    }

    /** Tìm ảnh theo từ khóa, trả về URL dạng images.unsplash.com */
    public void searchImage(String keyword, ImageCallback callback) {
        executor.execute(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
                String urlStr = BASE_URL + "?query=" + encoded
                        + "&per_page=1&orientation=landscape"
                        + "&client_id=" + BuildConfig.UNSPLASH_ACCESS_KEY;

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("Accept-Version", "v1");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject photo = json.getJSONArray("results").optJSONObject(0);
                if (photo != null) {
                    String rawUrl = photo.getJSONObject("urls").getString("raw");
                    String finalUrl = rawUrl + "&w=400&h=300&fit=crop&auto=format";
                    callback.onSuccess(finalUrl);
                } else {
                    callback.onError();
                }
            } catch (Exception e) {
                Log.e(TAG, "searchImage failed: " + e.getMessage());
                callback.onError();
            }
        });
    }
}
