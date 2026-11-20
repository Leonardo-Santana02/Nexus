package br.com.java.nexus.controller;

import br.com.java.nexus.service.chatbot.ChatbotService;
import br.com.java.nexus.service.chatbot.ChatbotService.Preferencias;
import br.com.java.nexus.service.chatbot.ModeloChat.ModeloIndisponivel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/chatbot/clientes/{clienteId}")
public class ChatbotController {
    private final ChatbotService chatbot;

    public ChatbotController(ChatbotService chatbot) {
        this.chatbot = chatbot;
    }

    @GetMapping
    public ChatbotService.Estado abrir(@PathVariable Long clienteId) {
        return chatbot.abrir(clienteId);
    }

    @PutMapping("/preferencias")
    public Preferencias salvarPreferencias(@PathVariable Long clienteId, @Valid @RequestBody Perfil perfil) {
        return chatbot.salvarPreferencias(clienteId, new Preferencias(perfil.favoritos(), perfil.evitar()));
    }

    @PostMapping("/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatbotService.Mensagem enviar(@PathVariable Long clienteId, @Valid @RequestBody Pergunta pergunta) {
        return chatbot.perguntar(clienteId, pergunta.texto());
    }

    @PutMapping("/mensagens/{respostaId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void avaliar(@PathVariable Long clienteId, @PathVariable Long respostaId,
                        @Valid @RequestBody Voto voto) {
        chatbot.avaliar(clienteId, respostaId, voto.util());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> naoEncontrado(NoSuchElementException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(ModeloIndisponivel.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> modeloIndisponivel(ModeloIndisponivel e) {
        return Map.of("erro", e.getMessage());
    }

    public record Perfil(@Size(max = 200) String favoritos, @Size(max = 200) String evitar) {}
    public record Pergunta(@NotBlank @Size(max = 500) String texto) {}
    public record Voto(@NotNull Boolean util) {}
}
