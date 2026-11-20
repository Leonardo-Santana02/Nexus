param(
    [string]$Modelo = (Join-Path $PSScriptRoot 'artifacts\modelos\qwen2.5-1.5b-instruct-q4_k_m.gguf'),
    [string]$Adaptador,
    [switch]$Base,
    [string]$Servidor = (Join-Path $PSScriptRoot 'artifacts\runtime\llama-b12359\llama-server.exe'),
    [ValidateRange(1024, 65535)][int]$Porta = 8081,
    [ValidateRange(1024, 16384)][int]$Contexto = 4096,
    [switch]$SomenteConfigurar
)

$ErrorActionPreference = 'Stop'
if ($Base -and $Adaptador) {
    throw 'Escolha -Base ou -Adaptador.'
}
$piloto = Join-Path $PSScriptRoot 'artifacts\piloto\booknexus-lora.gguf'
if (-not $Base -and -not $Adaptador -and (Test-Path -LiteralPath $piloto -PathType Leaf)) {
    $Adaptador = $piloto
}
if (-not (Test-Path -LiteralPath $Modelo -PathType Leaf)) {
    throw "Modelo GGUF nao encontrado: $Modelo. Informe -Modelo com o caminho do arquivo baixado."
}
$modeloResolvido = (Resolve-Path -LiteralPath $Modelo).Path
$servidorResolvido = (Get-Command -Name $Servidor -CommandType Application -ErrorAction Stop).Source
$arquivoModelo = [IO.File]::OpenRead($modeloResolvido)
try {
    $cabecalho = New-Object byte[] 4
    if ($arquivoModelo.Read($cabecalho, 0, 4) -ne 4 -or [Text.Encoding]::ASCII.GetString($cabecalho) -ne 'GGUF') {
        throw 'O arquivo informado nao possui um cabecalho GGUF valido.'
    }
} finally {
    $arquivoModelo.Dispose()
}
$aliasModelo = 'booknexus-qwen-base'
$parametros = @(
    '--model', $modeloResolvido, '--host', '127.0.0.1', '--port', "$Porta",
    '--ctx-size', "$Contexto", '--parallel', '1', '--gpu-layers', '0', '--jinja'
)
if ($Adaptador) {
    $adaptadorResolvido = (Resolve-Path -LiteralPath $Adaptador).Path
    $hash = (Get-FileHash -LiteralPath $adaptadorResolvido -Algorithm SHA256).Hash.Substring(0, 12).ToLowerInvariant()
    $aliasModelo = "booknexus-qwen-lora-$hash"
    $parametros += @('--lora', $adaptadorResolvido)
}

$raizProjeto = Split-Path -Parent $PSScriptRoot
$configuracaoLocal = Join-Path $raizProjeto '.booknexus-local.properties'
$conteudo = if (Test-Path -LiteralPath $configuracaoLocal) { [IO.File]::ReadAllText($configuracaoLocal) } else { '' }
$endpoint = "http://127.0.0.1:$Porta/v1/chat/completions"
$propriedades = [ordered]@{
    'booknexus.local.chat-model' = $aliasModelo
    'booknexus.local.chat-endpoint' = $endpoint
}
foreach ($chave in $propriedades.Keys) {
    $padrao = '(?m)^' + [regex]::Escape($chave) + '[ \t]*[=:][^\r\n]*'
    $linha = $chave + '=' + $propriedades[$chave]
    if ([regex]::IsMatch($conteudo, $padrao)) {
        $conteudo = [regex]::Replace($conteudo, $padrao, $linha)
    } else {
        if ($conteudo.Length -gt 0 -and -not $conteudo.EndsWith("`n")) { $conteudo += "`r`n" }
        $conteudo += $linha + "`r`n"
    }
}
if (-not (Test-Path -LiteralPath $configuracaoLocal) -or [IO.File]::ReadAllText($configuracaoLocal) -cne $conteudo) {
    [IO.File]::WriteAllText($configuracaoLocal, $conteudo, [Text.UTF8Encoding]::new($false))
}
$env:BOOKNEXUS_CHAT_MODEL = $aliasModelo
$env:BOOKNEXUS_CHAT_ENDPOINT = $endpoint
Write-Host "Modelo: $aliasModelo"
Write-Host "API: $endpoint"
Write-Host 'Configuracao local salva. Reinicie o Spring Boot para carrega-la.'
if ($SomenteConfigurar) { return }
Write-Host 'Mantenha este terminal aberto enquanto utilizar o chatbot. Ctrl+C encerra o servidor.'
Push-Location $raizProjeto
try {
    & $servidorResolvido @parametros --alias $aliasModelo
    $codigoSaida = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $codigoSaida
