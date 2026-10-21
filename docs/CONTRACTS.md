# Contratos do núcleo

## Estado e limites

Os schemas JSON e os modelos Kotlin definem o formato de dados públicos do núcleo. Eles **não** disponibilizam API, servidor, aplicativo Android, autorização ou execução de ferramentas. O arquivo `Openapi.Yaml` é uma referência futura, não uma lista de endpoints em operação.

O agente `void-planner` produz somente propostas revisáveis. O `PolicyGate` v1 avalia pedidos e nega todos por padrão; uma decisão de política não é uma instrução para executar. Nenhum contrato atual concede autoridade com base em `user_id`, `caller` ou `permissions` fornecidos pelo cliente.

## Serialização e validação

`VoidObjectMapper` usa propriedades `snake_case`, enums em minúsculas, instantes ISO-8601 e omite campos opcionais nulos. Na desserialização, campos desconhecidos são rejeitados.

Os schemas usam Draft-07 e IDs estáveis em `https://void.invalid/schemas/*.json`. A verificação no CI resolve referências localmente, sem rede. Kotlin e schemas rejeitam identificadores e textos obrigatórios vazios; `Plan.steps` não pode ser vazio; `Step` também rejeita tentativas negativas e timeout não positivo. Limites superiores de tamanho, tentativas e timeout continuam sendo responsabilidade de uma política de runtime futura.

## Aprovações futuras

Não existe aprovação implementada ou verificável neste repositório. O caminho `POST /api/v1/tasks/{task_id}/approve` está reservado e retorna `501`; ele não aceita nem transforma dados do cliente em autorização.

Uma aprovação futura deverá ser criada no servidor a partir da identidade autenticada e conter, no mínimo: alvo da tarefa/plano/etapa, ação normalizada, hash dos argumentos normalizados e do destino, escopo de autorização, versão da política, instante de emissão, expiração, estado de uso único e estado de revogação. O conteúdo sensível dos argumentos e do destino não deve ir para auditoria apenas para formar esse vínculo.

Aprovar não executa nada e não torna uma ação permitida por si só. Antes de qualquer efeito, um runtime futuro deverá validar propriedade e autorização, confirmar que o vínculo não foi alterado, verificar expiração, consumo e revogação de forma atômica, e avaliar novamente o `PolicyGate`. Uma política que nega continua negando mesmo se houver uma aprovação apresentada.

## Compatibilidade da API futura

`Openapi.Yaml` preserva a forma pretendida da API, mas seus caminhos são reservados. Em especial, `/api/v1/toolcalls` não executa ferramentas: qualquer implementação futura deverá encaminhar pedidos ao `PolicyGate`, exigir identidade autenticada e aprovação vinculada a ação, argumentos, destino e expiração antes de chamar um runner.

## Fora de escopo

Este repositório ainda não implementa runner, autorização, aprovações, persistência Android nem UI Android. Conteúdo externo deve ser tratado como não confiável, e auditorias futuras precisam redigir segredos e dados pessoais antes de persistir. Consulte a [revisão de segurança](SECURITY_REVIEW.md) para riscos abertos.
