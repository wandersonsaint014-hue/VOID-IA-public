# Roadmap do VOID AI

Direção proposta, sem prazos prometidos. Caixas representam critérios de conclusão verificáveis, não funcionalidades presumidas. O VOID segue como núcleo Kotlin/JVM Android-first: planos são revisáveis, e não existe execução automática de ferramentas.

## Estado confirmado

- O agente `void-planner` transforma uma tarefa em proposta persistida com status `pending_review`, trilha de auditoria e sem chamar ferramentas.
- Propostas vazias, etapas sem ID, IDs duplicados, descrições/responsáveis vazios e qualquer etapa com `tool` são rejeitados antes de persistência.
- A verificação do GitHub Actions no commit `132fde8` concluiu com sucesso em 21/10/2026. Ela cobre `:core:test` e os schemas Draft-07 offline. Consulte resultados reais em [Actions](https://github.com/wandersonsaint014-hue/VOID-IA-public/actions).
- Ainda não há aplicativo Android instalável, servidor, executor, conector real, aprovação verificável ou autenticação de pessoas.

## 1. Base reproduzível

- [ ] Adicionar Gradle Wrapper revisado em mudança técnica própria.
- [x] Executar e registrar verificação do núcleo e schemas no CI em checkout limpo.
- [x] Cobrir casos negativos do planejador seguro e impedir persistência de propostas inválidas.
- [ ] Ampliar compatibilidade Kotlin/JSON/OpenAPI e casos negativos fora do planejador.
- [ ] Revisar dependências e atualizações sem prometer ausência total de vulnerabilidades.

**Pronto quando:** outra pessoa reproduzir ambiente e testes com instruções verificadas, sem depender de Gradle instalado globalmente.

## 2. Controle antes dos conectores

- [x] Produzir apenas planos revisáveis, auditáveis e sem ferramentas executáveis.
- [ ] Formalizar estados e transições de tarefa, plano e etapa.
- [x] Implementar `PolicyGate` somente avaliador: negar por padrão e registrar decisão, sem chamar ferramentas.
- [ ] Vincular aprovação a identidade autenticada, ação, argumentos, destino e expiração.
- [ ] Definir idempotência, limites de tentativas, timeout e payload na política de runtime.
- [ ] Separar falha confirmada, efeito incerto e cancelamento.
- [ ] Tratar conteúdo externo como dado não confiável.

**Pronto quando:** testes rejeitarem ação não autorizada, aprovação alterada/expirada e repetição sensível indevida. Até lá, conectores e execução permanecem fora do produto.

## 3. Fluxo com ferramentas simuladas

- [ ] Implementar runner mínimo somente para ferramentas simuladas, atrás do `PolicyGate`.
- [ ] Persistir estado relevante e retomar após reinício.
- [ ] Redigir dados sensíveis na auditoria e limitar recursos.
- [ ] Testar cancelamento parcial e reconciliação de efeito incerto.

**Pronto quando:** sucesso, falha, incerteza e interrupção tiverem evidências e recuperação reproduzíveis, sem contas reais.

## 4. Interface Android preservada

- [ ] Implementar as seis telas seguindo a [direção visual](INTERFACE.md).
- [ ] Expor estado observável e receber ações por interfaces claras.
- [ ] Validar acessibilidade e ciclo de vida.
- [ ] Conectar primeiro ao fluxo simulado sem efeitos reais.

**Pronto quando:** fluxo completo testado sem perda de decisões importantes após reinício.

## 5. Uma ferramenta real sob controle

- [ ] Escolher caso pequeno, de baixo risco e escopo mínimo.
- [ ] Revisar autenticação, autorização, segredos e dados.
- [ ] Exigir aprovação específica para efeitos sensíveis.
- [ ] Publicar evidências e instruções antes de oferecer instalação.

**Pronto quando:** limites explícitos, recuperação testada e autorização do mantenedor para distribuição.

## Próxima mudança recomendada

Formalizar estados e transições de tarefa, plano e etapa. O `PolicyGate` v1 já nega por padrão e registra decisões auditáveis; ele não importa ou invoca `ToolRunner`.

## Participar

Abra issue com problema, benefício, impacto nos contratos, risco e critério de teste. Preserve a direção visual ou justifique a alternativa. Multiagentes/frameworks exigem comparação com solução mais simples.

Referências: [pesquisa técnica](RESEARCH.md) e [revisão de segurança](SECURITY_REVIEW.md).
