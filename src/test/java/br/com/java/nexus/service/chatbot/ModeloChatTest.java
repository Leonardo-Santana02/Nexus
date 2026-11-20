package br.com.java.nexus.service.chatbot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModeloChatTest {
    private final JsonMapper json = JsonMapper.builder().build();

    private ModeloChat modelo() throws Exception {
        return new ModeloChat(json, URI.create("http://127.0.0.1:8081/v1/chat/completions"),
                "booknexus-qwen-base", 90, new ByteArrayResource("Somente livros.".getBytes()));
    }

    @Test
    void restringeGeracaoAosIdsReaisEmVezDePermitirNumerosDePosicao() throws Exception {
        var corpo = json.valueToTree(modelo().requisicao(
                "{\"selecionarLivro\":true,\"candidatos\":[{\"id\":25},{\"id\":28},{\"id\":32}]}"));
        var livros = corpo.path("response_format").path("schema").path("properties").path("livros");
        var ids = livros.path("items").path("enum");
        assertEquals(3, ids.size());
        assertEquals(25L, ids.path(0).asLong());
        assertEquals(28L, ids.path(1).asLong());
        assertEquals(32L, ids.path(2).asLong());
        assertEquals(3, livros.path("maxItems").asInt());
        assertEquals(1, livros.path("minItems").asInt());
    }

    @Test
    void catalogoVazioExigeListaVaziaSemEnumInvalido() throws Exception {
        var corpo = json.valueToTree(modelo().requisicao("{\"candidatos\":[]}"));
        var livros = corpo.path("response_format").path("schema").path("properties").path("livros");
        assertEquals(0, livros.path("maxItems").asInt());
        assertFalse(livros.path("items").has("enum"));
    }

    @Test
    void interpretaRespostaValidaEMantemIdsSemCoercaoDeStrings() throws Exception {
        assertEquals(new ModeloChat.Saida("Sugiro Café na Rua do Sol.", List.of(25L)),
                modelo().interpretar(envelope("{\"texto\":\"Sugiro Café na Rua do Sol.\",\"livros\":[25]}", "stop")));
        assertThrows(ModeloChat.RespostaInvalida.class, () -> modelo().interpretar(
                envelope("{\"texto\":\"Sugestão\",\"livros\":[\"25\"]}", "stop")));
    }

    @Test
    void sinalizaJsonMalformadoERespostaTruncadaParaUmaNovaTentativa() throws Exception {
        assertThrows(ModeloChat.RespostaInvalida.class,
                () -> modelo().interpretar(envelope("{incompleto", "stop")));
        assertThrows(ModeloChat.RespostaInvalida.class,
                () -> modelo().interpretar(envelope("{\"texto\":\"Sugestão\",\"livros\":[]}", "length")));
    }

    @Test
    void aliasDiferenteContinuaSendoErroDeConfiguracao() throws Exception {
        String resposta = envelope("{\"texto\":\"Sugestão\",\"livros\":[]}", "stop")
                .replace("booknexus-qwen-base", "outro-modelo");
        var erro = assertThrows(ModeloChat.ModeloIndisponivel.class, () -> modelo().interpretar(resposta));
        assertFalse(erro instanceof ModeloChat.RespostaInvalida);
    }

    private String envelope(String conteudo, String motivo) {
        return json.writeValueAsString(Map.of("model", "booknexus-qwen-base", "choices", List.of(
                Map.of("message", Map.of("content", conteudo), "finish_reason", motivo))));
    }
}
