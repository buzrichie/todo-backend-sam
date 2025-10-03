package com.todo.utils;

import java.util.HashMap;
import java.util.Map;

public class CorsUtils {

    public static Map<String, String> createCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Amz-User-Agent,X-Requested-With");
        headers.put("Access-Control-Allow-Credentials", "true");
        headers.put("Access-Control-Max-Age", "600");
        return headers;
    }

    public static boolean isPreflightRequest(String httpMethod) {
        return "OPTIONS".equalsIgnoreCase(httpMethod);
    }
}