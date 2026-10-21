# Roadmap do VOID AI

> **Direção do projeto:** construir um planejador de IA Android-first que transforma objetivos em propostas revisáveis. O VOID só avança para execução quando os controles, evidências e limites dessa etapa existirem e forem testados.

| Agora | Depois | Não ainda |
| --- | --- | --- |
| Propostas revisáveis | Simulações controladas | Conectores e contas reais |
| Estados auditáveis | Interface Android | Execução automática |
| PolicyGate restritivo | Aprovação verificável | Autorização por dados do cliente |

## Onde estamos

O núcleo atual já prova o fluxo de **planejamento**, não de execução:

- `void-planner` transforma uma tarefa em plano persistível com status `pending_review`.
- Tarefa, plano e etapa possuem estados explícitos, transições restritas e estados terminais.
- O `PolicyGate` avalia pedidos e **nega por padrão**. Ele não executa ferramentas.
- Contratos Kotlin, JSON Schema Draft-07 e serialização são verificados no CI.
- Propostas inválidas são rejeitadas antes de persistência: vazias, com IDs ausentes ou duplicados, textos obrigatórios vazios ou etapas com `tool`.

O que ainda **não** existe: aplicativo Android instalável, servidor, autenticação, aprovação verificável, executor, conector externo ou ferramenta real.

[Ver contratos](CONTRACTS.md) · [Ver segurança](SECURITY_REVIEW.md) · [Ver execuções da CI](https://github.com/wandersonsaint014-hue/VOID-IA-public/actions)

## Ordem de entrega

```mermaid
flowchart LR
    A[1. Base reproduzível] --> B[2. Controle verificável]
    B --> C[3. Simulação segura]
    C --> D[4. Interface Android]
    D --> E[5. Uma integração real]
```

Cada fase depende da anterior. Não há atalho de planejamento para conector real, porque é justamente aí que projetos simpáticos ganham cicatrizes caras.

---

## 1. Base reproduzível

**Objetivo:** qualquer pessoa conseguir validar o núcleo sem adivinhar versões ou depender de configuração local implícita.

**Em andamento**

- [x] Executar testes Kotlin e schemas no GitHub Actions em checkout limpo.
- [x] Cobrir rejeições do planejador e impedir persistência de propostas inválidas.
- [x] Validar contratos de serialização e schemas offline.
- [ ] Adicionar Gradle Wrapper em alteração técnica isolada.
- [ ] Ampliar testes de compatibilidade Kotlin, JSON e OpenAPI.
- [ ] Revisar dependências sem prometer ausência total de vulnerabilidades.

**Pronto quando**

> Uma pessoa consegue clonar, executar verificações documentadas e reproduzir o resultado sem Gradle instalado globalmente.

**Não libera ainda:** aplicativo, servidor, autenticação ou execução de ferramentas.

---

## 2. Controle antes dos conectores

**Objetivo:** garantir que uma proposta seja rastreável e que nenhuma decisão do cliente possa virar autorização por acidente.

**Em andamento**

- [x] Produzir apenas planos revisáveis, auditáveis e sem ferramentas executáveis.
- [x] Formalizar estados de `Task`, `Plan` e `Step`.
- [x] Restringir transições inválidas e bloquear retorno de estados terminais.
- [x] Implementar `PolicyGate` como avaliador com negação por padrão.
- [ ] Implementar aprovação verificável vinculada a identidade autenticada.
- [ ] Vincular aprovação a ação, argumentos normalizados, destino, expiração e uso único.
- [ ] Implementar revogação e impedir repetição de efeitos sensíveis.
- [ ] Definir limites de payload, tentativas, timeout, idempotência e orçamento no runtime.
- [ ] Modelar falha confirmada, efeito incerto e cancelamento.
- [ ] Tratar conteúdo externo e resposta de modelo como dados não confiáveis.

**Pronto quando**

> Testes rejeitam ação não autorizada, aprovação alterada, expirada, revogada ou já consumida. Uma aprovação nunca supera uma decisão negativa da política.

**Regra inegociável:** aprovar uma proposta não executa nada.

---

## 3. Simulação segura

**Objetivo:** exercitar o ciclo completo de decisão e resultado sem tocar em contas, mensagens, pagamentos ou serviços externos.

**Planejado**

- [ ] Criar um runner mínimo restrito a ferramentas simuladas.
- [ ] Reavaliar `PolicyGate` imediatamente antes de cada efeito simulado.
- [ ] Persistir estado relevante e retomar com segurança após reinício.
- [ ] Registrar evidências sem expor segredos ou dados pessoais.
- [ ] Limitar recursos, entradas, tempo e tentativas.
- [ ] Testar cancelamento parcial, falha confirmada e efeito incerto.
- [ ] Criar testes de reconciliação e recuperação reproduzíveis.

**Pronto quando**

> Sucesso, falha, incerteza e interrupção possuem evidência, estado final compreensível e recuperação testada, tudo sem conta real.

**Não libera ainda:** conectores de terceiros ou efeitos fora do ambiente controlado.

---

## 4. Interface Android preservada

**Objetivo:** levar o fluxo seguro à interface sem esconder decisões importantes ou perder estado no ciclo de vida do aplicativo.

**Planejado**

- [ ] Implementar as seis telas da [direção visual](INTERFACE.md).
- [ ] Mostrar proposta, limites, estado e decisão de forma compreensível.
- [ ] Conectar a interface primeiro ao fluxo simulado.
- [ ] Preservar decisões e estado após reinício.
- [ ] Validar acessibilidade, leitura e navegação por teclado/tecnologias assistivas quando aplicável.
- [ ] Testar ciclo de vida Android e erros de rede simulados.

**Pronto quando**

> Uma pessoa percorre o fluxo completo de proposta a resultado simulado sem perder decisões importantes após reinício.

---

## 5. Uma integração real, sob controle

**Objetivo:** escolher **um** caso pequeno e reversível, provando controles antes de ampliar alcance.

**Só começa após as fases anteriores.**

- [ ] Selecionar uma ferramenta de baixo risco, escopo mínimo e recuperação possível.
- [ ] Implementar autenticação e autorização no servidor.
- [ ] Armazenar segredos fora de logs, auditoria e clientes.
- [ ] Exigir aprovação específica para ação, argumentos e destino.
- [ ] Criar limites, idempotência, timeout, revogação e observabilidade.
- [ ] Testar abuso, falha, incerteza, cancelamento e recuperação.
- [ ] Publicar instruções, limitações e evidências antes de distribuição.

**Pronto quando**

> Os limites são explícitos, a autorização é verificável, a recuperação foi testada e o mantenedor aprovou a distribuição.

---

## Próxima mudança recomendada

**Adicionar o Gradle Wrapper.**

É uma melhoria pequena, isolada e de alto impacto para deixar o projeto reproduzível. Depois dela, a prioridade é ampliar testes de contratos antes de iniciar qualquer infraestrutura de aprovação ou execução.

## Como contribuir

Antes de abrir uma mudança, descreva:

1. o problema e o benefício;
2. o impacto nos contratos e estados;
3. os riscos, inclusive de segurança e privacidade;
4. como a alteração será testada;
5. o que a mudança **não** faz.

Frameworks multiagente, conectores e automações precisam justificar por que a solução simples não basta. Leia [CONTRIBUTING.md](../CONTRIBUTING.md), [pesquisa técnica](RESEARCH.md) e [revisão de segurança](SECURITY_REVIEW.md).
