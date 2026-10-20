#!/usr/bin/env python3
"""Offline Draft-07 contract checks; no remote $ref resolution is permitted."""
import json
from pathlib import Path
from jsonschema import Draft7Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "schemas"
resources = []
for path in sorted(SCHEMAS.glob("*.json")):
    document = json.loads(path.read_text())
    assert document["$schema"] == "http://json-schema.org/draft-07/schema#"
    assert document["$id"] == f"https://void.invalid/schemas/{path.name}"
    resources.append((document["$id"], Resource.from_contents(document)))
registry = Registry().with_resources(resources)

def validate(name, instance):
    schema = json.loads((SCHEMAS / name).read_text())
    validator = Draft7Validator(schema, registry=registry, format_checker=FormatChecker())
    errors = sorted(validator.iter_errors(instance), key=lambda error: list(error.path))
    assert not errors, "; ".join(error.message for error in errors)

def invalid(name, instance):
    schema = json.loads((SCHEMAS / name).read_text())
    assert list(Draft7Validator(schema, registry=registry).iter_errors(instance))

validate("task.json", {"task_id": "t", "user_id": "u", "created_at": "2026-01-01T00:00:00Z", "objective": "o", "autonomy_level": "low"})
validate("plan.json", {"plan_id": "p", "task_id": "t", "steps": [{"step_id": "s", "description": "d", "responsible": "r", "retries": 0, "timeout_seconds": 1}]})
validate("tool.json", {"tool_id": "x", "name": "tool", "version": "1", "schema_input": {}, "schema_output": {}})
invalid("task.json", {"task_id": "t", "user_id": "u", "objective": "o"})
invalid("step.json", {"step_id": "s", "description": "d", "responsible": "r", "retries": -1})
invalid("step.json", {"step_id": "s", "description": "d", "responsible": "r", "timeout_seconds": 0})
invalid("tool.json", {"tool_id": "x", "schema_input": {}, "schema_output": {}})
print("schemas: offline Draft-07 contract checks passed")
