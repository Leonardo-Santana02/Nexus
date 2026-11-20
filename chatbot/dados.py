import json
import os
import subprocess
from base64 import b64encode
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
ARTEFATOS = RAIZ / "artifacts"
SISTEMA = (RAIZ.parent / "src/main/resources/chatbot/sistema.txt").read_text(encoding="utf-8").strip()
STATUS_COMPRAS = (
    "'APROVADO','EM_TRANSPORTE','ENTREGUE','EM_TROCA','TROCA_AUTORIZADA','TROCA_RECUSADA'"
)


def json_texto(valor):
    return json.dumps(valor, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def executar_sql(sql):
    # O psql usa PGHOST, PGPORT, PGDATABASE, PGUSER e PGPASSWORD/pgpass usuais.
    ambiente = {
        "PGHOST": "127.0.0.1", "PGDATABASE": "booknexus", "PGUSER": "postgres",
        **os.environ, "PGCLIENTENCODING": "UTF8",
    }
    return subprocess.run(
        [os.environ.get("BOOKNEXUS_PSQL", "psql"), "-XqAtw", "-v", "ON_ERROR_STOP=1"],
        input=sql.encode("utf-8"), stdout=subprocess.PIPE, check=True, env=ambiente,
    ).stdout.decode("utf-8")


def consulta(sql):
    return [json.loads(linha) for linha in executar_sql(sql).splitlines() if linha.strip()]


def sql_json(valor):
    # O transporte em Base64 mantém aspas, barras e caracteres de controle fora do SQL.
    dados = b64encode(json_texto(valor).encode("utf-8")).decode("ascii")
    return f"convert_from(decode('{dados}', 'base64'), 'UTF8')::jsonb"


def ler_jsonl(caminho):
    with Path(caminho).open(encoding="utf-8") as arquivo:
        return [json.loads(linha) for linha in arquivo if linha.strip()]


def gravar_jsonl(caminho, linhas):
    caminho = Path(caminho)
    caminho.parent.mkdir(parents=True, exist_ok=True)
    temporario = caminho.with_suffix(caminho.suffix + ".tmp")
    with temporario.open("w", encoding="utf-8", newline="\n") as arquivo:
        for linha in linhas:
            arquivo.write(json_texto(linha) + "\n")
    temporario.replace(caminho)


def livro(produto):
    return {
        "id": produto["id"], "titulo": produto["titulo"], "autor": produto["autor"],
        "genero": produto["genero"], "subgenero": produto.get("sub_genero"),
        "sinopse": (produto.get("sinopse") or "")[:240], "preco": produto["preco"],
        "paginas": produto["paginas"], "idioma": produto["idioma"],
        "ano": produto["ano_publicacao"], "editora": produto["editora"], "isbn": produto["isbn"],
    }


def compras_resumo(compras):
    unicos = {}
    for produto in compras:
        unicos.setdefault(produto["id"], produto)
    return [
        {campo: produto[campo] for campo in ("id", "titulo", "genero")}
        for produto in list(unicos.values())[:8]
    ]


def contexto(pergunta, favoritos, evitar, compras, candidatos, conversa=()):
    return {
        "pergunta": pergunta, "preferencias": {"favoritos": favoritos, "evitar": evitar},
        "historicoCompras": compras_resumo(compras),
        "candidatos": [livro(candidato) for candidato in candidatos], "conversaRecente": list(conversa),
    }
