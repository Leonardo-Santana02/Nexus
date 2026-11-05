package br.com.java.nexus.service.cliente;

import br.com.java.nexus.model.cliente.CartaoCredito;
import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.cliente.Telefone;
import br.com.java.nexus.model.cliente.enums.Genero;
import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.service.exception.ClienteNaoEncontradoException;
import br.com.java.nexus.service.exception.CpfJaCadastradoException;
import br.com.java.nexus.service.exception.EmailJaCadastradoException;
import br.com.java.nexus.service.exception.ValidacaoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final CartaoCreditoService cartaoCreditoService;

    public ClienteService(ClienteRepository clienteRepository, CartaoCreditoService cartaoCreditoService) {
        this.clienteRepository = clienteRepository;
        this.cartaoCreditoService = cartaoCreditoService;
    }

    /*
     * ==========================================================
     * CONSULTAS BÁSICAS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarAtivos() {
        return clienteRepository.findByInativadoFalseOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarInativos() {
        return clienteRepository.findByInativadoTrueOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        if (id == null) {
            throw new ValidacaoException("ID do cliente é obrigatório.");
        }
        return clienteRepository.findById(id).orElseThrow(() -> new ClienteNaoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public Cliente buscarDetalhes(Long id) {
        Cliente cliente = buscarPorId(id);
        //Inicializa os relacionamentos enquanto a transação ainda está aberta.
        cliente.getEnderecos().size();
        cliente.getTelefones().size();
        cliente.getCartoesCredito().size();
        return cliente;
    }


    /*
     * ==========================================================
     * PESQUISA COM FILTROS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public List<Cliente> pesquisar(
            Long id,
            String nome,
            String email,
            String cpf,
            Genero genero,
            LocalDate dataNascimento,
            String telefone,
            Boolean inativado) {

        //Formata os campos para o formato aceitado na query
        nome = limparTextoPesquisaIgnoreCase(nome);
        email = limparTextoPesquisaIgnoreCase(email);
        cpf = limparNumeroPesquisa(cpf);
        telefone = limparNumeroPesquisa(telefone);

        return clienteRepository.pesquisar(
                id,
                nome,
                email,
                cpf,
                genero,
                dataNascimento,
                telefone,
                inativado
        );
    }


    /*
     * ==========================================================
     * CADASTRO
     * ==========================================================
     */

    @Transactional
    public Cliente cadastrar(Cliente cliente) {
        if (cliente == null) {
            throw new ValidacaoException("Cliente não pode ser nulo.");
        }

        //Normaliza e valida os dados antes das regras de negócio e antes de salvar.
        prepararCliente(cliente);
        validarCliente(cliente);

        if (clienteRepository.existsByCpf(cliente.getCpf())) {
            throw new CpfJaCadastradoException(cliente.getCpf());}
        if (clienteRepository.existsByEmailIgnoreCase(cliente.getEmail())) {
            throw new EmailJaCadastradoException(cliente.getEmail());
        }

        cliente.setId(null);
        cliente.setInativado(false);

        if (cliente.getRanking() == null) {
            cliente.setRanking(0);
        }
        return clienteRepository.save(cliente);
    }


    /*
     * ==========================================================
     * EDIÇÃO
     * ==========================================================
     */

    @Transactional
    public Cliente atualizar(Long id, Cliente dadosAtualizados) {
        if (id == null) {
            throw new ValidacaoException("ID do cliente é obrigatório.");
        }

        if (dadosAtualizados == null) {
            throw new ValidacaoException("Dados do cliente são obrigatórios.");
        }

        //Puxa as informações do cliente por ID
        Cliente clienteExistente = buscarPorId(id);
        dadosAtualizados.setId(id);
        prepararCliente(dadosAtualizados);
        validarCliente(dadosAtualizados);

        if (clienteRepository.existsByCpfAndIdNot(dadosAtualizados.getCpf(), id)) {
            throw new CpfJaCadastradoException(dadosAtualizados.getCpf());
        }
        if (clienteRepository.existsByEmailIgnoreCaseAndIdNot(dadosAtualizados.getEmail(), id)) {
            throw new EmailJaCadastradoException(dadosAtualizados.getEmail());
        }

        atualizarDadosBasicos(clienteExistente, dadosAtualizados);
        atualizarEnderecos(clienteExistente, dadosAtualizados);
        atualizarTelefones(clienteExistente, dadosAtualizados);
        atualizarCartoes(clienteExistente, dadosAtualizados);

        validarEnderecos(clienteExistente);
        validarTelefone(clienteExistente);
        validarCartoes(clienteExistente);

        return clienteRepository.save(clienteExistente);
    }

    /*
     * ==========================================================
     * INATIVAÇÃO / REATIVAÇÃO
     * ==========================================================
     */

    @Transactional
    public void inativar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.inativar();
        clienteRepository.save(cliente);
    }

    @Transactional
    public void reativar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.ativar();
        clienteRepository.save(cliente);
    }


    /*
     * ==========================================================
     * VALIDAÇÕES
     * ==========================================================
     */

    private void validarCliente(Cliente cliente) {

        if (cliente == null) {
            throw new ValidacaoException("Cliente não pode ser nulo.");
        }

        if (!notBlank(cliente.getNome())) {
            throw new ValidacaoException("Nome do cliente é obrigatório.");
        }

        if (!notBlank(cliente.getCpf())) {
            throw new ValidacaoException("CPF é obrigatório.");
        }

        if (!validarCpf(cliente.getCpf())) {
            throw new ValidacaoException("CPF inválido.");
        }

        if (!notBlank(cliente.getEmail())) {
            throw new ValidacaoException("E-mail é obrigatório.");
        }

        if (!cliente.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new ValidacaoException("E-mail inválido.");
        }

        if (cliente.getGenero() == null) {
            throw new ValidacaoException("Gênero é obrigatório.");
        }

        if (cliente.getDataNascimento() == null) {
            throw new ValidacaoException("Data de nascimento é obrigatória.");
        }

        if (!cliente.getDataNascimento().isBefore(LocalDate.now())) {
            throw new ValidacaoException("Data de nascimento deve ser anterior à data atual.");
        }

        validarTelefone(cliente);
        validarEnderecos(cliente);
        validarCartoes(cliente);
    }


    private void validarTelefone(Cliente cliente) {
        if (cliente.getTelefones() == null || cliente.getTelefones().isEmpty()) {
            throw new ValidacaoException("Cliente deve possuir pelo menos um telefone.");
        }

        boolean existeTelefoneValido = cliente.getTelefones().stream().anyMatch(this::telefoneValido);

        if (!existeTelefoneValido) {
            throw new ValidacaoException("Cliente deve possuir pelo menos um telefone válido."
            );
        }
    }


    private void validarEnderecos(Cliente cliente) {
        if (cliente.getEnderecos() == null || cliente.getEnderecos().isEmpty()) {
            throw new ValidacaoException("Cliente deve possuir endereços cadastrados.");
        }

        boolean possuiEntrega = cliente.getEnderecos().stream().filter(this::enderecoValido)
                        .anyMatch(Endereco::isEnderecoEntrega);

        boolean possuiCobranca = cliente.getEnderecos().stream().filter(this::enderecoValido)
                        .anyMatch(Endereco::isEnderecoCobranca);

        if (!possuiEntrega) {
            throw new ValidacaoException("Cliente deve possuir pelo menos um endereço de entrega.");
        }

        if (!possuiCobranca) {
            throw new ValidacaoException("Cliente deve possuir pelo menos um endereço de cobrança.");
        }
    }


    private void validarCartoes(Cliente cliente) {
        if (cliente.getCartoesCredito() == null || cliente.getCartoesCredito().isEmpty()) {
            throw new ValidacaoException("Cliente deve possuir pelo menos um cartão de crédito.");
        }

        for (CartaoCredito cartao : cliente.getCartoesCredito()) {
            if (cartao == null) {
                throw new ValidacaoException("Cartão de crédito inválido.");
            }
            cartaoCreditoService.validarCartao(cartao
            );
        }

        long quantidadePreferenciais = cliente.getCartoesCredito().stream()
                .filter(CartaoCredito::isPreferencial).count();

        if (quantidadePreferenciais != 1) {
            throw new ValidacaoException("Cliente deve possuir exatamente um cartão preferencial.");
        }
    }


    /*
     * ==========================================================
     * PREPARAÇÃO DOS DADOS
     * ==========================================================
     */

    private void prepararCliente(Cliente cliente) {
        cliente.setNome(limparTexto(cliente.getNome()));
        cliente.setCpf(somenteNumeros(cliente.getCpf()));
        cliente.setEmail(limparTexto(cliente.getEmail()));

        prepararEnderecos(cliente);
        prepararTelefones(cliente);
        prepararCartoes(cliente);
    }

    private void prepararEnderecos(Cliente cliente) {
        if (cliente.getEnderecos() == null) {
            return;
        }

        for (Endereco endereco : cliente.getEnderecos()) {
            if (endereco == null) {
                continue;
            }

            endereco.setLogradouro(limparTexto(endereco.getLogradouro()));
            endereco.setNumero(limparTexto(endereco.getNumero()));
            endereco.setComplemento(limparTexto(endereco.getComplemento()));
            endereco.setBairro(limparTexto(endereco.getBairro()));
            endereco.setCidade(limparTexto(endereco.getCidade()));
            endereco.setCep(limparTexto(endereco.getCep()));
            endereco.setPais(limparTexto(endereco.getPais()));
            endereco.setObservacoes(limparTexto(endereco.getObservacoes()));
            endereco.setApelido(limparTexto(endereco.getApelido()));

            endereco.setCliente(cliente);
        }
    }

    private void prepararTelefones(Cliente cliente) {
        if (cliente.getTelefones() == null) {
            return;
        }

        for (Telefone telefone : cliente.getTelefones()) {
            if (telefone == null) {
                continue;
            }

            telefone.setDdd(somenteNumeros(telefone.getDdd()));
            telefone.setNumero(somenteNumeros(telefone.getNumero()));

            telefone.setCliente(cliente);
        }
    }

    private void prepararCartoes(Cliente cliente) {
        if (cliente.getCartoesCredito() == null) {
            return;
        }

        for (CartaoCredito cartao : cliente.getCartoesCredito()) {
            if (cartao == null) {
                continue;
            }

            cartao.setNumeroCartao(somenteNumeros(cartao.getNumeroCartao()));
            cartao.setCodigoSeguranca(somenteNumeros(cartao.getCodigoSeguranca()));
            cartao.setNomeImpresso(limparTexto(cartao.getNomeImpresso()));

            cartao.setCliente(cliente);
        }
    }


    /*
     * ==========================================================
     * ATUALIZAÇÃO DOS DADOS
     * ==========================================================
     */

    private void atualizarDadosBasicos(Cliente atual, Cliente novo) {
        atual.setNome(novo.getNome());
        atual.setCpf(novo.getCpf());
        atual.setEmail(novo.getEmail());
        atual.setGenero(novo.getGenero());
        atual.setDataNascimento(novo.getDataNascimento());

        if (Boolean.TRUE.equals(novo.getInativado())) {
            atual.inativar();
        } else {
            atual.ativar();
        }
    }

    /*
     * Na edição, os IDs existentes são preservados.
     *
     * Itens removidos do formulário são excluídos
     * por orphanRemoval.
     *
     * Itens sem ID são considerados novos.
     */

    private void atualizarEnderecos(Cliente atual, Cliente novo) {
        Set<Long> idsRecebidos = new HashSet<>();
        for (Endereco endereco : novo.getEnderecos()) {
            if (endereco != null && endereco.getId() != null) {
                idsRecebidos.add(endereco.getId()
                );
            }
        }

        atual.getEnderecos().removeIf(endereco ->
                endereco.getId() != null && !idsRecebidos.contains(endereco.getId()));

        for (Endereco recebido : novo.getEnderecos()) {
            if (recebido == null) {
                continue;
            }

            if (recebido.getId() == null) {
                recebido.setCliente(atual);
                atual.getEnderecos().add(recebido);
                continue;
            }

            Endereco existente = atual.getEnderecos().stream()
                            .filter(endereco -> recebido.getId().equals(endereco.getId()))
                            .findFirst().orElseThrow(() -> new ValidacaoException("Endereço informado não pertence ao cliente."));

            copiarEndereco(existente, recebido);
        }
    }


    private void atualizarTelefones(Cliente atual, Cliente novo) {
        Set<Long> idsRecebidos = new HashSet<>();

        for (Telefone telefone : novo.getTelefones()) {
            if (telefone != null && telefone.getId() != null) {
                idsRecebidos.add(telefone.getId());
            }
        }

        atual.getTelefones().removeIf(telefone ->
                telefone.getId() != null && !idsRecebidos.contains(telefone.getId()));

        for (Telefone recebido : novo.getTelefones()) {
            if (recebido == null) {
                continue;
            }

            if (recebido.getId() == null) {
                recebido.setCliente(atual);
                atual.getTelefones().add(recebido);
                continue;
            }

            Telefone existente = atual.getTelefones().stream()
                            .filter(telefone -> recebido.getId().equals(telefone.getId()))
                            .findFirst().orElseThrow(() -> new ValidacaoException("Telefone informado não pertence ao cliente."));

            copiarTelefone(existente, recebido);
        }
    }

    private void atualizarCartoes(Cliente atual, Cliente novo) {
        Set<Long> idsRecebidos = new HashSet<>();

        for (CartaoCredito cartao : novo.getCartoesCredito()) {
            if (cartao != null && cartao.getId() != null) {
                idsRecebidos.add(cartao.getId());
            }
        }

        atual.getCartoesCredito().removeIf(cartao ->
                cartao.getId() != null && !idsRecebidos.contains(cartao.getId())
        );

        for (CartaoCredito recebido : novo.getCartoesCredito()) {
            if (recebido == null) {
                continue;
            }

            if (recebido.getId() == null) {
                recebido.setCliente(atual);
                atual.getCartoesCredito().add(recebido);
                continue;
            }

            CartaoCredito existente = atual.getCartoesCredito().stream()
                    .filter(cartao -> recebido.getId().equals(cartao.getId()))
                            .findFirst().orElseThrow(() -> new ValidacaoException("Cartão informado não pertence ao cliente."));

            copiarCartao(existente, recebido);
        }
    }

    private void copiarEndereco(Endereco destino, Endereco origem) {

        destino.setTipoLogradouro(origem.getTipoLogradouro());
        destino.setLogradouro(origem.getLogradouro());
        destino.setNumero(origem.getNumero());
        destino.setComplemento(origem.getComplemento());
        destino.setBairro(origem.getBairro());
        destino.setCidade(origem.getCidade());
        destino.setUf(origem.getUf());
        destino.setCep(origem.getCep());
        destino.setPais(origem.getPais());
        destino.setObservacoes(origem.getObservacoes());
        destino.setApelido(origem.getApelido());
        destino.setEnderecoEntrega(origem.isEnderecoEntrega());
        destino.setEnderecoCobranca(origem.isEnderecoCobranca());
    }

    private void copiarTelefone(Telefone destino, Telefone origem) {
        destino.setTipo(origem.getTipo());
        destino.setDdd(origem.getDdd());
        destino.setNumero(origem.getNumero());
    }

    private void copiarCartao(CartaoCredito destino, CartaoCredito origem) {
        destino.setNumeroCartao(origem.getNumeroCartao());
        destino.setNomeImpresso(origem.getNomeImpresso());
        destino.setBandeira(origem.getBandeira());
        destino.setCodigoSeguranca(origem.getCodigoSeguranca());
        destino.setPreferencial(origem.isPreferencial());
    }

    /*
     * ==========================================================
     * MÉTODOS AUXILIARES DE VALIDAÇÃO
     * ==========================================================
     */

    private boolean enderecoValido(Endereco endereco) {
        if (endereco == null) {
            return false;
        }

        return endereco.getTipoLogradouro() != null
                && notBlank(endereco.getLogradouro())
                && notBlank(endereco.getNumero())
                && notBlank(endereco.getBairro())
                && notBlank(endereco.getCep())
                && notBlank(endereco.getCidade())
                && endereco.getUf() != null
                && notBlank(endereco.getPais());
    }

    private boolean telefoneValido(Telefone telefone) {
        if (telefone == null) {
            return false;
        }

        String ddd = somenteNumeros(telefone.getDdd());
        String numero = somenteNumeros(telefone.getNumero());

        return telefone.getTipo() != null && ddd.length() == 2 && (numero.length() == 8 || numero.length() == 9);
    }

    private boolean validarCpf(String cpf) {
        String numero = somenteNumeros(cpf);

        if (numero.length() != 11) {
            return false;
        }

        if (numero.matches("(\\d)\\1{10}")) {
            return false;
        }

        int soma = 0;
        for (int i = 0; i < 9; i++) {
            soma += Character.getNumericValue(numero.charAt(i)) * (10 - i);
        }

        int resto = 11 - (soma % 11);
        int primeiroDigito = resto >= 10 ? 0 : resto;

        if (primeiroDigito != Character.getNumericValue(numero.charAt(9))) {
            return false;
        }

        soma = 0;
        for (int i = 0; i < 10; i++) {
            soma += Character.getNumericValue(numero.charAt(i)) * (11 - i);
        }

        resto = 11 - (soma % 11);
        int segundoDigito = resto >= 10 ? 0 : resto;

        return segundoDigito == Character.getNumericValue(numero.charAt(10));
    }


    /*
     * ==========================================================
     * MÉTODOS AUXILIARES
     * ==========================================================
     */

    private String limparTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String limparTextoPesquisaIgnoreCase(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return valor.trim().toLowerCase();
    }

    private String limparNumeroPesquisa(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }

        String numero = somenteNumeros(valor);

        return numero.isBlank() ? "" : numero;
    }

    private String somenteNumeros(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("\\D", "");
    }

    private boolean notBlank(String valor) {
        return valor != null && !valor.isBlank();
    }
}