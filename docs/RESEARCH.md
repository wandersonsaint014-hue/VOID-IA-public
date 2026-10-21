# VOID AI: revisão da ideia e pesquisa técnica

**21/10/2026.** Inspeção do repositório público a partir de `e86d70dda9679d987aa6ea2aa35f903876cf0e91` e leitura de referências públicas. Este documento separa fatos observados e propostas. Não é auditoria completa ou validação de produção.

## Síntese

Preservar o VOID como assistente operacional Android-first, núcleo Kotlin/JVM, plano visível e controle humano. Manter a interface escura e verde-menta. Melhorar a apresentação sem vender o protótipo como produto pronto.

O repositório contém modelos, interfaces, serialização, schemas, OpenAPI, testes e workflow. Não contém app Android, servidor ou execução completa. O README anterior não mostrava a interface e citava Gradle 8.5; o workflow usa 8.14.4. A apresentação foi alinhada ao workflow, sem alterar o build.

## Fontes e aplicação

### GitHub: explicar utilidade, início e contribuição

[About READMEs, GitHub Docs](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes) recomenda apresentar o projeto, sua utilidade, como começar e onde conseguir ajuda. Aplicação: abertura curta, visual, estado real, comandos e links relativos. Badge de CI aponta ao workflow real; não é selo de segurança.

### Android: estado explícito e fluxo unidirecional

[Architecture recommendations](https://developer.android.com/topic/architecture/recommendations) e [UI events](https://developer.android.com/topic/architecture/ui-layer/events), Android Developers, recomendam separar UI e dados e usar estado observável. Proposta: representar plano, aprovação, execução e resultado em estado explícito, separando callbacks visuais da lógica de negócio. Compose é opção recomendada pela plataforma para trabalho futuro, não dependência adicionada aqui.

ViewModel sozinho não garante recuperação após encerramento do processo. Aprovações e resultados relevantes precisarão de persistência, expiração e recuperação testadas. Histórico da demo é temporário.

### Agentes: complexidade apenas quando necessária

[Building effective agents, Anthropic](https://www.anthropic.com/engineering/building-effective-agents), publicado em 19/12/2024. A página avisa que o ecossistema evoluiu: usamos seu princípio arquitetural, não uma comparação atual de produtos.

Distinguir workflows previsíveis de agentes que escolhem o caminho dinamicamente. Proposta: fluxo determinístico pequeno e ferramentas simuladas primeiro; medir qualidade, custo e latência antes de multiagentes. Nenhum framework ou serviço foi instalado.

### Segurança: conteúdo externo não concede autorização

[LLM01:2025 Prompt Injection, OWASP](https://genai.owasp.org/llmrisk/llm01-prompt-injection/) descreve injeção direta/indireta, privilégio mínimo, validação em código e aprovação humana para alto risco. Proposta: páginas, documentos e respostas de ferramentas são dados não confiáveis; validar a ação no runtime e não confiar em permissões declaradas pelo cliente.

Aprovação é uma camada, não solução completa. Deve estar vinculada a identidade, ação, argumentos, destino e validade. Alterações relevantes exigem nova aprovação. Segredos não devem aparecer em prompts, exemplos ou logs públicos.

## Preservar

- Android-first com núcleo Kotlin/JVM separado da UI.
- Seis telas e identidade escura/verde-menta.
- Plano compreensível e aprovação concreta.
- Efeito incerto distinto de falha ou sucesso.
- Histórico e evidências com redação de dados sensíveis como direção futura.

## Implementar depois

1. Máquina de estados e política de aprovação vinculada à ação.
2. Identidade e autorização em fronteira confiável.
3. Runner com limites, idempotência, cancelamento e reconciliação.
4. Persistência e retomada após reinício.
5. UI Android acessível e integração controlada.

Veja [roadmap](ROADMAP.md) e [riscos conhecidos](SECURITY_REVIEW.md). Esta revisão muda apresentação e documentação, não código Kotlin, schemas, API, dependências ou workflows. Nenhuma conta foi conectada e nenhuma garantia de execução foi validada.
