package br.com.java.nexus.service.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ModeloChat {
    private static final Logger LOG = LoggerFactory.getLogger(ModeloChat.class);

    private final ObjectMapper json;
    private final RestClient http;
    private final URI endpoint;
    private final String modelo;
    private final String instrucao;

    ModeloChat(ObjectMapper json, @Value("${booknexus.chat.endpoint}") URI endpoint,
               @Value("${booknexus.chat.modelo}") String modelo,
               @Value("${booknexus.chat.timeout-segundos}") long timeoutSegundos,
               @Value("classpath:chatbot/sistema.txt") Resource instrucao) throws IOException {
        if (!"http".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || !Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())
                || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("O endereço do modelo precisa apontar para o servidor local.");
        }
        if (modelo.isBlank() || modelo.length() > 160) {
            throw new IllegalArgumentException("Informe um alias válido para o modelo do chatbot.");
        }
        this.json = json;
        this.endpoint = endpoint;
        this.modelo = modelo;
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(4));
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.instrucao = instrucao.getContentAsString(StandardCharsets.UTF_8).strip();
    }

    String versao() {
        return modelo;
    }

    Saida responder(String contexto) {
        var corpo = requisicao(contexto);
        String resposta;
        try {
            resposta = http.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(corpo)).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            LOG.warn("Servidor local do chatbot retornou HTTP {}", e.getStatusCode().value());
            throw new ModeloIndisponivel();
        } catch (RestClientException e) {
            LOG.warn("Falha de comunicação com o servidor local do chatbot ({})", e.getClass().getSimpleName());
            throw new ModeloIndisponivel();
        }
        if (resposta == null) {
            throw new ModeloIndisponivel();
        }
        return interpretar(resposta);
    }

    Map<String, Object> requisicao(String contexto) {
        var dados = json.readTree(contexto);
        var candidatos = dados.path("candidatos");
        var permitidos = new ArrayList<Long>();
        for (int i = 0; i < candidatos.size(); i++) {
            permitidos.add(candidatos.path(i).path("id").asLong());
        }
        Map<String, Object> item = permitidos.isEmpty() ? Map.of("type", "integer")
                : Map.of("type", "integer", "enum", permitidos);
        int minimo = !permitidos.isEmpty() && dados.path("selecionarLivro").asBoolean() ? 1 : 0;
        Map<String, Object> formato = Map.of("type", "json_object", "schema", Map.of(
                "type", "object", "required", List.of("texto", "livros"), "additionalProperties", false,
                "properties", Map.of("texto", Map.of("type", "string", "minLength", 1, "maxLength", 2000),
                        "livros", Map.of("type", "array", "minItems", minimo,
                                "maxItems", Math.min(3, permitidos.size()), "items", item))));
        return Map.of("model", modelo, "temperature", 0.15, "max_tokens", 420, "response_format", formato,
                "messages", List.of(Map.of("role", "system", "content", instrucao),
                        Map.of("role", "user", "content", contexto)));
    }

    Saida interpretar(String resposta) {
        try {
            var documento = json.readTree(resposta);
            if (documento == null || !documento.isObject()) {
                throw new RespostaInvalida("envelope ausente");
            }
            if (!modelo.equals(documento.path("model").asText())) {
                LOG.warn("O alias do modelo respondente ({}) difere do alias configurado ({}).",
                        documento.path("model").asText(), modelo);
                throw new ModeloIndisponivel("O modelo em execução não corresponde ao configurado no BookNexus. "
                        + "Inicie o servidor com chatbot\\iniciar-modelo.ps1 e reinicie o BookNexus.");
            }
            var escolha = documento.path("choices").path(0);
            if ("length".equals(escolha.path("finish_reason").asText())) {
                throw new RespostaInvalida("geração interrompida pelo limite de tokens");
            }
            var conteudo = escolha.path("message").path("content").asText();
            var dados = json.readTree(conteudo);
            if (dados == null || !dados.path("texto").isString() || !dados.path("livros").isArray()) {
                throw new RespostaInvalida("campos texto/livros ausentes ou com tipo incorreto");
            }
            for (int i = 0; i < dados.path("livros").size(); i++) {
                var id = dados.path("livros").path(i);
                if (!id.isIntegralNumber() || !id.canConvertToLong()) {
                    throw new RespostaInvalida("ID de livro não é um inteiro válido");
                }
            }
            return json.readValue(conteudo, Saida.class);
        } catch (tools.jackson.core.JacksonException e) {
            throw new RespostaInvalida("JSON inválido na resposta");
        }
    }

    record Saida(String texto, List<Long> livros) {}

    static class RespostaInvalida extends ModeloIndisponivel {
        private final String motivo;

        RespostaInvalida(String motivo) {
            this.motivo = motivo;
        }

        String motivo() {
            return motivo;
        }
    }

    public static class ModeloIndisponivel extends RuntimeException {
        ModeloIndisponivel() {
            this("O assistente local não conseguiu responder. Tente novamente em instantes.");
        }

        ModeloIndisponivel(String mensagem) {
            super(mensagem);
        }
    }
}
