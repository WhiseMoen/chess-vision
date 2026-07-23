package ru.chessvision.network;

import android.os.Handler;
import android.os.Looper;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import ru.chessvision.core.*;

public final class OpeningExplorerClient {
    public record MoveStat(String san, long white, long draws, long black) {
        public long total() { return white + draws + black; }
    }
    public record Stats(long white, long draws, long black, String opening, List<MoveStat> moves) {
        public long total() { return white + draws + black; }
        public int whitePercent() { return percent(white, total()); }
        public int drawPercent() { return percent(draws, total()); }
        public int blackPercent() { return percent(black, total()); }
        private static int percent(long value, long total) {
            return total == 0 ? 0 : (int) Math.round(value * 100.0 / total);
        }
    }
    public interface Callback {
        void onResult(Stats stats);
        void onUnavailable(String reason);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ConcurrentMap<String, Stats> CACHE = new ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String bearerToken = "";

    public void setBearerToken(String token) {
        bearerToken = token == null ? "" : token.trim();
        CACHE.clear();
    }

    public void load(Board board, Callback callback) {
        String fen = Fen.write(board);
        Stats cached = CACHE.get(fen);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                String encoded = URLEncoder.encode(fen, StandardCharsets.UTF_8.name());
                JSONObject json = get("https://explorer.lichess.ovh/masters?moves=8&topGames=0&recentGames=0&fen=" + encoded);
                List<MoveStat> moves = new ArrayList<>();
                JSONArray array = json.optJSONArray("moves");
                if (array != null) for (int i = 0; i < array.length(); i++) {
                    JSONObject move = array.getJSONObject(i);
                    moves.add(new MoveStat(move.optString("san", move.optString("uci")),
                            move.optLong("white"), move.optLong("draws"), move.optLong("black")));
                }
                JSONObject opening = json.optJSONObject("opening");
                String name = opening == null ? "" : opening.optString("name", "");
                Stats stats = new Stats(json.optLong("white"), json.optLong("draws"),
                        json.optLong("black"), name, List.copyOf(moves));
                CACHE.put(fen, stats);
                main.post(() -> callback.onResult(stats));
            } catch (AuthorizationException auth) {
                main.post(() -> callback.onUnavailable("нужна авторизация Lichess"));
            } catch (Exception error) {
                main.post(() -> callback.onUnavailable("нет сети или сервис недоступен"));
            }
        });
    }

    private JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(4500);
        connection.setReadTimeout(5500);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ChessVision/0.4 (github.com/WhiseMoen/chess-vision)");
        if (!bearerToken.isBlank()) connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        int code = connection.getResponseCode();
        if (code == 401) throw new AuthorizationException();
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

    private static final class AuthorizationException extends IOException {}
}
