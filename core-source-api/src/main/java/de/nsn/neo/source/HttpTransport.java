package de.nsn.neo.source;

import de.nsn.neo.session.SourceSession;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.InetAddress;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small source-scoped transport. Cookies can never leak into another provider. */
public final class HttpTransport {
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private final SourceSession session;

    public HttpTransport(SourceSession session) { this.session = session; }

    public String get(String address) throws Exception {
        return requestWithDnsFallback(address, null);
    }

    public String postForm(String address, Map<String,String> fields) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String,String> field : fields.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(field.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(field.getValue(), "UTF-8"));
        }
        return requestWithDnsFallback(address, body.toString());
    }

    /** Retries exactly once through Cloudflare DoH, only for DNS resolution failures. */
    private String requestWithDnsFallback(String address, String formBody) throws Exception {
        try { return request(address, formBody); }
        catch (Exception error) {
            if (!hasUnknownHost(error)) throw error;
            return requestViaDoh(address, formBody);
        }
    }

    private static boolean hasUnknownHost(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause())
            if (current instanceof UnknownHostException) return true;
        return false;
    }

    private String requestViaDoh(String address, String formBody) throws Exception {
        URI target = URI.create(address);
        final Map<String, java.util.List<InetAddress>> resolved = new java.util.HashMap<>();
        Dns dohDns = hostname -> {
            if ("cloudflare-dns.com".equalsIgnoreCase(hostname)) return Dns.SYSTEM.lookup(hostname);
            java.util.List<InetAddress> cached = resolved.get(hostname);
            if (cached != null) return cached;
            Request dnsRequest = new Request.Builder()
                    .url("https://cloudflare-dns.com/dns-query?name=" + URLEncoder.encode(hostname, StandardCharsets.UTF_8) + "&type=A")
                    .header("Accept", "application/dns-json").build();
            try (Response response = new OkHttpClient.Builder().build().newCall(dnsRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new UnknownHostException(hostname);
                String body = response.body().string();
                java.util.ArrayList<InetAddress> result = new java.util.ArrayList<>();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"data\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(body);
                while (matcher.find()) { String ip = matcher.group(1); if (ip.matches("[0-9.]+")) result.add(InetAddress.getByName(ip)); }
                if (result.isEmpty()) throw new UnknownHostException(hostname);
                resolved.put(hostname, result); return result;
            } catch (Exception failure) {
                UnknownHostException dnsFailure = new UnknownHostException(hostname);
                dnsFailure.initCause(failure);
                throw dnsFailure;
            }
        };
        OkHttpClient client = new OkHttpClient.Builder().dns(dohDns).followRedirects(formBody == null).build();
        Request.Builder builder = new Request.Builder().url(address)
                .header("User-Agent", USER_AGENT).header("Accept", "text/html,application/xhtml+xml");
        Map<String, List<String>> cookies = session.cookies().get(target, Map.of());
        for (Map.Entry<String, List<String>> entry : cookies.entrySet()) builder.header(entry.getKey(), String.join("; ", entry.getValue()));
        if (formBody != null) builder.post(okhttp3.RequestBody.create(formBody, okhttp3.MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8")));
        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.body() == null) throw new IllegalStateException("Leere DoH-Antwort");
            if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code() + " für " + address);
            return response.body().string();
        }
    }

    private String request(String address, String formBody) throws Exception {
        URL url = new URL(address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(18_000);
        connection.setInstanceFollowRedirects(formBody == null);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        URI uri = url.toURI();
        Map<String, List<String>> cookieHeaders = session.cookies().get(uri, Map.of());
        for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
            connection.setRequestProperty(entry.getKey(), String.join("; ", entry.getValue()));
        }
        if (formBody != null) {
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        }
        int status = connection.getResponseCode();
        storeCookies(uri, connection.getHeaderFields());
        if (status >= 300 && status < 400) {
            String location = connection.getHeaderField("Location"); connection.disconnect();
            if (location != null) return get(uri.resolve(location).toString());
        }
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) throw new IllegalStateException("HTTP " + status + " ohne Antwort");
        StringBuilder html = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) html.append(line).append('\n');
        } finally {
            connection.disconnect();
        }
        if (status >= 400) throw new IllegalStateException("HTTP " + status + " für " + address);
        return html.toString();
    }

    private void storeCookies(URI uri, Map<String,List<String>> headers) throws Exception {
        try { session.cookies().put(uri, headers); }
        catch (IllegalArgumentException error) {
            Map<String,List<String>> sanitized = new LinkedHashMap<>(headers);
            for (String key : List.of("Set-Cookie","set-cookie")) if (sanitized.containsKey(key)) {
                List<String> values = new java.util.ArrayList<>();
                for (String value : sanitized.get(key)) values.add(value.replaceAll("(?i);\\s*domain=[^;]+", ""));
                sanitized.put(key, values);
            }
            session.cookies().put(uri, sanitized);
        }
    }
}
