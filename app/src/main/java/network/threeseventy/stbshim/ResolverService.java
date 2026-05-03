package network.threeseventy.stbshim;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

public class ResolverService extends Service {

    private static final String CHANNEL_ID = "resolver_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceMode();
        startHTTPServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Important: tells Android to restart if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }

    private void startForegroundServiceMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Resolver Service",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Resolver running")
                .setContentText("HTTP server active")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        startForeground(1, notification);
    }

    private void startHTTPServer() {
        new Thread(() -> {
            try {
                java.net.ServerSocket server = new java.net.ServerSocket(8080);

                while (true) {
                    java.net.Socket client = server.accept();
                    handleClient(client);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClient(Socket client) {
        try {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(client.getInputStream()));

            String requestLine = reader.readLine();
            if (requestLine == null) {
                client.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                client.close();
                return;
            }

            String path = parts[1];
            Uri uri = Uri.parse(path);

            String route = uri.getPath();

            Map<String, String> headers = new HashMap<>();

            String line;
            while (!(line = reader.readLine()).isEmpty()) {

                int idx = line.indexOf(":");
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();

                    headers.put(key, value);
                }
            }

            if ("/decipher".equals(route)) {
                String url = uri.getQueryParameter("url");
                String result = resolveUrl(url, headers);
                sendResponse(client, result);
            } else {
                sendResponse(client, "unknown");
            }

            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String resolveUrl(String url, Map<String, String> headers) throws Exception {

        HttpURLConnection conn =
                (HttpURLConnection) new URL(url).openConnection();

        conn.setRequestMethod("GET");

        for (Map.Entry<String, String> entry : headers.entrySet()) {

            String key = entry.getKey();

            if (key.equalsIgnoreCase("Host")) continue;
            if (key.equalsIgnoreCase("Connection")) continue;
            if (key.equalsIgnoreCase("Content-Length")) continue;
            if (key.equalsIgnoreCase("Accept-Encoding")) continue;
            if (key.equalsIgnoreCase("Authorization")) continue;

            System.out.println("Setting additional key: " + key + "\t\t" + entry.getValue());
            conn.setRequestProperty(key, entry.getValue());
        }

        TokenGenerator auth = new TokenGenerator("hablasdl", "askdjkjksad");
        auth.login();
        String token = auth.getAccessToken();
        System.out.println("TOKEN: " + auth.getAccessToken());
        conn.setRequestProperty("Authorization", "Bearer " + auth.getAccessToken());

        InputStream stream;

        int code = conn.getResponseCode();

        if (code >= 200 && code < 300) {
            stream = conn.getInputStream();
        } else {
            stream = conn.getErrorStream();
        }

        System.out.println("HTTP CODE: " + conn.getResponseCode());
        System.out.println("URL: " + url);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(stream)
        );

        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            sb.append(line);
        }

        in.close();

        System.out.println("JSON out: " + sb.toString());

        JSONObject obj = new JSONObject(sb.toString());

        return obj.getString("url");
    }

    private void sendResponse(Socket client, String body) {
        try {
            OutputStream out = client.getOutputStream();

            String response =
                    "HTTP/1.1 303 See Other\r\n" +
                            "Location: " + body + "\r\n" +
                            "Content-Length: " + body.length() + "\r\n" +
                            "\r\n";

            out.write(response.getBytes());
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}