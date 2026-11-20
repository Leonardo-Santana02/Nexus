import argparse
import hashlib
import json
import os
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from time import monotonic

from dados import ARTEFATOS, ler_jsonl

MODELO_BASE = "Qwen/Qwen2.5-1.5B-Instruct"


def carregar_dados(diretorio, tokenizer, max_tokens):
    dados = {}
    estatisticas = {}
    for nome in ("treino", "validacao"):
        arquivo = diretorio / f"{nome}.jsonl"
        linhas = ler_jsonl(arquivo)
        selecionadas = [
            linha for linha in linhas
            if len(tokenizer.apply_chat_template(linha["prompt"] + linha["completion"])) <= max_tokens
        ]
        if not selecionadas:
            raise ValueError(f"Nenhum exemplo de {nome} cabe em {max_tokens} tokens.")
        dados[nome] = selecionadas
        estatisticas[nome] = {
            "origens": dict(Counter(linha["origem"] for linha in selecionadas)),
            "ignoradas_por_tamanho": len(linhas) - len(selecionadas),
            "sha256_exportacao": hashlib.sha256(arquivo.read_bytes()).hexdigest(),
        }
    origens = estatisticas["treino"]["origens"]
    if "venda" not in origens or not any(origem.startswith("feedback_") for origem in origens):
        raise ValueError("O conjunto de treino precisa incluir exemplos de vendas e de feedback.")
    return dados, estatisticas


def main():
    parser = argparse.ArgumentParser(description="Executa ajuste fino supervisionado LoRA do RNF0044.")
    parser.add_argument("--dados", type=Path, default=ARTEFATOS / "dados")
    parser.add_argument("--saida", type=Path, required=True)
    parser.add_argument("--base", default=MODELO_BASE, help="Modelo original ou caminho para um snapshot local.")
    parser.add_argument("--revisao", default=None, help="Commit Hugging Face do modelo base.")
    parser.add_argument("--dispositivo", choices=("cpu", "cuda"), default="cpu")
    parser.add_argument("--max-tokens", type=int, default=2048)
    parser.add_argument("--epocas", type=float, default=2)
    parser.add_argument("--max-passos", type=int, default=-1, help="Use poucos passos apenas no piloto.")
    args = parser.parse_args()
    if args.saida.exists():
        parser.error("--saida deve apontar para uma nova pasta da versão do adaptador.")

    # Um identificador Hugging Face tambem contem '/', como Qwen/modelo.
    # Caminhos locais precisam apontar para a pasta dos pesos originais.
    caminho_base = os.path.expanduser(args.base)
    eh_local = os.path.exists(caminho_base) or os.path.isabs(caminho_base) or "\\" in caminho_base or args.base.startswith((".", "~"))
    if eh_local:
        caminho_base = os.path.abspath(caminho_base)
        if not os.path.isdir(caminho_base):
            parser.error("--base deve apontar para uma pasta com os pesos originais do Qwen; o arquivo GGUF e usado apenas na inferencia.")
        obrigatorios = ("config.json", "tokenizer.json", "tokenizer_config.json")
        faltantes = [nome for nome in obrigatorios if not (Path(caminho_base) / nome).is_file()]
        if not (Path(caminho_base) / "model.safetensors").is_file() and not (Path(caminho_base) / "model.safetensors.index.json").is_file():
            faltantes.append("model.safetensors (ou pesos divididos com model.safetensors.index.json)")
        if faltantes:
            parser.error("Modelo original incompleto. Arquivos ausentes: " + ", ".join(faltantes))
    kwargs_carregamento = {"local_files_only": True} if eh_local else {}

    import torch
    from datasets import Dataset
    from peft import LoraConfig
    from transformers import AutoModelForCausalLM, AutoTokenizer, set_seed
    from trl import SFTConfig, SFTTrainer

    if args.dispositivo == "cuda" and not torch.cuda.is_available():
        parser.error("CUDA não está disponível nesta instalação.")

    set_seed(44)

    tokenizer = AutoTokenizer.from_pretrained(caminho_base, revision=args.revisao, trust_remote_code=False, **kwargs_carregamento)
    dados, estatisticas = carregar_dados(args.dados, tokenizer, args.max_tokens)
    print(json.dumps(estatisticas, ensure_ascii=False, indent=2), flush=True)
    args.saida.mkdir(parents=True)
    inicio = monotonic()
    dtype = torch.bfloat16 if args.dispositivo == "cuda" and torch.cuda.is_bf16_supported() else torch.float32
    base = AutoModelForCausalLM.from_pretrained(
        caminho_base, revision=args.revisao, torch_dtype=dtype, low_cpu_mem_usage=True, trust_remote_code=False, **kwargs_carregamento
    )
    base.config.use_cache = False
    lora = LoraConfig(
        task_type="CAUSAL_LM", target_modules=["q_proj", "v_proj"], r=8, lora_alpha=16, lora_dropout=0.05,
    )
    config = SFTConfig(
        output_dir=str(args.saida),
        max_length=args.max_tokens,
        completion_only_loss=True,
        per_device_train_batch_size=1,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=4,
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        num_train_epochs=args.epocas,
        max_steps=args.max_passos,
        learning_rate=1e-4,
        use_cpu=args.dispositivo == "cpu",
        bf16=dtype == torch.bfloat16,
        eos_token="<|im_end|>",
        logging_steps=1,
        save_strategy="no",
        eval_strategy="no",
        seed=44,
        report_to="none",
    )
    conjuntos = {
        nome: Dataset.from_list([{"prompt": e["prompt"], "completion": e["completion"]} for e in linhas])
        for nome, linhas in dados.items()
    }
    trainer = SFTTrainer(
        model=base, args=config, peft_config=lora, processing_class=tokenizer,
        train_dataset=conjuntos["treino"], eval_dataset=conjuntos["validacao"],
    )
    trainer.model.print_trainable_parameters()
    treino = trainer.train()
    trainer.save_model()
    validacao = trainer.evaluate()
    arquivo = args.saida / "adapter_model.safetensors"
    resumo = {
        "requisito": "RNF0044", "criado_em": datetime.now(timezone.utc).isoformat(),
        "base": str(args.base), "revisao_base": args.revisao or getattr(base.config, "_commit_hash", None),
        "dispositivo": args.dispositivo, "max_tokens": args.max_tokens, "epocas_solicitadas": args.epocas,
        "passos": treino.global_step, "segundos": round(monotonic() - inicio, 2),
        "sha256_adaptador": hashlib.sha256(arquivo.read_bytes()).hexdigest(),
        "dados": estatisticas, "metricas_treino": treino.metrics, "metricas_validacao": validacao,
        "historico": trainer.state.log_history,
    }
    (args.saida / "treinamento.json").write_text(json.dumps(resumo, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(resumo, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
