# Runtime simulado do VOID

O módulo `execution` entrega o primeiro ciclo **controlado** do agente: uma ferramenta local marcada como simulada pode ser executada somente depois de uma autorização específica e de uma decisão de política avaliada imediatamente antes do efeito.

Ele foi inspirado em fronteiras úteis observadas no Hermes, sem importar seu runtime Python: separação entre modelo, ferramentas e autorização; vínculo da aprovação aos argumentos; e auditoria de decisões. O VOID continua Kotlin/JVM e Android-first.

## O que existe

`GuardedSimulationRunner` aceita somente `SimulatedTool`. Essa marca é deliberada: uma implementação comum de `Tool` não pode entrar nesse runtime.

Antes de chamar a ferramenta, o runner confere:

1. autorização encontrada e ainda válida;
2. tarefa, usuário autenticado, ferramenta e ação esperada;
3. hashes SHA-256 canônicos dos argumentos e do destino;
4. permissões concedidas pela autorização persistida, nunca as permissões declaradas pelo cliente;
5. limites estruturais de entrada e destino antes da canonicalização (profundidade, tamanho, tipos e números finitos);
6. validação de entrada da ferramenta;
7. `PolicyGate` reavaliado para a chamada; e
8. consumo atômico de uso único da autorização.

A autorização é um objeto emitido por uma borda confiável. O núcleo não cria identidade nem autentica pessoas. Um armazenamento real deve implementar `consumeIfActive` de modo atômico e tratar revogação como ausência/inatividade da autorização.

## Limites explícitos

- Não há app Android, servidor, login, banco de dados, cron, gateway ou conector externo.
- Não há ferramenta de rede, conta, compra, mensagem, arquivo ou dispositivo neste runtime.
- Uma autorização válida não supera uma política negativa.
- O runner registra apenas IDs, status, contagens e hashes necessários. Argumentos e destino em texto puro não entram na auditoria.
- Cancelamento, efeito incerto, limites globais de custo/tempo e recuperação após reinício ainda são próximos marcos, não garantias atuais.

## Como integrar uma simulação

1. Implemente `SimulatedTool`, não apenas `Tool`.
2. Emita `ExecutionAuthorization` fora do cliente, após autenticação, com expiração curta e escopo mínimo.
3. Armazene-a em um `ExecutionAuthorizationStore` cujo consumo seja atômico.
4. Forneça um `PolicyGate` que mantenha o comportamento deny-by-default para qualquer ação não liberada explicitamente.
5. Execute os testes do núcleo antes de conectar uma interface.

Não conecte uma conta real a este módulo. A próxima etapa deve ampliar os estados de execução e a recuperação do fluxo simulado antes de qualquer integração externa.
