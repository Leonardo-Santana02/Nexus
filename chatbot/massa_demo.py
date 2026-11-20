import argparse
from pathlib import Path

from dados import ARTEFATOS, consulta, contexto, executar_sql, gravar_jsonl, sql_json

CATALOGO = {
    "Fantasia": (
        ("O Farol das Runas", "Magia", "Uma aprendiz de cartógrafa descobre runas que mudam as rotas de navios."),
        ("O Bosque das Marés", "Aventura", "Irmãos cruzam uma floresta inundada para devolver uma relíquia."),
        ("Guardiões de Vidro", "Magia", "Em uma cidade de torres frágeis, um vidreiro aprende a criar guardiões."),
        ("O Mapa do Vento", "Aventura", "Uma viajante segue um mapa encantado por ilhas suspensas."),
        ("A Biblioteca do Inverno", "Magia", "Um bibliotecário encontra livros cujas histórias alteram as estações."),
        ("Jardins de Pedra", "Fábula", "Uma jovem jardineira desperta estátuas esquecidas no reino."),
        ("O Sino do Crepúsculo", "Aventura", "Uma mensageira atravessa montanhas com a ajuda de um sino mágico."),
        ("A Ponte das Estrelas", "Fábula", "Duas pessoas de mundos vizinhos tentam reabrir uma passagem celeste."),
    ),
    "Mistério": (
        ("Cartas do Porto", "Investigação", "Uma repórter reúne cartas anônimas para investigar o sumiço de um barco."),
        ("O Arquivo sem Nome", "Investigação", "Fichas de um arquivo municipal apontam para um caso esquecido."),
        ("A Testemunha da Chuva", "Suspense", "Uma testemunha desafia o relato oficial em uma vila durante as chuvas."),
        ("O Último Quarto", "Suspense", "Uma detetive examina os registros de um hotel para explicar um desaparecimento."),
        ("Sussurros no Trilho", "Investigação", "Passageiros ajudam uma jornalista a reconstituir o trajeto de um trem."),
        ("As Chaves do Museu", "Investigação", "Um restaurador segue pistas depois do desaparecimento de uma pintura."),
        ("O Retrato sem Data", "Suspense", "Uma fotografia leva uma investigadora a reexaminar um acidente antigo."),
        ("Rua dos Relógios Parados", "Investigação", "Um relojoeiro reconstrói a rotina da rua para localizar um vizinho."),
    ),
    "Ciência": (
        ("Céu de Bolso", "Astronomia", "Um guia de observação do céu noturno com exercícios para iniciantes."),
        ("Rios em Movimento", "Ecologia", "Introdução aos rios e às espécies que vivem em suas margens."),
        ("Diário de uma Galáxia", "Astronomia", "Explica como galáxias nascem a partir de observações astronômicas."),
        ("Pequenos Climas", "Ecologia", "Mostra como árvores e ruas influenciam o clima de um bairro."),
        ("O Ofício de Medir", "Divulgação científica", "Apresenta experimentos simples para entender medidas e incertezas."),
        ("Oceanos em Camadas", "Ecologia", "Introdução ilustrada a correntes, profundidade e biodiversidade marinha."),
        ("De Onde Vem a Luz", "Astronomia", "Apresenta estrelas e a luz que chega à Terra em linguagem introdutória."),
        ("Sementes em Viagem", "Ecologia", "Explora como água, vento e animais espalham sementes."),
    ),
    "Romance": (
        ("Café na Rua do Sol", "Contemporâneo", "Dois vizinhos restauram um café e descobrem interesses em comum."),
        ("A Última Carta de Junho", "Contemporâneo", "Cartas antigas reaproximam dois amigos após anos distantes."),
        ("Domingos na Livraria", "Comédia romântica", "Uma livreira e um professor disputam a escolha do clube de leitura."),
        ("Entre Duas Estações", "Contemporâneo", "Uma estudante revê um antigo amor durante viagens de trem."),
        ("O Som de um Encontro", "Contemporâneo", "Dois músicos precisam conciliar carreira e relacionamento."),
        ("Rascunhos de Verão", "Comédia romântica", "Uma desenhista e um escritor tentam terminar um projeto juntos."),
        ("Bicicletas na Avenida", "Comédia romântica", "Dois ciclistas se conhecem tentando recuperar uma encomenda."),
        ("Sob o Mesmo Guarda-Chuva", "Contemporâneo", "Uma chuva inesperada aproxima duas pessoas em uma cidade nova."),
    ),
}

SEMENTE_SQL = """
BEGIN;
SELECT pg_advisory_xact_lock(440044);
INSERT INTO produtos (isbn, titulo, autor, editora, ano_publicacao, paginas, idioma,
    sinopse, preco, estoque, genero, sub_genero, peso, ativo, data_cadastro)
SELECT isbn, titulo, autor, 'Editora Demo BookNexus', ano, paginas, 'Português',
    sinopse, preco, 20, genero, subgenero, 0.35, true, CURRENT_DATE
FROM jsonb_to_recordset(@LIVROS@) AS p(isbn text, titulo text, autor text, ano int, paginas int,
    sinopse text, preco numeric, genero text, subgenero text)
ON CONFLICT (isbn) DO NOTHING;

CREATE TEMP TABLE perfis_demo ON COMMIT DROP AS
SELECT * FROM jsonb_to_recordset(@PERFIS@) AS p(email text, nome text, cpf text, genero text, evitar text, isbns jsonb);

INSERT INTO clientes (nome, cpf, email, genero, data_nascimento, inativado, ranking)
SELECT nome, cpf, email, 'OUTRO', DATE '1995-01-01', false, 0 FROM perfis_demo
ON CONFLICT (email) DO NOTHING;

INSERT INTO chat_preferencias (cliente_id, favoritos, evitar)
SELECT c.id, p.genero, p.evitar FROM perfis_demo p JOIN clientes c USING (email)
ON CONFLICT (cliente_id) DO NOTHING;

DO $seed$
DECLARE
    registro record;
    entrega bigint;
    pagamento bigint;
    pedido bigint;
    data_venda timestamp;
BEGIN
    FOR registro IN
        SELECT c.id AS cliente, c.email, livro.id AS produto, livro.preco, escolhidos.posicao
        FROM perfis_demo p JOIN clientes c USING (email)
        CROSS JOIN LATERAL jsonb_array_elements_text(p.isbns) WITH ORDINALITY AS escolhidos(isbn, posicao)
        JOIN produtos livro ON livro.isbn = escolhidos.isbn
        ORDER BY c.id, escolhidos.posicao
    LOOP
        IF EXISTS (
            SELECT 1 FROM pedidos pe JOIN itens_pedido i ON i.pedido_id = pe.id
            WHERE pe.cliente_id = registro.cliente AND i.produto_id = registro.produto
              AND pe.status = 'ENTREGUE'
        ) THEN CONTINUE; END IF;

        SELECT id INTO entrega FROM enderecos
        WHERE cliente_id = registro.cliente AND apelido = 'Demo RNF0044' ORDER BY id LIMIT 1;
        IF entrega IS NULL THEN
            INSERT INTO enderecos (tipo_logradouro, logradouro, numero, bairro, cidade, uf, cep, pais,
                                   endereco_entrega, endereco_cobranca, cliente_id, apelido)
            VALUES ('RUA', 'Endereço fictício de demonstração', '1', 'Teste', 'São Paulo', 'SP',
                    '01000-000', 'Brasil', true, true, registro.cliente, 'Demo RNF0044')
            RETURNING id INTO entrega;
        END IF;

        data_venda := CURRENT_DATE - (32 - registro.posicao)::int;
        INSERT INTO pagamentos (forma_pagamento, status, valor_original, valor_desconto, valor_pago,
                                data_criacao, data_confirmacao)
        VALUES ('CARTAO_CREDITO', 'APROVADO', registro.preco, 0, registro.preco, data_venda, data_venda)
        RETURNING id INTO pagamento;
        INSERT INTO pedidos (cliente_id, endereco_entrega_id, pagamento_id, status, data_criacao,
                             subtotal, desconto_promocional, valor_frete, valor_total)
        VALUES (registro.cliente, entrega, pagamento, 'ENTREGUE', data_venda, registro.preco, 0, 0, registro.preco)
        RETURNING id INTO pedido;
        INSERT INTO itens_pedido (pedido_id, produto_id, quantidade, preco_unitario)
        VALUES (pedido, registro.produto, 1, registro.preco);
    END LOOP;
END $seed$;
COMMIT;
"""


def cpf_teste(indice):
    numeros = [int(d) for d in f"{700000000 + indice:09d}"]
    for peso in (10, 11):
        resto = sum(digito * (peso - i) for i, digito in enumerate(numeros)) % 11
        numeros.append(0 if resto < 2 else 11 - resto)
    return "".join(map(str, numeros))


def preparar_massa(treinamento=False):
    livros = []
    perfis = []
    for i, (genero, entradas) in enumerate(CATALOGO.items()):
        isbns = []
        for j, (titulo, subgenero, sinopse) in enumerate(entradas):
            isbn = f"BNX-RNF-{i * len(entradas) + j + 1:03d}"
            isbns.append(isbn)
            livros.append(dict(isbn=isbn, titulo=titulo, autor=f"Autor Demo {i + 1}.{j + 1}", genero=genero,
                               subgenero=subgenero, sinopse=sinopse, preco=39 + j * 5,
                               ano=2018 + j, paginas=144 + j * 20))
        evitar = list(CATALOGO)[(i + 1) % len(CATALOGO)] if treinamento else ""
        for j in range(5):
            indice = i * 5 + j + 1
            demo = i < 2 and j == 0
            if not treinamento and not demo:
                continue
            nome = ("Lucas (Fantasia)" if i == 0 else "Maria (Mistério)") if demo else f"Leitor de teste {indice:02d} ({genero})"
            posicoes = [(j + k) % len(isbns) for k in range(5)]
            perfis.append(dict(email=f"rn0044-{indice:02d}@booknexus.example", nome=nome,
                               cpf=cpf_teste(indice), genero=genero, evitar=evitar, demo=demo,
                               isbns=[isbns[posicao] for posicao in posicoes]))
    return livros, perfis


def semear_feedback(perfis, destino):
    produtos = {p["isbn"]: p for p in consulta(
        "SELECT row_to_json(p) FROM produtos p WHERE isbn LIKE 'BNX-RNF-%' ORDER BY isbn;"
    )}
    clientes = {p["email"]: p["id"] for p in consulta(
        "SELECT json_build_object('id', id, 'email', email) FROM clientes WHERE email LIKE 'rn0044-%@booknexus.example';"
    )}
    entradas = []
    for perfil in perfis:
        if perfil["demo"]:
            continue
        historico = [produtos[isbn] for isbn in reversed(perfil["isbns"])]
        indicados = [p for p in produtos.values() if p["genero"] == perfil["genero"]
                     and p["isbn"] not in perfil["isbns"]]
        distrator = next(p for p in produtos.values() if p["genero"] == perfil["evitar"])
        alvo, outro, terceiro = indicados
        preco = alvo["preco"]
        exemplos = (
            ("Pode me indicar uma leitura de acordo com o meu histórico?", alvo, True,
             f"Minha indicação é {alvo['titulo']}. É de {alvo['genero'].lower()} e combina com suas leituras. {alvo['sinopse']}"),
            (f"Do que trata {outro['titulo']}?", outro, True, outro["sinopse"]),
            (f"Tem alguma indicação de {perfil['genero'].lower()} em até R$ {preco}?", alvo, False,
             f"Sugiro {alvo['titulo']}, que custa R$ {preco:.2f} e está dentro do seu orçamento. {alvo['sinopse']}"),
            (f"Quem escreveu {terceiro['titulo']}?", terceiro, False,
             f"{terceiro['titulo']} foi escrito por {terceiro['autor']}."),
        )
        for pergunta, escolhido, util, texto in exemplos:
            candidatos = [alvo] if pergunta.startswith("Tem alguma") else [distrator, alvo, outro, terceiro]
            corrigida = {"texto": texto, "livros": [escolhido["id"]]}
            entradas.append({
                "cliente_id": clientes[perfil["email"]], "pergunta": pergunta,
                "contexto": contexto(pergunta, perfil["genero"], perfil["evitar"], historico, candidatos),
                "resposta": corrigida if util else {"texto": "Experimente um título qualquer do catálogo.", "livros": []},
                "util": util, "correcao": None if util else corrigida,
            })
    sql = """
    BEGIN;
    SELECT pg_advisory_xact_lock(440044);
    CREATE TEMP TABLE votos_demo ON COMMIT DROP AS
    SELECT * FROM jsonb_to_recordset(@VOTOS@) AS v(cliente_id bigint, pergunta text,
        contexto jsonb, resposta jsonb, util boolean, correcao jsonb);
    INSERT INTO chat_respostas (cliente_id, pergunta, contexto, resposta, modelo, criada_em, util, avaliada_em)
    SELECT v.cliente_id, v.pergunta, v.contexto::text, v.resposta::text,
           'massa-sintetica-rnf0044', now(), v.util, now()
    FROM votos_demo v WHERE NOT EXISTS (
        SELECT 1 FROM chat_respostas r WHERE r.cliente_id = v.cliente_id AND r.pergunta = v.pergunta
          AND r.modelo = 'massa-sintetica-rnf0044'
    );
    SELECT json_build_object('resposta_id', r.id, 'correcao', v.correcao)
    FROM votos_demo v JOIN chat_respostas r USING (cliente_id, pergunta)
    WHERE r.modelo = 'massa-sintetica-rnf0044' AND v.correcao IS NOT NULL;
    COMMIT;
    """
    correcoes = consulta(sql.replace("@VOTOS@", sql_json(entradas)))
    gravar_jsonl(destino, correcoes)
    return len(entradas), len(correcoes)


def main():
    parser = argparse.ArgumentParser(description="Carrega dados fictícios e idempotentes para o RNF0044.")
    parser.add_argument("--correcoes", type=Path, default=ARTEFATOS / "correcoes-demo.jsonl")
    parser.add_argument("--treinamento", action="store_true",
                        help="Carga ampliada de 20 clientes e feedback sintético, para um banco de treinamento separado.")
    args = parser.parse_args()
    livros, perfis = preparar_massa(treinamento=args.treinamento)
    executar_sql(SEMENTE_SQL.replace("@LIVROS@", sql_json(livros)).replace("@PERFIS@", sql_json(perfis)))
    votos, correcoes = semear_feedback(perfis, args.correcoes) if args.treinamento else (0, 0)
    print(f"Massa sintética: {len(perfis)} clientes, {len(livros)} livros, "
          f"{sum(len(p['isbns']) for p in perfis)} vendas, {votos} votos e {correcoes} correções.")
    print("Perfis para testar ao vivo: Lucas (Fantasia) e Maria (Mistério).")


if __name__ == "__main__":
    main()
