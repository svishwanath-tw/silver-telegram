package com.thoughtworks.mingle.client;

import org.apache.http.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class MingleRequestSigningInterceptor implements HttpRequestInterceptor {
    private final String accessKey;
    private final String secretKey;
    private static final String EMPTY_STRING = "";

    public MingleRequestSigningInterceptor(String accessKey, java.lang.String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public void process(final HttpRequest request, final HttpContext httpContext) throws HttpException {
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

    private String getRequestBody(final HttpRequest request) throws MingleRequestSigningException {
        if(request instanceof HttpEntityEnclosingRequest){
            try {
                HttpEntityEnclosingRequest httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) request;
                return EntityUtils.toString(httpEntityEnclosingRequest.getEntity());
            } catch (IOException e) {
                throw new MingleRequestSigningException("Error reading request body!!");
            }
        }
        return EMPTY_STRING;
    }

    private HttpException makeHttpException(String message) {
        return new HttpException("MingleRequestSigningException:" + message);
    }

    private String getRequestPath(HttpRequest request) throws HttpException {
        return request.getRequestLine().getUri().toString().replaceAll("http[s]?://[^\\s,\\/\\?]+", "");
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

    private void sign(HttpRequest request, String requestPath, String content, String contentType) throws NoSuchAlgorithmException, MingleRequestSigningException, UnsupportedEncodingException {
        String contentMd5 = calculateMD5(content);
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

    private String calculateMD5(String content) throws NoSuchAlgorithmException {
        String result = EMPTY_STRING;
        if (!content.equals(EMPTY_STRING)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(content.getBytes());
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
