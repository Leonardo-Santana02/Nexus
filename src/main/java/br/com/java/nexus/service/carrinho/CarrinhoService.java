package br.com.java.nexus.service.carrinho;

import br.com.java.nexus.model.carrinho.Carrinho;
import br.com.java.nexus.model.carrinho.ItemCarrinho;
import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.produto.Produto;

import br.com.java.nexus.repository.carrinho.CarrinhoRepository;
import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.repository.cliente.EnderecoRepository;
import br.com.java.nexus.repository.produto.ProdutoRepository;

import br.com.java.nexus.service.exception.ClienteNaoEncontradoException;
import br.com.java.nexus.service.exception.ValidacaoException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
public class CarrinhoService {
    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final EnderecoRepository enderecoRepository;

    public CarrinhoService(
            CarrinhoRepository carrinhoRepository,
            ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository,
            EnderecoRepository enderecoRepository) {

        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.enderecoRepository = enderecoRepository;
    }

    @Transactional
    public Carrinho obterCarrinhoDoCliente(Long clienteId) {
        validarClienteId(clienteId);

        return carrinhoRepository.findByClienteId(clienteId)
                .orElseGet(() -> criarCarrinho(clienteId));
    }

    @Transactional(readOnly = true)
    public Carrinho buscarComItens(Long clienteId) {
        validarClienteId(clienteId);

        return carrinhoRepository.buscarComItensPorClienteId(clienteId)
                .orElse(null);
    }

    @Transactional
    public Carrinho adicionarItem(
            Long clienteId,
            Long produtoId,
            Integer quantidade) {

        validarQuantidade(quantidade);

        Produto produto = buscarProdutoDisponivel(produtoId);
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        int quantidadeAtual = carrinho.buscarItemPorProduto(produtoId).map(ItemCarrinho::getQuantidade).orElse(0);
        int quantidadeFinal = quantidadeAtual + quantidade;

        validarEstoque(produto, quantidadeFinal);
        carrinho.adicionarItem(produto, quantidade);
        return carrinhoRepository.save(carrinho);
    }

    @Transactional
    public Carrinho alterarQuantidadeItem(
            Long clienteId,
            Long produtoId,
            Integer novaQuantidade) {

        validarQuantidade(novaQuantidade);

        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        ItemCarrinho item = carrinho.buscarItemPorProduto(produtoId)
                        .orElseThrow(() -> new ValidacaoException("Item não encontrado no carrinho."));

        Produto produto = buscarProdutoDisponivel(item.getProduto().getId());

        validarEstoque(produto, novaQuantidade);
        carrinho.alterarQuantidadeItem(produtoId, novaQuantidade);
        return carrinhoRepository.save(carrinho);
    }

    @Transactional
    public Carrinho removerItem(Long clienteId, Long produtoId) {

        if (produtoId == null) {
            throw new ValidacaoException("Produto é obrigatório.");
        }

        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        if (carrinho.buscarItemPorProduto(produtoId).isEmpty()) {
            throw new ValidacaoException("Item não encontrado no carrinho.");
        }

        carrinho.removerItem(produtoId);
        return carrinhoRepository.save(carrinho);
    }

    @Transactional
    public void limparCarrinho(Long clienteId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);
        carrinho.limpar();
        carrinhoRepository.save(carrinho);
    }


    @Transactional
    public Endereco cadastrarEnderecoEntrega(Long clienteId, Endereco endereco) {
        validarClienteId(clienteId);

        if (endereco == null) {
            throw new ValidacaoException("Os dados do endereço são obrigatórios.");
        }

        Cliente cliente = buscarCliente(clienteId);

        endereco.setId(null);
        prepararEndereco(endereco);
        validarEnderecoEntrega(endereco);

        endereco.setEnderecoEntrega(true);
        endereco.setEnderecoCobranca(false);
        endereco.setCliente(cliente);

        return enderecoRepository.save(endereco);
    }

    @Transactional(readOnly = true)
    public Integer getQuantidadeItens(Long clienteId) {
        Carrinho carrinho = carrinhoRepository
                        .buscarComItensPorClienteId(clienteId)
                        .orElse(null);

        if (carrinho == null) {
            return 0;
        }

        return carrinho.getQuantidadeItens();
    }

    @Transactional(readOnly = true)
    public BigDecimal getSubtotal(Long clienteId) {

        Carrinho carrinho = carrinhoRepository
                        .buscarComItensPorClienteId(clienteId)
                        .orElse(null);

        if (carrinho == null) {
            return BigDecimal.ZERO;
        }

        return carrinho.getSubtotal();
    }

    @Transactional(readOnly = true)
    public Carrinho validarParaCheckout(Long clienteId) {
        Carrinho carrinho = carrinhoRepository
                        .buscarComItensPorClienteId(clienteId)
                        .orElseThrow(() -> new ValidacaoException("O carrinho está vazio."));

        if (carrinho.isVazio()) {
            throw new ValidacaoException("O carrinho está vazio.");
        }

        for (ItemCarrinho item : carrinho.getItens()) {
            Produto produto = item.getProduto();

            if (produto == null || !produto.isAtivo()) {
                throw new ValidacaoException("Existe um produto indisponível no carrinho.");
            }
            validarEstoque(produto, item.getQuantidade());
        }

        return carrinho;
    }

    private Carrinho criarCarrinho(Long clienteId) {
        Cliente cliente = buscarCliente(clienteId);
        Carrinho carrinho = new Carrinho();

        carrinho.setCliente(cliente);

        return carrinhoRepository.save(carrinho);
    }

    private Cliente buscarCliente(Long clienteId) {
        validarClienteId(clienteId);

        return clienteRepository
                .findById(clienteId)
                .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
    }

    private Produto buscarProdutoDisponivel(Long produtoId) {
        if (produtoId == null) {
            throw new ValidacaoException("Produto é obrigatório.");
        }

        Produto produto = produtoRepository
                        .findById(produtoId)
                        .orElseThrow(() -> new ValidacaoException("Produto não encontrado."));

        if (!produto.isAtivo()) {
            throw new ValidacaoException("O produto está indisponível para compra.");
        }

        if (produto.getEstoque() == null || produto.getEstoque() <= 0) {
            throw new ValidacaoException("O produto está sem estoque.");
        }

        return produto;
    }


    private void prepararEndereco(Endereco endereco) {
        endereco.setLogradouro(limparTexto(endereco.getLogradouro()));
        endereco.setNumero(limparTexto(endereco.getNumero()));
        endereco.setComplemento(limparTexto(endereco.getComplemento()));
        endereco.setBairro(limparTexto(endereco.getBairro()));
        endereco.setCidade(limparTexto(endereco.getCidade()));
        endereco.setCep(limparTexto(endereco.getCep()));
        endereco.setPais(limparTexto(endereco.getPais()));
        endereco.setObservacoes(limparTexto(endereco.getObservacoes()));
        endereco.setApelido(limparTexto(endereco.getApelido()));
    }

    private void validarEnderecoEntrega(Endereco endereco) {
        if (endereco.getTipoLogradouro() == null) {
            throw new ValidacaoException("Tipo de logradouro é obrigatório.");
        }

        if (!notBlank(endereco.getLogradouro())) {
            throw new ValidacaoException("Logradouro é obrigatório.");
        }

        if (!notBlank(endereco.getNumero())) {
            throw new ValidacaoException("Número é obrigatório.");
        }

        if (!notBlank(endereco.getBairro())) {
            throw new ValidacaoException("Bairro é obrigatório.");
        }

        if (!notBlank(endereco.getCep())) {
            throw new ValidacaoException("CEP é obrigatório.");
        }

        if (!notBlank(endereco.getCidade())) {
            throw new ValidacaoException("Cidade é obrigatória.");
        }

        if (endereco.getUf() == null) {
            throw new ValidacaoException("UF é obrigatória.");
        }

        if (!notBlank(endereco.getPais())) {
            throw new ValidacaoException("País é obrigatório.");
        }
    }

    private void validarEstoque(Produto produto, Integer quantidadeDesejada) {
        if (!produto.temEstoque(quantidadeDesejada)) {
            throw new ValidacaoException("Quantidade indisponível para o livro "
                            + produto.getTitulo()
                            + ". Estoque disponível: "
                            + produto.getEstoque()
                            + ".");
        }
    }

    private void validarQuantidade(Integer quantidade) {
        if (quantidade == null || quantidade <= 0) {
            throw new ValidacaoException("A quantidade deve ser maior que zero.");
        }
    }

    private void validarClienteId(Long clienteId) {
        if (clienteId == null) {
            throw new ValidacaoException("Cliente é obrigatório.");
        }
    }

    private String limparTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private boolean notBlank(String valor) {
        return valor != null && !valor.isBlank();
    }
}