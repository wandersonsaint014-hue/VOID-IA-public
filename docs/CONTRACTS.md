# Contratos do núcleo

## Estado e limites

Os schemas JSON e os modelos Kotlin definem o formato de dados públicos do núcleo. Eles **não** disponibilizam API, servidor, aplicativo Android, autorização ou execução de ferramentas. O arquivo `Openapi.Yaml` é uma referência futura, não uma lista de endpoints em operação.

O agente `void-planner` produz somente propostas revisáveis. O `PolicyGate` v1 avalia pedidos e nega todos por padrão; uma decisão de política não é uma instrução para executar. Nenhum contrato atual concede autoridade com base em `user_id`, `caller` ou `permissions` fornecidos pelo cliente.

## Serialização e validação

`VoidObjectMapper` usa propriedades `snake_case`, enums em minúsculas, instantes ISO-8601 e omite campos opcionais nulos. Na desserialização, campos desconhecidos são rejeitados.

Os schemas usam Draft-07 e IDs estáveis em `https://void.invalid/schemas/*.json`. A verificação no CI resolve referências localmente, sem rede. Kotlin e schemas rejeitam identificadores e textos obrigatórios vazios; `Plan.steps` não pode ser vazio; `Step` também rejeita tentativas negativas e timeout não positivo. O runtime simulado também limita profundidade, mapas, coleções, strings e tipos aceitos antes de gerar hashes ou chamar a ferramenta. Limites de tentativas, timeout, orçamento e concorrência continuam sendo responsabilidade do runtime persistente futuro.

## Propostas de ação, capacidades e aprovação

Além de planos, o núcleo modela uma `ActionProposal`: uma proposta curta para **uma** ação potencial, com resumo humano, capacidades permitidas e hashes SHA-256 de argumentos e destino. O texto bruto dos argumentos e do destino não é colocado no contrato nem na auditoria só para formar esse vínculo.

`ActionProposalService` só aceita um `VerifiedPrincipal` fornecido pela borda confiável. Ele ignora `ToolCall.caller`, resolve cada capacidade em um `CapabilityRegistry`, aplica `CapabilityPolicy`, limita dados de entrada e persiste uma proposta `awaiting_review`. O serviço não recebe uma `Tool`, portanto não pode executar efeitos.

A aprovação reavalia a política e chama `ActionReviewStore.approveAndIssue`. Essa operação deve, em uma implementação real, marcar a proposta como aprovada e persistir a `ExecutionAuthorization` numa única transação. A autorização resultante fica vinculada a usuário autenticado, tarefa, ferramenta, ação, argumentos, destino, permissões internas mínimas, versão da política, emissão e expiração.

A interface e os schemas existem no núcleo; ainda não há autenticador, banco Android/servidor, revogação persistida nem emissor de produção. Aprovar não executa nada e não torna uma ação permitida por si só. Antes de qualquer efeito, o runtime ainda deve validar o vínculo, expiração e consumo atômico, e avaliar novamente o `PolicyGate`. Uma política que nega continua negando mesmo se houver uma autorização apresentada.

## Compatibilidade da API futura

`Openapi.Yaml` preserva a forma pretendida da API, mas seus caminhos são reservados. Em especial, `/api/v1/toolcalls` não executa ferramentas: qualquer implementação futura deverá encaminhar pedidos ao `PolicyGate`, exigir identidade autenticada e aprovação vinculada a ação, argumentos, destino e expiração antes de chamar um runner.

## Fora de escopo

Este repositório implementa somente um runner para `SimulatedTool`, sem conectores ou efeitos externos. Ele exige uma autorização emitida por uma borda confiável, vinculada a usuário, tarefa, ferramenta, ação, argumentos, destino, expiração e uso único, além de uma reavaliação do `PolicyGate`. Não implementa servidor, autenticação, emissão real de autorizações, persistência Android nem UI Android. Conteúdo externo deve ser tratado como não confiável, e auditorias precisam redigir segredos e dados pessoais antes de persistir. Consulte [o runtime simulado](SIMULATION_RUNTIME.md) e a [revisão de segurança](SECURITY_REVIEW.md).

## Estados e transições

O ciclo atual modela revisão de proposta, não execução. Os estados são serializados em minúsculas:

- `Task`: `planning` → `pending_review` → `approved` | `rejected` | `cancelled`; uma tarefa em `planning` também pode ser `cancelled`.
- `Plan` e `Step`: `pending_review` → `approved` | `rejected` | `cancelled`.

`approved`, `rejected` e `cancelled` são terminais na versão atual: não há retorno implícito, reabertura nem transição automática. Qualquer mudança posterior exigirá uma nova proposta com identificador próprio e auditoria. `approved` indica somente que a proposta foi resolvida para revisão; não é autorização para executar nem substitui `PolicyGate`, identidade autenticada ou a aprovação verificável futura.
