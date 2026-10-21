# Roadmap do VOID AI

Direção proposta, sem prazos prometidos. Caixas representam critérios de conclusão, não funcionalidades existentes.

## Base observável

Há modelos/interfaces Kotlin/JVM, serialização, testes, schemas, OpenAPI e workflow. Isso não constitui produto funcional. Consulte resultados reais em Actions.

## 1. Base reproduzível

- [ ] Adicionar Gradle Wrapper revisado em mudança técnica própria.
- [ ] Validar clone limpo e registrar resultados dos testes.
- [ ] Ampliar casos negativos e compatibilidade Kotlin/JSON/API.
- [ ] Revisar dependências e atualizações sem prometer ausência total de vulnerabilidades.

**Pronto quando:** outra pessoa reproduzir ambiente e testes com instruções verificadas.

## 2. Controle antes dos conectores

- [ ] Formalizar estados e transições de tarefa/etapa.
- [ ] Vincular aprovação a identidade, ação, argumentos, destino e expiração.
- [ ] Definir idempotência, limites de tentativas, timeout e payload.
- [ ] Separar falha confirmada, efeito incerto e cancelamento.
- [ ] Tratar conteúdo externo como dado não confiável.

**Pronto quando:** testes rejeitarem ação não autorizada, aprovação alterada/expirada e repetição sensível indevida.

## 3. Fluxo com ferramentas simuladas

- [ ] Implementar runner mínimo sem contas reais.
- [ ] Persistir estado relevante e retomar após reinício.
- [ ] Redigir dados sensíveis na auditoria e limitar recursos.
- [ ] Testar cancelamento parcial e reconciliação de efeito incerto.

**Pronto quando:** sucesso, falha, incerteza e interrupção tiverem evidências e recuperação reproduzíveis.

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

## Participar

Abra issue com problema, benefício, impacto nos contratos, risco e critério de teste. Preserve a direção visual ou justifique a alternativa. Multiagentes/frameworks exigem comparação com solução mais simples.

Referências: [pesquisa técnica](RESEARCH.md).
