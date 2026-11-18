package ru.vacancybot.bot.client;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Фейковый db-service на JDK HttpServer: запоминает запросы и отвечает заданными ответами. */
class FakeDbServer implements AutoCloseable {

    record Req(String method, String target, String body) {
    }

    record Resp(int status, String body) {
        static Resp ok(String body) {
            return new Resp(200, body);
        }

        static Resp status(int status) {
            return new Resp(status, "");
        }
    }

    final List<Req> requests = new ArrayList<>();
    Function<Req, Resp> handler = r -> Resp.status(404);
    private final HttpServer server;

    FakeDbServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String target = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            Req req = new Req(exchange.getRequestMethod(), target, body);
            synchronized (requests) {
                requests.add(req);
            }
            Resp resp = handler.apply(req);
            byte[] bytes = resp.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(resp.status(), resp.status() == 204 || bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0 && resp.status() != 204) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    Req last() {
        return requests.get(requests.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
