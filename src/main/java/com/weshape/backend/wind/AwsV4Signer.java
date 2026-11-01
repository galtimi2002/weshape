package com.weshape.backend.wind;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4 for plain S3 GET requests without a body. Cloudflare R2
 * speaks the same protocol (region "auto"), so no SDK is needed for reading ranges.
 */
final class AwsV4Signer {

    static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private AwsV4Signer() {}

    /**
     * Signs a GET of path on host. headers must already hold every header to sign except
     * host, x-amz-date and x-amz-content-sha256, which are added here. Returns all headers
     * to send, including Authorization (host is set by the HTTP client).
     */
    static Map<String, String> signGet(String host, String path, Map<String, String> headers, ZonedDateTime time,
                                       String region, String accessKey, String secretKey) {
        String amzDate = STAMP.format(time);
        String day = amzDate.substring(0, 8);

        TreeMap<String, String> canonical = new TreeMap<>();
        headers.forEach((k, v) -> canonical.put(k.toLowerCase(), v.trim()));
        canonical.put("host", host);
        canonical.put("x-amz-content-sha256", EMPTY_SHA256);
        canonical.put("x-amz-date", amzDate);

        StringBuilder canonicalHeaders = new StringBuilder();
        canonical.forEach((k, v) -> canonicalHeaders.append(k).append(':').append(v).append('\n'));
        String signedHeaders = String.join(";", canonical.keySet());

        String request = "GET\n" + encodePath(path) + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + EMPTY_SHA256;
        String scope = day + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(request);

        byte[] key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, "s3");
        key = hmac(key, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(key, toSign));

        TreeMap<String, String> out = new TreeMap<>(canonical);
        out.remove("host");
        out.put("authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ",SignedHeaders=" + signedHeaders + ",Signature=" + signature);
        return out;
    }

    /** URI-encodes each path segment the way S3 expects (keeps '/' and unreserved characters). */
    static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || c == '/') {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return sb.toString();
    }

    private static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
