package ru.chessvision.network;

import android.os.Handler;
import android.os.Looper;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import ru.chessvision.core.*;

public final class StockfishCloudClient {
    public record Result(double pawns, int depth, String line, boolean mate) {}
    public interface Callback {
        void onResult(Result result);
        void onUnavailable(String reason);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ConcurrentMap<String, Result> CACHE = new ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void evaluate(Board board, Callback callback) {
        String fen = Fen.write(board);
        Result cached = CACHE.get(fen);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                String encoded = URLEncoder.encode(fen, StandardCharsets.UTF_8.name());
                JSONObject json = get("https://lichess.org/api/cloud-eval?multiPv=1&variant=standard&fen=" + encoded);
                int depth = json.optInt("depth", 0);
                JSONArray pvs = json.getJSONArray("pvs");
                JSONObject pv = pvs.getJSONObject(0);
                boolean mate = pv.has("mate");
                double score = mate ? Math.copySign(100.0, pv.getInt("mate"))
                        : pv.optInt("cp", 0) / 100.0;
                Result result = new Result(score, depth, pv.optString("moves", ""), mate);
                CACHE.put(fen, result);
                main.post(() -> callback.onResult(result));
            } catch (FileNotFoundException notCached) {
                main.post(() -> callback.onUnavailable("позиции пока нет в облачном кэше"));
            } catch (Exception error) {
                main.post(() -> callback.onUnavailable("сеть недоступна или исчерпан лимит"));
            }
        });
    }

    private JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(4500);
        connection.setReadTimeout(5500);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ChessVision/0.4 (github.com/WhiseMoen/chess-vision)");
        int code = connection.getResponseCode();
        if (code == 404) throw new FileNotFoundException("not cached");
        if (code != 200) throw new IOException("HTTP " + code);
        try (InputStream input = connection.getInputStream()) {
            return new JSONObject(readUtf8(input));
        } finally {
            connection.disconnect();
        }
    }

    private String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
