package br.com.java.nexus.controller;

import br.com.java.nexus.repository.produto.ProdutoRepository;
import br.com.java.nexus.service.chatbot.ChatbotService;
import br.com.java.nexus.service.cliente.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"
})
class ChatbotControllerTest {
    @Autowired
    private WebApplicationContext contexto;
    @MockitoBean
    private ChatbotService chatbot;
    @MockitoBean
    private ClienteService clientes;
    @MockitoBean
    private ProdutoRepository produtos;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = webAppContextSetup(contexto).build();
        when(clientes.listarAtivos()).thenReturn(List.of());
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0)).thenReturn(List.of());
    }

    @Test
    void exibeChatNaVitrineEEntregaRespostaPelaRotaDoCliente() throws Exception {
        mvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"chatPreferences\"")))
                .andExpect(content().string(containsString("/js/chatbot.js")));

        when(chatbot.perguntar(1L, "Sugira fantasia")).thenReturn(
                new ChatbotService.Mensagem(25L, "Sugira fantasia", "Encontrei uma opção.", List.of(),
                        "booknexus-qwen-lora-teste", null));

        mvc.perform(post("/api/chatbot/clientes/1/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Sugira fantasia\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.modelo").value("booknexus-qwen-lora-teste"));
    }

    @Test
    void rejeitaVotoAusenteEEncaminhaVotoNegativo() throws Exception {
        String rota = "/api/chatbot/clientes/1/mensagens/25/feedback";
        mvc.perform(put(rota).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(rota).contentType(MediaType.APPLICATION_JSON).content("{\"util\":false}"))
                .andExpect(status().isNoContent());
        verify(chatbot).avaliar(1L, 25L, false);
    }
}
