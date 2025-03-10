import service.WebCrawler;

public class Main {
    public static void main(String[] args) {
        String url = "https://orf.at/";
        int threadPoolSize = 10;

        WebCrawler crawler = new WebCrawler(url, threadPoolSize);
        crawler.start(url);
        crawler.waitUntilFinished();
        crawler.printResults();
    }
}