package com.thoughtworks.mingle.client;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class MingleRequestSigningInterceptor implements HttpRequestInterceptor {
    private final String accessKey;
    private final String secretKey;

    public MingleRequestSigningInterceptor(String accessKey, java.lang.String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public void process(HttpRequest request, HttpContext httpContext) throws HttpException {
        String requestPathWithApiVersion;
        requestPathWithApiVersion = getRequestPath(request);
        String contentType = requestContentType(request);
        try {
            requestContentType(request);
            String requestBody = getRequestBody(request);
            sign(request, requestPathWithApiVersion, requestBody, contentType);
        } catch (Exception e) {
            throw makeHttpException(e.getLocalizedMessage());
        }
    }

    private String getRequestBody(HttpRequest request) {
        return request.getRequestLine().getUri();
    }

    private HttpException makeHttpException(String message) {
        return new HttpException("com.thoughtworks.mingle.client.MingleRequestSigningException:" + message);
    }

    private String getRequestPath(HttpRequest request) {
        return getRequestBody(request).toString().replaceAll("http[s]?://[^\\s,\\/\\?]+", "");
    }

    private String requestContentType(HttpRequest request) {
        Header[] contentTypeHeaders = request.getHeaders(MingleClientConstants.CONTENT_TYPE_HEADER_NAME);
        if(contentTypeHeaders.length == 0) {
            request.setHeader(
                    new BasicHeader(MingleClientConstants.CONTENT_TYPE_HEADER_NAME,MingleClientConstants.XML_CONTENT_TYPE)
            );
            return MingleClientConstants.XML_CONTENT_TYPE;
        }
        return contentTypeHeaders[0].getValue();
    }

    private void sign(HttpRequest request, String requestPath, String contentToEncode, String contentType) throws NoSuchAlgorithmException, MingleRequestSigningException {
        String contentMd5 = calculateMD5(contentToEncode);
        String formattedDate = getFormattedDate();
        String canonicalString = String.format("%s,%s,%s,%s", contentType, contentMd5, requestPath, formattedDate);
        String hmac = calculateHMAC(this.secretKey, canonicalString);
        String authHeaderValue = String.format("APIAuth %s:%s", this.accessKey, hmac);

        request.setHeader(new BasicHeader(MingleClientConstants.DATE_HEADER_NAME, formattedDate));
        request.setHeader(new BasicHeader(MingleClientConstants.CONTENT_MD5_HEADER_NAME, contentMd5));
        request.setHeader(new BasicHeader(MingleClientConstants.AUTHORIZATION_HEADER_NAME, authHeaderValue));
        request.setHeader(new BasicHeader(MingleClientConstants.CONTENT_TYPE_HEADER_NAME, contentType));
    }

    private String calculateHMAC(String secret, String data) throws MingleRequestSigningException {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), "HmacSHA1");
            Mac mac = Mac.getInstance(MingleClientConstants.HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.getBytes());
            return base64Encode(rawHmac);
        } catch (GeneralSecurityException e) {
            throw new MingleRequestSigningException("Error when calculating HMAC signature: " + e.getMessage());
        }
    }

    private String calculateMD5(String contentToEncode) throws NoSuchAlgorithmException {
        String result = "";
        if (!contentToEncode.equals("")) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(contentToEncode.getBytes());
            result = new String(base64Encode(digest.digest()));
        }
        return result;
    }

    private String base64Encode(byte[] data){
        return DatatypeConverter.printBase64Binary(data);
    }

    private String getFormattedDate() {
        return ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }
}
