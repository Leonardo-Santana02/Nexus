(() => {
    const selector = document.getElementById('clienteSelector');
    const painel = document.getElementById('chatSidebar');
    const abrir = document.getElementById('chatToggleBtn');
    const fechar = document.getElementById('chatCloseBtn');
    const camada = document.getElementById('chatOverlay');
    const quadro = document.getElementById('chatMessages');
    const aviso = document.getElementById('chatStatus');
    const formulario = document.getElementById('chatForm');
    const entrada = document.getElementById('chatInput');
    const enviar = document.getElementById('chatSendBtn');
    const perfil = document.getElementById('chatPreferences');
    const favoritos = document.getElementById('chatFavorites');
    const evitar = document.getElementById('chatAvoid');
    let sessao;

    function status(texto) {
        aviso.textContent = texto;
        aviso.hidden = !texto;
    }

    function mostrar(aberto) {
        painel.classList.toggle('open', aberto);
        painel.inert = !aberto;
        camada.classList.toggle('active', aberto);
        abrir.setAttribute('aria-expanded', String(aberto));
        if (!aberto) {
            abrir.focus();
            return;
        }
        if (sessao?.cliente !== selector.value || !sessao?.pronta) carregar();
        (entrada.disabled ? fechar : entrada).focus();
    }

    function controles(ativa) {
        entrada.disabled = !ativa;
        enviar.disabled = !ativa;
        for (const campo of perfil.elements) campo.disabled = !ativa;
    }

    async function api(atual, rota = '', metodo = 'GET', dados) {
        const resposta = await fetch('/api/chatbot/clientes/' + encodeURIComponent(atual.cliente) + rota, {
            method: metodo,
            cache: 'no-store',
            signal: atual.abort.signal,
            headers: dados === undefined ? {} : { 'Content-Type': 'application/json' },
            body: dados === undefined ? undefined : JSON.stringify(dados)
        });
        if (resposta.status === 204) return;
        const conteudo = await resposta.json().catch(() => ({}));
        if (!resposta.ok) throw new Error(conteudo.erro || conteudo.detail || 'Não foi possível completar a ação.');
        return conteudo;
    }

    function balao(texto, tipo) {
        const bloco = document.createElement('div');
        bloco.className = 'message ' + tipo;
        bloco.textContent = texto;
        quadro.append(bloco);
        quadro.scrollTop = quadro.scrollHeight;
        return bloco;
    }

    function mostrarTurno(atual, mensagem) {
        balao(mensagem.pergunta, 'user');
        const resposta = balao(mensagem.texto, 'bot');
        const feedback = document.createElement('div');
        feedback.className = 'chat-feedback';
        const botoes = [true, false].map(util => {
            const botao = document.createElement('button');
            botao.type = 'button';
            botao.textContent = util ? 'Útil' : 'Não útil';
            botao.setAttribute('aria-pressed', String(mensagem.util === util));
            botao.addEventListener('click', async () => {
                botoes.forEach(item => { item.disabled = true; });
                try {
                    await api(atual, '/mensagens/' + mensagem.id + '/feedback', 'PUT', { util });
                    if (sessao !== atual) return;
                    botoes.forEach(item => item.setAttribute('aria-pressed', String(item === botao)));
                    status('Avaliação registrada.');
                } catch (erro) {
                    if (sessao === atual) status(erro.message);
                } finally {
                    botoes.forEach(item => { item.disabled = false; });
                }
            });
            return botao;
        });
        feedback.append(...botoes);
        resposta.append(feedback);
        quadro.scrollTop = quadro.scrollHeight;
    }

    async function carregar() {
        sessao?.abort.abort();
        const atual = { cliente: selector.value, abort: new AbortController(), pronta: false };
        sessao = atual;
        quadro.replaceChildren();
        entrada.value = favoritos.value = evitar.value = '';
        controles(false);
        if (!atual.cliente) {
            status('Selecione um cliente na vitrine para iniciar a conversa.');
            return;
        }

        status('Carregando conversa…');
        try {
            const estado = await api(atual);
            if (sessao !== atual) return;
            favoritos.value = estado.preferencias.favoritos;
            evitar.value = estado.preferencias.evitar;
            if (!estado.mensagens.length) {
                balao('Olá! Posso encontrar livros no catálogo, responder dúvidas e sugerir leituras.', 'bot');
            }
            estado.mensagens.forEach(mensagem => mostrarTurno(atual, mensagem));
            atual.pronta = true;
            controles(true);
            status('');
        } catch (erro) {
            if (sessao === atual) status(erro.message + ' Feche e reabra o chat para tentar de novo.');
        }
    }

    formulario.addEventListener('submit', async evento => {
        evento.preventDefault();
        const atual = sessao;
        const texto = entrada.value.trim();
        if (!atual?.pronta || !texto || enviar.disabled) return;
        enviar.disabled = true;
        entrada.readOnly = true;
        status('Buscando livros e preparando a resposta…');
        try {
            const mensagem = await api(atual, '/mensagens', 'POST', { texto });
            if (sessao !== atual) return;
            mostrarTurno(atual, mensagem);
            entrada.value = '';
            status('');
        } catch (erro) {
            if (sessao === atual) status(erro.message);
        } finally {
            if (sessao === atual) {
                enviar.disabled = false;
                entrada.readOnly = false;
                if (!painel.inert) entrada.focus();
            }
        }
    });

    perfil.addEventListener('submit', async evento => {
        evento.preventDefault();
        const atual = sessao;
        if (!atual?.pronta) return;
        const botao = perfil.querySelector('button');
        botao.disabled = true;
        try {
            await api(atual, '/preferencias', 'PUT', { favoritos: favoritos.value, evitar: evitar.value });
            if (sessao === atual) status('Preferências salvas.');
        } catch (erro) {
            if (sessao === atual) status(erro.message);
        } finally {
            if (sessao === atual) botao.disabled = false;
        }
    });

    abrir.addEventListener('click', () => mostrar(painel.inert));
    fechar.addEventListener('click', () => mostrar(false));
    camada.addEventListener('click', () => mostrar(false));
    selector.addEventListener('change', () => {
        entrada.readOnly = false;
        if (!painel.inert) carregar();
        else {
            sessao?.abort.abort();
            sessao = undefined;
        }
    });
    document.addEventListener('keydown', evento => {
        if (evento.key === 'Escape' && !painel.inert) mostrar(false);
    });
})();
