import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * JVM downloader (works where Windows Schannel-based curl/.NET fail).
 *
 * usage: java Download.java <url> <outPath> [proxy] [resume] [sha256=<hex>]
 *   proxy  -> route through 127.0.0.1:7897
 *   resume -> continue a partial file (HTTP Range)
 *   sha256 -> verify digest after download, delete on mismatch
 */
public class Download {

    static final String PROXY_HOST = "127.0.0.1";
    static final int PROXY_PORT = 7897;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: Download <url> <outPath> [proxy] [resume] [sha256=<hex>]");
            System.exit(2);
        }
        String url = args[0];
        Path out = Paths.get(args[1]);
        boolean useProxy = false, resume = false;
        String expectSha = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "proxy": useProxy = true; break;
                case "resume": resume = true; break;
                default:
                    if (args[i].startsWith("sha256=")) expectSha = args[i].substring(7).toLowerCase();
                    else { System.out.println("unknown arg: " + args[i]); System.exit(2); }
            }
        }

        long existing = (resume && Files.exists(out)) ? Files.size(out) : 0L;
        if (!resume && Files.exists(out)) Files.delete(out);
        if (out.getParent() != null) Files.createDirectories(out.getParent());

        HttpClient.Builder builder = HttpClient.newBuilder()
                .sslContext(trustAllContext())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30));
        if (useProxy) builder.proxy(ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
        HttpClient client = builder.build();

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofHours(3))
                .header("User-Agent", "echo-build/1.0");
        if (existing > 0) rb.header("Range", "bytes=" + existing + "-");

        long t0 = System.currentTimeMillis();
        HttpResponse<InputStream> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        if (code != 200 && code != 206) {
            System.out.println("HTTP " + code + " -> FAILED " + url);
            System.exit(1);
        }
        long len = resp.headers().firstValueAsLong("content-length").orElse(-1L);
        long total = (code == 206 && len > 0) ? len + existing : len;
        System.out.printf("HTTP %d  existing=%d  total=%.1f MB  %s%n",
                code, existing, total / 1048576.0, code == 206 ? "(resuming)" : "");

        long done = existing, lastPrint = 0;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        if (existing > 0 && expectSha != null) {
            // digest of already-written prefix, so a resumed file still verifies
            try (InputStream pre = Files.newInputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = pre.read(buf)) > 0) md.update(buf, 0, n);
            }
        }
        try (InputStream in = resp.body();
             OutputStream os = new FileOutputStream(out.toFile(), existing > 0)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                if (expectSha != null || existing == 0) md.update(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - lastPrint >= 1000) {
                    lastPrint = now;
                    double secs = Math.max(0.001, (now - t0) / 1000.0);
                    double pct = total > 0 ? done * 100.0 / total : -1;
                    System.out.printf("\r  %6.2f%%  %7.1f / %7.1f MB  %6.1f MB/s   ",
                            pct, done / 1048576.0, total / 1048576.0, (done - existing) / 1048576.0 / secs);
                    System.out.flush();
                }
            }
        }
        System.out.println();
        long size = Files.size(out);
        System.out.printf("SAVED %s (%.1f MB, %d bytes) in %.1fs%n",
                out, size / 1048576.0, size, (System.currentTimeMillis() - t0) / 1000.0);

        if (expectSha != null) {
            String actual = hex(md.digest());
            if (actual.equalsIgnoreCase(expectSha)) {
                System.out.println("SHA256 OK " + actual);
            } else {
                System.out.println("SHA256 MISMATCH expected=" + expectSha + " actual=" + actual);
                Files.deleteIfExists(out);
                System.exit(3);
            }
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static SSLContext trustAllContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new SecureRandom());
        return ctx;
    }
}
