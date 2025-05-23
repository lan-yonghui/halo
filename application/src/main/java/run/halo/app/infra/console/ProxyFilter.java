package run.halo.app.infra.console;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.app.infra.properties.ProxyProperties;

@Slf4j
public class ProxyFilter implements WebFilter {

    private final ProxyProperties proxyProperties;

    private final ServerWebExchangeMatcher consoleMatcher;

    private final WebClient webClient;

    public ProxyFilter(String pattern, ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        var consoleMatcher = ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, pattern);
        consoleMatcher = new AndServerWebExchangeMatcher(consoleMatcher,
            new NegatedServerWebExchangeMatcher(new WebSocketServerWebExchangeMatcher()));
        this.consoleMatcher = consoleMatcher;
        this.webClient = WebClient.create(proxyProperties.getEndpoint().toString());
        log.debug("Initialized ProxyFilter to proxy {} to endpoint {}", pattern,
            proxyProperties.getEndpoint());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return consoleMatcher.matches(exchange)
            .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
            .switchIfEmpty(chain.filter(exchange).then(Mono.empty()))
            .map(matchResult -> {
                var request = exchange.getRequest();
                return UriComponentsBuilder.fromUriString(
                        request.getPath().pathWithinApplication().value())
                    .queryParams(request.getQueryParams())
                    .build()
                    .toUriString();
            })
            .doOnNext(uri -> {
                if (log.isTraceEnabled()) {
                    log.trace("Proxy {} to {}", uri, proxyProperties.getEndpoint());
                }
            })
            .flatMap(uri -> webClient.get()
                .uri(uri)
                .headers(httpHeaders -> httpHeaders.addAll(exchange.getRequest().getHeaders()))
                .exchangeToMono(clientResponse -> {
                    var response = exchange.getResponse();
                    var httpStatusCode = clientResponse.statusCode();
                    // set headers
                    var httpHeaders = clientResponse.headers().asHttpHeaders();
                    response.getHeaders().putAll(httpHeaders);
                    // set cookies
                    response.getCookies().putAll(clientResponse.cookies());
                    // set status code
                    response.setStatusCode(httpStatusCode);
                    var contentLength = clientResponse.headers().contentLength().orElse(0L);
                    if (httpStatusCode.is3xxRedirection()
                        || httpStatusCode.equals(HttpStatus.NO_CONTENT)
                        || contentLength == 0) {
                        return Mono.empty();
                    }
                    var body = clientResponse.bodyToFlux(DataBuffer.class);
                    return response.writeWith(body);
                }));
    }
}
