package com.thoughtworks.mingle.client;

public class MingleUrlHelper {
    private String baseUrl;

    public MingleUrlHelper(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRequestUrl(String requestPath) {
        return String.format("%s%s",this.baseUrl, prependApiVersion(requestPath));
    }

    private String prependApiVersion(String requestPath) {
        return String.format("/api/v2%s", requestPath);
    }

}
