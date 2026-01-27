package fr.unilasalle.chat.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WeatherService {
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static String getWeather(String city) {
        try {
            // wttr.in returns simple text. format=3 gives a one-line summary "City:
            // Condition Temp".
            // We replace spaces in city name with '+' for the URL.
            String url = "https://wttr.in/" + city.replace(" ", "+") + "?format=3";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                return "Error: Could not retrieve weather (HTTP " + response.statusCode() + ")";
            }
        } catch (Exception e) {
            return "Error fetching weather: " + e.getMessage();
        }
    }
}
