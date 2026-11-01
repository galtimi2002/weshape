package com.weshape.backend.wind;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

/** Range reads from a private Cloudflare R2 bucket (S3 API, signed requests). */
public class R2RangeSource implements RangeSource {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final String host;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;

    public R2RangeSource(String accountId, String bucket, String accessKey, String secretKey) {
        this.host = accountId + ".r2.cloudflarestorage.com";
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public byte[] read(String key, long offset, int length) throws IOException {
        String range = "bytes=" + offset + "-" + (offset + length - 1);
        byte[] body = get(key, Map.of("range", range));
        if (body.length > length) {
            // A 200 instead of 206: the store ignored the range; keep the part asked for.
            int from = (int) Math.min(offset, body.length);
            return java.util.Arrays.copyOfRange(body, from, Math.min(body.length, from + length));
        }
        return body;
    }

    @Override
    public byte[] readAll(String key) throws IOException {
        return get(key, Map.of());
    }

    private byte[] get(String key, Map<String, String> extra) throws IOException {
        String path = "/" + bucket + "/" + key;
        Map<String, String> headers = AwsV4Signer.signGet(host, path, extra,
                ZonedDateTime.now(ZoneOffset.UTC), "auto", accessKey, secretKey);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://" + host + AwsV4Signer.encodePath(path)))
                .timeout(Duration.ofSeconds(30))
                .GET();
        headers.forEach(request::header);
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 206 || status == 200) {
                    return response.body();
                }
                if (status == 404) {
                    throw new FileNotFoundException(key);
                }
                if (status == 416) {
                    return new byte[0];
                }
                if (status < 500) {
                    // Wrong key or bucket settings: trying again will not help.
                    throw new R2Exception("R2 answered " + status + " for " + key);
                }
                last = new IOException("R2 answered " + status + " for " + key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } catch (FileNotFoundException | R2Exception e) {
                throw e;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** A refusal from the store (bad key, no access), not worth retrying. */
    static class R2Exception extends IOException {
        R2Exception(String message) {
            super(message);
        }
    }

    @Override
    public String describe() {
        return "R2 bucket " + bucket;
    }
}
