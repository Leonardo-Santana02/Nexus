-- Execute o arquivo completo no Query Tool do banco local booknexus.
-- Mantém os IDs, compras e cadastros dos dois perfis de apresentação.
-- Uma cópia de todas as tabelas públicas é guardada em outro schema antes da limpeza.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SELECT pg_advisory_xact_lock(440044);

DO $backup$
DECLARE
    destino text := 'backup_apresentacao_' || to_char(clock_timestamp(), 'YYYYMMDD_HH24MISS_US');
    tabela record;
BEGIN
    IF current_database() <> 'booknexus' THEN
        RAISE EXCEPTION 'Execute somente no banco local booknexus.';
    END IF;
    IF (SELECT count(*) FROM public.clientes WHERE email IN
        ('rn0044-01@booknexus.example', 'rn0044-06@booknexus.example')) <> 2 THEN
        RAISE EXCEPTION 'Os dois perfis de apresentação precisam existir antes da limpeza.';
    END IF;
    FOR tabela IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename LOOP
        EXECUTE format('LOCK TABLE public.%I IN SHARE ROW EXCLUSIVE MODE', tabela.tablename);
    END LOOP;
    EXECUTE format('CREATE SCHEMA %I', destino);
    FOR tabela IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename LOOP
        EXECUTE format('CREATE TABLE %I.%I AS TABLE public.%I', destino, tabela.tablename, tabela.tablename);
    END LOOP;
    PERFORM set_config('booknexus.backup_apresentacao', destino, false);
END $backup$;

CREATE TEMP TABLE manter_apresentacao ON COMMIT DROP AS
SELECT id, CASE email WHEN 'rn0044-01@booknexus.example' THEN 'Lucas (Fantasia)'
                     ELSE 'Maria (Mistério)' END AS nome,
           CASE email WHEN 'rn0044-01@booknexus.example' THEN 'Fantasia'
                     ELSE 'Mistério' END AS favorito
FROM public.clientes WHERE email IN ('rn0044-01@booknexus.example', 'rn0044-06@booknexus.example');
CREATE TEMP TABLE remover_clientes ON COMMIT DROP AS
SELECT id FROM public.clientes WHERE id NOT IN (SELECT id FROM manter_apresentacao);
CREATE TEMP TABLE remover_pedidos ON COMMIT DROP AS
SELECT id, pagamento_id FROM public.pedidos WHERE cliente_id IN (SELECT id FROM remover_clientes);
CREATE TEMP TABLE remover_devolucoes ON COMMIT DROP AS
SELECT id FROM public.devolucoes WHERE cliente_id IN (SELECT id FROM remover_clientes)
    OR pedido_id IN (SELECT id FROM remover_pedidos);

DELETE FROM public.itens_devolucao WHERE devolucao_id IN (SELECT id FROM remover_devolucoes)
    OR item_pedido_id IN (SELECT id FROM public.itens_pedido WHERE pedido_id IN (SELECT id FROM remover_pedidos));
DELETE FROM public.devolucoes WHERE id IN (SELECT id FROM remover_devolucoes);
DELETE FROM public.itens_carrinho WHERE carrinho_id IN
    (SELECT id FROM public.carrinhos WHERE cliente_id IN (SELECT id FROM remover_clientes));
DELETE FROM public.carrinhos WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.pagamento_cartoes WHERE pagamento_id IN (SELECT pagamento_id FROM remover_pedidos)
    OR cartao_id IN (SELECT id FROM public.cartoes_credito WHERE cliente_id IN (SELECT id FROM remover_clientes));
DELETE FROM public.itens_pedido WHERE pedido_id IN (SELECT id FROM remover_pedidos);
DELETE FROM public.pedidos WHERE id IN (SELECT id FROM remover_pedidos);
DELETE FROM public.pagamentos WHERE id IN (SELECT pagamento_id FROM remover_pedidos)
    AND NOT EXISTS (SELECT 1 FROM public.pedidos p WHERE p.pagamento_id = pagamentos.id);
DELETE FROM public.cupons WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.cartoes_credito WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.enderecos WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.telefones WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.chat_respostas WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.chat_preferencias WHERE cliente_id IN (SELECT id FROM remover_clientes);
DELETE FROM public.clientes WHERE id IN (SELECT id FROM remover_clientes);

UPDATE public.clientes c SET nome = m.nome, inativado = false
FROM manter_apresentacao m WHERE c.id = m.id;
INSERT INTO public.chat_preferencias (cliente_id, favoritos, evitar)
SELECT id, favorito, '' FROM manter_apresentacao
ON CONFLICT (cliente_id) DO UPDATE SET favoritos = excluded.favoritos, evitar = excluded.evitar;

DO $validar$
BEGIN
    IF (SELECT count(*) FROM public.clientes) <> 2
        OR EXISTS (SELECT 1 FROM public.chat_preferencias p LEFT JOIN public.clientes c ON c.id = p.cliente_id WHERE c.id IS NULL)
        OR EXISTS (SELECT 1 FROM public.chat_respostas r LEFT JOIN public.clientes c ON c.id = r.cliente_id WHERE c.id IS NULL) THEN
        RAISE EXCEPTION 'A validação da limpeza falhou. Nenhuma alteração será confirmada.';
    END IF;
END $validar$;
COMMIT;

SELECT c.id, c.nome, p.favoritos,
    (SELECT count(*) FROM public.pedidos pe WHERE pe.cliente_id = c.id) AS compras_preservadas,
    current_setting('booknexus.backup_apresentacao') AS copia_de_seguranca
FROM public.clientes c JOIN public.chat_preferencias p ON p.cliente_id = c.id ORDER BY c.id;
