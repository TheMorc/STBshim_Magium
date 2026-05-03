package network.threeseventy.stbshim;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class TokenGenerator {

    private String userName;
    private String password;

    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public TokenGenerator(String user, String pass) {
        this.userName = user;
        this.password = pass;
    }

    public void login() throws Exception {

        System.out.println("Main login!");

        if (userName == null || password == null) {
            throw new RuntimeException("User not defined");
        }

        // If no token > full login
        if (accessToken == null) {
            System.out.println("No token, full login!");
            initAndLogin();
            return;
        }

        // If expired > refresh
        if (refreshToken != null && expiresIn < System.currentTimeMillis()) {
            System.out.println("Expired token, refresh login!");
            try {
                refreshTokens();
            } catch (Exception e) {
                // force full re-login
                accessToken = null;
                refreshToken = null;
                initAndLogin();
            }
        }
    }

    private void resetMagion() throws Exception {
        URL url = new URL("https://skgo.magio.tv/home/listDevices");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        System.out.println("Devices list result code: " + code);

        InputStream stream =
                (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();

        String result = response.toString();
        System.out.println("Devices list: " + result);

        JSONObject obj = new JSONObject(result);
        JSONArray items = obj.getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject device = items.getJSONObject(i);

            String name = device.optString("name"); // or "name" depending on API
            String id = device.optString("id");

            System.out.println("Found device: " + name + " with ID: " + id);

            if (name != null && name.equals("Magium")) {
                String urlString = "https://skgo.magio.tv/home/deleteDevice?id="
                                + URLEncoder.encode(id, "UTF-8");

                conn = (HttpURLConnection) new URL(urlString).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);

                code = conn.getResponseCode();
                System.out.println("Deleting device response code: " + code);

                stream = (code >= 200 && code < 300)
                                ? conn.getInputStream()
                                : conn.getErrorStream();

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuilder sb = new StringBuilder();
                line = "";

                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                reader.close();

                result = sb.toString();
                System.out.println("DELETE RESPONSE: " + result);
            }
        }
    }
    private void initAndLogin() throws Exception {

        System.out.println("Init and login!");

        String url = "https://skgo.magio.tv/v2/auth/init"
                        + "?dsid=Netscape." + (System.currentTimeMillis() / 1000) + "." + Math.random()
                        + "&deviceName=Magium"
                        + "&deviceType=OTT_STB"
                        + "&osVersion=0.0.0"
                        + "&appVersion=0.0.0"
                        + "&language=SK";

        String initResult = post(
                url,
                null,
                buildInitHeaders(),
                null
        );

        JSONObject loginBody = new JSONObject();
        loginBody.put("loginOrNickname", userName);
        loginBody.put("password", password);
        System.out.println("Login:" + userName + " Password: " + password);

        String response = post(
                "https://skgo.magio.tv/v2/auth/login",
                null,
                buildAuthHeaders(),
                loginBody.toString()
        );


        resetMagion();
    }

    private void refreshTokens() throws Exception {

        System.out.println("Refresh tokens!");

        JSONObject body = new JSONObject();
        body.put("refreshToken", refreshToken);

        String response = post(
                "https://skgo.magio.tv/v2/auth/tokens",
                null,
                buildAuthHeaders(),
                body.toString()
        );

        parseSession(response);
    }

    private String post(String urlStr,
                        Map<String, String> params,
                        Map<String, String> headers,
                        String body) throws Exception {

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
        }

        if (body != null) {
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes());
            os.flush();
            os.close();
        }

        BufferedReader reader;

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }

        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        reader.close();

        parseSession(sb.toString());

        return sb.toString();
    }

    private void parseSession(String response) throws Exception {

        System.out.println("Parse output: " + response);

        JSONObject obj = new JSONObject(response);

        if (obj.has("token")) {
            JSONObject token = obj.getJSONObject("token");

            if (token.has("accessToken")) {
                accessToken = token.getString("accessToken");
                System.out.println("Found accessToken: " + accessToken);
            }

            if (token.has("refreshToken")) {
                refreshToken = token.getString("refreshToken");
                System.out.println("Found refreshToken: " + refreshToken);
            }

            if (token.has("expiresIn")) {
                expiresIn = token.getLong("expiresIn");
                System.out.println("Found expiresIn: " + expiresIn);
            }
        }
    }

    private Map<String, String> buildInitHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("X-ClientId", "-1");
        h.put("Origin", "https://www.magiogo.sk");
        h.put("Referer", "https://www.magiogo.sk/");
        h.put("Pragma", "no-cache");
        h.put("Sec-Fetch-Mode", "cors");
        h.put("Sec-Fetch-Site", "cross-site");
        h.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:137.0) Gecko/20100101 Firefox/137.0");
        return h;
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> h = buildInitHeaders();
        if (accessToken != null) {
            h.put("Authorization", "Bearer " + accessToken);
        }
        return h;
    }

    public String getAccessToken() {
        return accessToken;
    }
}