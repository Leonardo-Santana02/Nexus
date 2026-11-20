import argparse
import hashlib
import json
import random
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from dados import ARTEFATOS, SISTEMA, STATUS_COMPRAS, consulta, contexto, gravar_jsonl, json_texto, ler_jsonl


def exemplo(cliente, origem, referencia, entrada, saida):
    entrada = {"selecionarLivro": False, **entrada}
    return {
        "grupo": cliente, "origem": origem, "referencia": referencia,
        "prompt": [{"role": "system", "content": SISTEMA},
                   {"role": "user", "content": json_texto(entrada)}],
        "completion": [{"role": "assistant", "content": json_texto(saida)}],
    }


def exemplos_vendas(catalogo, pedidos):
    disponiveis = {p["id"]: p for p in catalogo}
    historias = defaultdict(list)
    exemplos = []
    for pedido in pedidos:
        cliente = pedido["cliente"]
        historico = historias[cliente]
        anteriores = {p["id"] for p in historico}
        produtos = pedido["produtos"]
        alvo = next((p for p in produtos if p["id"] in disponiveis and p["id"] not in anteriores), None)
        if historico and alvo is not None:
            alvo = disponiveis[alvo["id"]]
            genero = alvo["genero"]
            outros = [p for p in catalogo if p["id"] != alvo["id"] and p["id"] not in anteriores]
            rng = random.Random(pedido["pedido"])
            rng.shuffle(outros)
            semelhantes = [p for p in outros if p["genero"] == genero][:3]
            diferentes = [p for p in outros if p["genero"] != genero][:3]
            candidatos = [alvo, *semelhantes, *diferentes]
            rng.shuffle(candidatos)
            perguntas = [
                "O que você me recomenda com base no que já comprei?",
                f"Pode sugerir um livro de {genero.lower()} que ainda não li?",
                f"Quero uma nova leitura de {genero.lower()}. O que encontro na loja?",
            ]
            entrada = contexto(perguntas[pedido["pedido"] % len(perguntas)], "", "", historico, candidatos)
            entrada["selecionarLivro"] = True
            texto = f"Minha sugestão é {alvo['titulo']}, de {alvo['autor']}, do gênero {genero.lower()}."
            if alvo.get("sinopse"):
                texto += " " + alvo["sinopse"][:240]
            saida = {"texto": texto, "livros": [alvo["id"]]}
            exemplos.append(exemplo(cliente, "venda", pedido["pedido"], entrada, saida))
        historico[:0] = produtos
    return exemplos


def resposta_valida(entrada, resposta):
    if not isinstance(resposta, dict) or set(resposta) != {"texto", "livros"}:
        return False
    texto, livros = resposta["texto"], resposta["livros"]
    if not isinstance(texto, str) or not 0 < len(texto.strip()) <= 2000:
        return False
    if not isinstance(livros, list) or len(livros) > 3 or any(type(i) is not int for i in livros):
        return False
    ids = {p["id"] for p in entrada["candidatos"]}
    return len(set(livros)) == len(livros) and ids.issuperset(livros)


def texto_do_catalogo(entrada, resposta):
    """Usa os dados que estavam no catálogo quando a resposta foi avaliada."""
    candidatos = {livro["id"]: livro for livro in entrada["candidatos"]}
    descricoes = []
    for id_ in resposta["livros"]:
        livro = candidatos[id_]
        descricao = " · ".join(livro[campo] for campo in ("titulo", "autor", "genero") if livro.get(campo))
        if livro.get("sinopse"):
            descricao += "\n" + livro["sinopse"][:240]
        descricoes.append(descricao)
    return {**resposta, "texto": "\n\n".join(descricoes)} if descricoes else resposta


# Respostas gravadas pelo BookNexus sem consultar o modelo (cortesia, redirecionamento e orientações de compra).
RESPOSTAS_FIXAS = {"booknexus-escopo-v1", "booknexus-loja-v1"}


def votado_sobre_o_modelo(voto):
    """Só votos sobre uma escolha entre candidatos servem de exemplo para o ajuste do modelo."""
    contexto_salvo = voto["contexto"] or {}
    return voto["modelo"] not in RESPOSTAS_FIXAS and bool(contexto_salvo.get("candidatos"))


def exemplos_feedback(votos, correcoes):
    exemplos, revisao = [], []
    for voto in votos:
        id_ = voto["id"]
        contexto_salvo = voto["contexto"]
        original = voto["resposta"]
        correcao = correcoes.get(id_)
        saida = original if voto["util"] else correcao
        if saida is None or not resposta_valida(contexto_salvo, saida) or (not voto["util"] and saida == original):
            revisao.append({"resposta_id": id_, "contexto": contexto_salvo, "resposta": original,
                            "util": voto["util"], "correcao": None})
            continue
        origem = "feedback_util" if voto["util"] else "feedback_corrigido"
        if voto["modelo"] == "massa-sintetica-rnf0044":
            origem += "_sintetico"
        exemplos.append(exemplo(voto["cliente"], origem, id_, contexto_salvo, texto_do_catalogo(contexto_salvo, saida)))
    return exemplos, revisao


def dividir(exemplos, reservar=(), forcar_treino=()):
    todos = {exemplo["grupo"] for exemplo in exemplos}
    grupos = sorted(todos - set(reservar) - set(forcar_treino))
    random.Random(44).shuffle(grupos)
    if not grupos or len(todos) < 2:
        raise ValueError("É preciso haver exemplos de pelo menos dois clientes para separar treino e validação.")
    reservados = set(grupos[:max(1, round(len(grupos) * 0.2))]) | set(reservar)
    treino = [e for e in exemplos if e["grupo"] not in reservados]
    validacao = [e for e in exemplos if e["grupo"] in reservados]
    if not treino:
        raise ValueError("Os grupos reservados deixaram o conjunto de treino vazio.")
    return treino, validacao


def incorporar_anterior(diretorio, vendas, feedback, votos, demo, incluir_demo):
    """Reúne ciclos do mesmo banco, preservando clientes de validação como grupo."""
    treino_anterior = ler_jsonl(diretorio / "treino.jsonl")
    validacao_anterior = ler_jsonl(diretorio / "validacao.jsonl")
    reservados = {linha["grupo"] for linha in validacao_anterior} - (set(demo) if incluir_demo else set())
    if not incluir_demo:
        reservados.update(demo)

    def chave(linha):
        classe = "venda" if linha["origem"] == "venda" else "feedback"
        return linha["grupo"], classe, linha["referencia"]

    reavaliados = {(voto["cliente"], "feedback", voto["id"]) for voto in votos}
    acumulado = {chave(linha): linha for linha in treino_anterior + validacao_anterior
                 if chave(linha) not in reavaliados}
    acumulado.update((chave(linha), linha) for linha in vendas + feedback)
    treino, validacao = [], []
    for linha in acumulado.values():
        entrada = json.loads(linha["prompt"][-1]["content"])
        saida = json.loads(linha["completion"][0]["content"])
        # Exportações antigas não tinham o campo; o servidor exige livros quando a resposta indica obras.
        entrada.setdefault("selecionarLivro", bool(saida.get("livros")))
        atualizada = exemplo(linha["grupo"], linha["origem"], linha["referencia"], entrada,
                            texto_do_catalogo(entrada, saida))
        destino = validacao if linha["grupo"] in reservados else treino
        destino.append(atualizada)
    if not treino or not validacao:
        raise ValueError("O novo ciclo precisa manter clientes distintos no treino e na validação.")
    return treino, validacao


def main():
    parser = argparse.ArgumentParser(description="Exporta vendas e feedback para o ajuste fino do RNF0044.")
    parser.add_argument("--saida", type=Path, default=ARTEFATOS / "dados")
    parser.add_argument("--correcoes", type=Path, action="append", default=[])
    parser.add_argument("--dados-anteriores", type=Path,
                        help="Exportação anterior do mesmo banco, para preservar os exemplos e grupos de validação.")
    parser.add_argument("--incluir-perfis-demo", action="store_true",
                        help="Inclui os dois perfis de demonstração no treino dos ciclos posteriores.")
    args = parser.parse_args()
    if args.dados_anteriores and args.dados_anteriores.resolve() == args.saida.resolve():
        parser.error("Use uma saída diferente da exportação anterior.")
    arquivos = args.correcoes
    padrao = ARTEFATOS / "correcoes-demo.jsonl"
    # As correções sintéticas usam IDs de resposta do banco de treinamento; em ciclos posteriores,
    # aplicadas a outro banco, elas poderiam corrigir respostas que não são as mesmas.
    if not args.dados_anteriores and padrao.exists() and padrao not in arquivos:
        arquivos = [padrao, *arquivos]
    correcoes = {int(linha["resposta_id"]): linha["correcao"]
                 for caminho in arquivos for linha in ler_jsonl(caminho) if linha.get("correcao") is not None}

    catalogo = consulta("SELECT row_to_json(p) FROM produtos p WHERE ativo AND estoque > 0 ORDER BY id;")
    pedidos = consulta(f"""
        SELECT json_build_object('pedido', pe.id, 'cliente', pe.cliente_id,
                                 'produtos', json_agg(to_jsonb(p) ORDER BY i.id))
        FROM pedidos pe JOIN itens_pedido i ON pe.id = i.pedido_id
        JOIN produtos p ON p.id = i.produto_id
        WHERE pe.status IN ({STATUS_COMPRAS}) AND NOT EXISTS (
            SELECT 1 FROM itens_devolucao it JOIN devolucoes d ON d.id = it.devolucao_id
            WHERE it.item_pedido_id = i.id AND d.data_recebimento IS NOT NULL
        )
        GROUP BY pe.id ORDER BY pe.data_criacao, pe.id;
    """)
    votos = consulta("""
        SELECT json_build_object('id', r.id, 'cliente', r.cliente_id, 'contexto', r.contexto::jsonb,
                                 'resposta', r.resposta::jsonb, 'modelo', r.modelo, 'util', r.util)
        FROM chat_respostas r WHERE r.util IS NOT NULL ORDER BY r.id;
    """)
    demo = consulta("""
        SELECT to_json(id) FROM clientes WHERE email IN ('rn0044-01@booknexus.example', 'rn0044-06@booknexus.example');
    """)
    vendas = exemplos_vendas(catalogo, pedidos)
    ignorados = [voto for voto in votos if not votado_sobre_o_modelo(voto)]
    votos = [voto for voto in votos if votado_sobre_o_modelo(voto)]
    feedback, revisao = exemplos_feedback(votos, correcoes)
    args.saida.mkdir(parents=True, exist_ok=True)
    gravar_jsonl(args.saida / "feedback-para-revisao.jsonl", revisao)
    if args.dados_anteriores:
        treino, validacao = incorporar_anterior(args.dados_anteriores, vendas, feedback, votos, demo,
                                                args.incluir_perfis_demo)
    elif args.incluir_perfis_demo:
        treino, validacao = dividir(vendas + feedback, forcar_treino=demo)
    else:
        treino, validacao = dividir(vendas + feedback, reservar=demo)
    manifesto = {"criado_em": datetime.now(timezone.utc).isoformat(), "votos_pendentes": len(revisao),
                 "votos_sem_escolha_do_modelo": len(ignorados),
                 "votos_exportados": {"uteis": sum(1 for v in votos if v["util"]),
                                      "nao_uteis": sum(1 for v in votos if not v["util"])},
                 "conjuntos": {}}
    for nome, linhas in (("treino", treino), ("validacao", validacao)):
        arquivo = args.saida / f"{nome}.jsonl"
        gravar_jsonl(arquivo, linhas)
        manifesto["conjuntos"][nome] = {
            "total": len(linhas), "origens": dict(Counter(e["origem"] for e in linhas)),
            "sha256": hashlib.sha256(arquivo.read_bytes()).hexdigest(),
        }
    (args.saida / "manifesto.json").write_text(json.dumps(manifesto, indent=2), encoding="utf-8")
    print(json.dumps(manifesto, indent=2))


if __name__ == "__main__":
    main()
