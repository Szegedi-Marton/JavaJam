package service;

import model.Music;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeScraperService {
    private static final Logger LOGGER = Logger.getLogger(YoutubeScraperService.class.getName());

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<Music> searchTracks(String query) {
        List<Music> tracks = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.youtube.com/results?search_query=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();

            Pattern videoBlockPattern = Pattern.compile(
                    "\"videoRenderer\":\\{(.*?)(?=,\"(?:trackingParams|menu|thumbnailOverlays)\"|\\}\\s*,\\s*\"videoRenderer\"|$)",
                    Pattern.DOTALL
            );

            Matcher matcher = videoBlockPattern.matcher(html);

            while (matcher.find() && tracks.size() < 10) {
                String block = matcher.group(1);

                Matcher idMatcher = Pattern.compile(
                        "\"videoId\":\"([a-zA-Z0-9_-]{11})\""
                ).matcher(block);

                Matcher titleMatcher = Pattern.compile(
                        "\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:\\\\.|[^\"\\\\])*)\""
                ).matcher(block);

                if (idMatcher.find() && titleMatcher.find()) {
                    String videoId = idMatcher.group(1);
                    String title = titleMatcher.group(1);

                    title = title
                            .replace("\\u0026", "&")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");

                    String videoUrl =
                            "https://www.youtube.com/watch?v=" + videoId;

                    tracks.add(new Music(title, videoUrl));
                }
            }

            LOGGER.info("Successfully scraped " + tracks.size() + " clean tracks.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error scraping YouTube", e);
        }
        return tracks;
    }
}