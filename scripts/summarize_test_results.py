"""Validate fresh Gradle JUnit XML and write an aggregate GitHub summary."""
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def collect(directory):
    files = sorted(Path(directory).glob('TEST-*.xml'))
    if not files:
        raise ValueError('Nenhum XML de testes encontrado; execução não comprovada.')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in files:
        root = ET.parse(path).getroot()
        if root.tag != 'testsuite':
            raise ValueError('Formato XML inesperado.')
        cases = root.findall('testcase')
        observed = dict(tests=len(cases), failures=0, errors=0, skipped=0)
        for case in cases:
            states = [name for name in ('failure', 'error', 'skipped') if case.find(name) is not None]
            if len(states) > 1:
                raise ValueError('Caso com resultados incompatíveis.')
            if states:
                observed[{'failure': 'failures', 'error': 'errors', 'skipped': 'skipped'}[states[0]]] += 1
        for key in totals:
            declared = int(root.get(key, '0'))
            if declared < 0 or declared != observed[key]:
                raise ValueError('Contagens XML inconsistentes.')
            totals[key] += observed[key]
    if totals['tests'] == totals['skipped']:
        raise ValueError('Nenhum teste executado ou todos ignorados.')
    return totals


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', default='core/build/test-results/test')
    args = parser.parse_args()
    outcome = os.environ.get('KOTLIN_OUTCOME', 'unknown')
    lines = ['## Testes Kotlin/JVM', '',
             f"Revisão executada: `{os.environ.get('GITHUB_SHA', 'local')}`", 
             f"Revisão da proposta: `{os.environ.get('SOURCE_SHA', 'local')}`",
             f"Resultado da etapa Gradle: `{outcome}`", '']
    server = os.environ.get('GITHUB_SERVER_URL', 'https://github.com')
    repository = os.environ.get('GITHUB_REPOSITORY')
    run = os.environ.get('GITHUB_RUN_ID')
    if repository and run:
        lines += [f'Execução: {server}/{repository}/actions/runs/{run}', '']
    status = 1
    try:
        totals = collect(args.directory)
        passed = totals['tests'] - totals['failures'] - totals['errors'] - totals['skipped']
        lines += ['| Total | Aprovados | Falhas | Erros | Ignorados |',
                  '| --- | --- | --- | --- | --- |',
                  f"| {totals['tests']} | {passed} | {totals['failures']} | {totals['errors']} | {totals['skipped']} |"]
        status = int(bool(totals['failures'] or totals['errors']))
        if outcome not in ('success', 'unknown'):
            status = 1
        lines += ['', 'XML válido não substitui o resultado da etapa Gradle.']
    except (ValueError, OSError, ET.ParseError):
        # Do not echo test output, exception text, paths or XML payloads into the summary.
        lines += ['Evidência incompleta: XML ausente, inválido, inconsistente ou sem testes executados.',
                  'Consulte a etapa Gradle para distinguir compilação, execução e interrupção.']
    lines += ['', 'Schemas são verificados em etapa independente. Isto não valida Android.']
    text = '\n'.join(lines) + '\n'
    print(text)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as stream:
            stream.write(text)
    return status


if __name__ == '__main__':
    raise SystemExit(main())
