package service;

import model.Link;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebCrawler {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Pattern LINK_PATTERN = Pattern.compile("<a[^>]+href=[\"']([^\"'#]+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final Set<String> visitedUrls;
    private final Set<Link> collectedLinks;
    private final ExecutorService executorService;
    private final String domain;
    private final CountDownLatch latch;

    public WebCrawler(String url, int threadPoolSize) {
        String normalizedUrl = normalizeUrl(url);
        this.domain = extractDomain(normalizedUrl);
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.latch = new CountDownLatch(1);
        this.visitedUrls = Collections.synchronizedSet(new HashSet<>());
        this.collectedLinks = ConcurrentHashMap.newKeySet();
    }

    public void start(String url) {
        executorService.submit(new CrawlerTask(url));
    }

    public void waitUntilFinished() {
        try {
            latch.await();
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void printResults() {
        System.out.println("\nCollected Links (sorted by link label):");
        System.out.println("\n--- Crawling Results ---");
        System.out.println("Total pages visited: " + visitedUrls.size());
        System.out.println("Total links found: " + collectedLinks.size());
        collectedLinks.stream().sorted().forEach(link -> System.out.println(link.label() + " -> " + link.url()));
    }

    private class CrawlerTask implements Runnable {
        private final String url;

        public CrawlerTask(String url) {
            this.url = url;
        }

        @Override
        public void run() {
            if (visitedUrls.contains(url)) {
                checkAndFinish();
                return;
            }

            visitedUrls.add(url);
            System.out.println("Crawling: " + url);

            try {
                String content = fetchContent(url);
                List<Link> links = extractLinks(content, url);

                for (Link link : links) {
                    collectedLinks.add(link);
                    if (!visitedUrls.contains(link.url())) {
                        executorService.submit(new CrawlerTask(link.url()));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing " + url + ": " + e.getMessage());
            }

            checkAndFinish();
        }

        private void checkAndFinish() {
            if (executorService instanceof ThreadPoolExecutor exec) {
                if (exec.getActiveCount() == 1 && exec.getQueue().isEmpty()) {
                    latch.countDown();
                }
            }
        }

        private String fetchContent(String urlString) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();
        }

        private List<Link> extractLinks(String content, String baseUrl) throws URISyntaxException {
            URI baseUri = new URI(baseUrl);

            return LINK_PATTERN.matcher(content)
                    .results()
                    .map(match -> createLink(match, baseUri))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        private Link createLink(MatchResult match, URI baseUri) {
            String href = match.group(1).trim();
            String label = match.group(2).replaceAll("<.*?>", "").strip();

            if (href.isEmpty() || label.isEmpty()) return null;

            try {
                URI resolvedUri = baseUri.resolve(href);
                if (domain.equals(resolvedUri.getHost())) {
                    return new Link(label, resolvedUri.toString());
                }
            } catch (IllegalArgumentException ignored) {
            }
            return null;
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
