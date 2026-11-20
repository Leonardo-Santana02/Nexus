import argparse
import json
import urllib.request
from pathlib import Path
from time import monotonic

from dados import ARTEFATOS, gravar_jsonl, ler_jsonl
from exportar import resposta_valida

def formato_resposta(contexto):
    ids = [livro["id"] for livro in contexto["candidatos"]]
    itens = {"type": "integer"}
    if ids:
        itens["enum"] = ids
    return {"type": "json_object", "schema": {
        "type": "object", "required": ["texto", "livros"], "additionalProperties": False,
        "properties": {"texto": {"type": "string", "minLength": 1, "maxLength": 2000},
                       "livros": {"type": "array", "items": itens, "maxItems": min(3, len(ids)),
                                  "minItems": int(bool(ids) and contexto.get("selecionarLivro", False))}},
    }}


def main():
    parser = argparse.ArgumentParser(description="Avalia o modelo em execução com exemplos reservados.")
    parser.add_argument("--dados", type=Path, default=ARTEFATOS / "dados/validacao.jsonl")
    parser.add_argument("--modelo", required=True)
    parser.add_argument("--saida", type=Path, required=True)
    parser.add_argument("--limite", type=int)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8081/v1/chat/completions")
    args = parser.parse_args()
    linhas = ler_jsonl(args.dados)[:args.limite]
    if not linhas:
        parser.error("O conjunto de validação está vazio.")
    resultados = []
    for exemplo in linhas:
        contexto = json.loads(exemplo["prompt"][-1]["content"])
        esperada = json.loads(exemplo["completion"][0]["content"])
        pedido = urllib.request.Request(args.endpoint, json.dumps({
            "model": args.modelo, "messages": exemplo["prompt"], "max_tokens": 420,
            "temperature": 0, "response_format": formato_resposta(contexto),
        }).encode("utf-8"), {"Content-Type": "application/json"})
        inicio = monotonic()
        with urllib.request.urlopen(pedido, timeout=120) as resposta:
            envelope = json.load(resposta)
        if envelope.get("model") != args.modelo:
            raise RuntimeError("O alias retornado pelo servidor diverge do alias escolhido para a avaliação.")
        original = envelope["choices"][0]["message"]["content"]
        try:
            gerada = json.loads(original)
        except json.JSONDecodeError:
            gerada = None
        valida = resposta_valida(contexto, gerada)
        acerto = valida and (
            bool(set(gerada["livros"]) & set(esperada["livros"]))
            if esperada["livros"] else not gerada["livros"]
        )
        resultados.append({
            "origem": exemplo["origem"], "referencia": exemplo["referencia"], "modelo": args.modelo,
            "pergunta": contexto["pergunta"], "esperada": esperada, "gerada": gerada,
            "invalida": None if valida else original, "ids_validos": valida,
            "acerto_referencia": acerto, "segundos": round(monotonic() - inicio, 3),
        })
    gravar_jsonl(args.saida, resultados)
    print(json.dumps({
        "modelo": args.modelo, "n": len(resultados),
        "ids_validos": sum(r["ids_validos"] for r in resultados),
        "acertos_referencia": sum(r["acerto_referencia"] for r in resultados),
        "latencia_media_segundos": round(sum(r["segundos"] for r in resultados) / len(resultados), 3),
    }, indent=2))


if __name__ == "__main__":
    main()
