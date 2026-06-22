package com.xavierruiz.app.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherApi {
    private final String baseUrl = "https://api.weatherapi.com/v1/current.json";
    private final String city;
    private final String apiKey;

    public WeatherApi(String city) {
        this.city = city;
        this.apiKey = System.getenv("WEATHER_API_KEY");
    }

    public String getRequest() {
        String uri = String.format("%s?q=%s&key=%s", this.baseUrl, this.city, this.apiKey);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status Code: " + response.statusCode());
//            System.out.println("Response Body:\n" + response.body());
            return response.body();
        } catch (InterruptedException | IOException e) {
            System.err.println("Error fetching data from api: " + e);
            return null;
        }
    }
}
