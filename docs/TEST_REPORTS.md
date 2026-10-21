# Relatórios de verificação

Em Actions, abra a execução e o job Verify core. O resumo mostra revisão executada, revisão da proposta, resultado Gradle e contagens de testes. Em pull requests, a revisão executada pode ser o merge temporário criado pelo GitHub.

Os artefatos `core-tests-<sha>-<run>-<attempt>` contêm XML JUnit e HTML do Gradle, com retenção de 7 dias. São coletados mesmo após falha, salvo cancelamento ou encerramento do runner. Ausência de arquivos falha a coleta. Falha de upload não equivale a falha funcional dos testes, mas impede a entrega completa das evidências.

O job remove resultados anteriores, força a execução sem build cache e não usa continue-on-error. O verificador exige XML consistente e pelo menos um teste não ignorado; falhas e erros resultam em saída não zero. O status original do Gradle permanece preservado. Schemas possuem etapa e resumo independentes, mesmo após falha Kotlin, respeitando cancelamento.

Para testar somente o verificador, sem JVM ou rede:

```bash
python3 -m unittest discover -s scripts -p 'test_summarize_test_results.py' -v
```

Esses testes usam XML sintético, inclusive falhas intencionais. Não substituem a suíte Kotlin nem comprovam upload em GitHub Actions. O resumo exibe apenas totais; XML/HTML podem conter saída dos testes. Não inclua credenciais nem dados pessoais nos testes. Retenção não garante remoção de cópias baixadas.

A mudança não altera lógica Kotlin, dependências, schemas ou requisitos Java/Gradle. Não valida Android nem conectores reais.
