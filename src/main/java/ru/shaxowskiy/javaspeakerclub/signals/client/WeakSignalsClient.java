package ru.shaxowskiy.javaspeakerclub.signals.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;
import ru.shaxowskiy.javaspeakerclub.signals.WeakSignalsProperties;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentScoreResponse;

import java.util.concurrent.TimeoutException;

/**
 * HTTP-клиент сервиса weak-signals. Ошибки сервиса не пробрасываются наружу как есть: тело ответа только в лог.
 */
@Slf4j
@Component
public class WeakSignalsClient {

    static final String SCORE_PATH = "/signals/weak-signals";

    private final WebClient webClient;
    private final WeakSignalsProperties properties;

    public WeakSignalsClient(@Qualifier("weakSignalsWebClient") WebClient webClient, WeakSignalsProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public TalentScoreResponse score(TalentRequest request) {
        try {
            TalentScoreResponse response = webClient.post()
                    .uri(SCORE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TalentScoreResponse.class)
                    .timeout(properties.requestTimeout())
                    .block();
            if (response == null) {
                throw badGateway("Сервис оценки спикеров вернул пустой ответ");
            }
            return response;
        } catch (WebClientResponseException e) {
            log.warn("Weak-signals failed for speaker {}: {} {}", request.id(), e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                throw gatewayTimeout();
            }
            throw badGateway("Сервис оценки спикеров вернул ошибку");
        } catch (WebClientRequestException e) {
            log.warn("Weak-signals unreachable for speaker {}: {}", request.id(), e.getMessage());
            throw badGateway("Сервис оценки спикеров недоступен");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            if (Exceptions.unwrap(e) instanceof TimeoutException) {
                log.warn("Weak-signals timed out for speaker {} after {}", request.id(), properties.requestTimeout());
                throw gatewayTimeout();
            }
            throw e;
        }
    }

    private static ResponseStatusException badGateway(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
    }

    private static ResponseStatusException gatewayTimeout() {
        return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Сервис оценки спикеров не ответил вовремя");
    }
}
