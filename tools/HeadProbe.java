import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * URL probe: HEAD-ish check (Range GET) for reachability, size and redirect chain.
 * Works from the JVM where Windows Schannel-based tools fail.
 *
 * usage: java HeadProbe.java [--proxy] <url> [url...]
 *   with no urls, probes the built-in candidate list (model mirrors / LLM endpoints).
 */
public class HeadProbe {

    static final String[] DEFAULT_URLS = {
        // SenseVoice int8 model — HuggingFace (single files, no archive needed)
        "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx",
        "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt",
        // ... ModelScope mirror (国内直连更快)
        "https://modelscope.cn/models/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/master/model.int8.onnx",
        // silero-vad (小模型，APK 内置)
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        // GitHub release tarball (备用镜像)
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2",
        // LLM providers (只探测连通性)
        "https://api.deepseek.com/v1/models",
        "https://open.bigmodel.cn/api/paas/v4/models",
        "https://api.moonshot.cn/v1/models",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
        "https://ark.cn-beijing.volces.com/api/v3/models",
        "https://api.hunyuan.cloud.tencent.com/v1/models",
        "https://api.minimaxi.com/v1/models",
        "https://api.siliconflow.cn/v1/models",
    };

    public static void main(String[] args) throws Exception {
        boolean useProxy = false;
        java.util.List<String> urls = new java.util.ArrayList<>();
        for (String a : args) {
            if (a.equals("--proxy")) useProxy = true;
            else urls.add(a);
        }
        if (urls.isEmpty()) urls = java.util.Arrays.asList(DEFAULT_URLS);

        HttpClient.Builder b = HttpClient.newBuilder()
                .sslContext(trustAll())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20));
        if (useProxy) b.proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 7897)));
        HttpClient client = b.build();

        System.out.println("mode=" + (useProxy ? "PROXY" : "DIRECT") + "  urls=" + urls.size());
        for (String u : urls) {
            long t0 = System.currentTimeMillis();
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(u))
                        .header("Range", "bytes=0-0")
                        .header("User-Agent", "echo-probe/1.0")
                        .timeout(Duration.ofSeconds(25))
                        .GET()
                        .build();
                HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
                String len = r.headers().firstValue("content-range").orElse(
                        r.headers().firstValue("content-length").orElse("-"));
                String ctype = r.headers().firstValue("content-type").orElse("-");
                String body = r.body() == null ? "" : r.body().replaceAll("\\s+", " ").trim();
                if (body.length() > 90) body = body.substring(0, 90) + "...";
                System.out.printf("%-3d %-7s %-32s %-28s %s%n",
                        r.statusCode(), (System.currentTimeMillis() - t0) + "ms", shorten(len), shorten(ctype), shorten(u));
                if (r.statusCode() >= 400 && !body.isEmpty()) System.out.println("      body: " + body);
            } catch (Exception e) {
                System.out.printf("ERR      %-32s %s%n", (System.currentTimeMillis() - t0) + "ms",
                        shorten(u) + "  -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    static String shorten(String s) {
        if (s == null) return "-";
        return s.length() <= 34 ? s : s.substring(0, 31) + "...";
    }

    static SSLContext trustAll() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new SecureRandom());
        return ctx;
    }
}
