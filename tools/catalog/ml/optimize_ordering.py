"""Tune SearchOrdering against the real Kotlin solver with resumable Optuna trials."""

from __future__ import annotations

import argparse
import csv
import json
import subprocess
from pathlib import Path

import optuna


def parse_stage(value: str) -> tuple[int, int]:
    seeds, nodes = value.split(":", maxsplit=1)
    return int(seeds), int(nodes)


def runner_command(runner: Path, arguments: list[str]) -> list[str]:
    if runner.suffix.lower() == ".bat":
        return ["cmd", "/d", "/c", str(runner), *arguments]
    return [str(runner), *arguments]


def evaluate(
    runner: Path,
    manifest: Path,
    split: str,
    seed_count: int,
    node_cap: int,
    duration_ms: int,
    parameters: dict[str, int | bool],
) -> dict[str, float | int]:
    arguments = [
        "eval-astar-ordering",
        str(manifest),
        split,
        str(seed_count),
        str(node_cap),
        str(duration_ms),
        str(parameters["lower_bound_weight"]),
        str(parameters["down_card_weight"]),
        str(parameters["ace_burial_weight"]),
        str(parameters["needed_card_depth_weight"]),
        str(parameters["foundation_withdrawal"]).lower(),
    ]
    completed = subprocess.run(
        runner_command(runner, arguments),
        check=True,
        capture_output=True,
        text=True,
    )
    for line in reversed(completed.stdout.splitlines()):
        if line.startswith("TUNING_RESULT "):
            return json.loads(line.removeprefix("TUNING_RESULT "))
    raise RuntimeError(f"runner returned no TUNING_RESULT:\n{completed.stdout}\n{completed.stderr}")


def scalar_score(metrics: dict[str, float | int], node_cap: int) -> float:
    # Coverage dominates every secondary term. The area rewards earlier solutions;
    # normalized PAR2 breaks otherwise-equal coverage/area results deterministically.
    return (
        float(metrics["coverageAtMaxCap"]) * 1_000_000.0
        + float(metrics["coverageArea"]) * 10_000.0
        - float(metrics["par2Nodes"]) / node_cap
    )


def enqueue_baselines(study: optuna.Study) -> None:
    baselines = [
        (1, 0, 0, 0, True),
        (10, 0, 0, 0, True),
        (25, 50, 100, 50, False),
        (25, 150, 25, 0, False),
        (25, 0, 0, 50, True),
        (25, 0, 0, 0, True),
    ]
    for lower, down, ace, needed, withdrawal in baselines:
        study.enqueue_trial(
            {
                "lower_bound_weight": lower,
                "down_card_weight": down,
                "ace_burial_weight": ace,
                "needed_card_depth_weight": needed,
                "foundation_withdrawal": withdrawal,
            },
            skip_if_exists=True,
        )


def write_summary(study: optuna.Study, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "number", "state", "value",
        "lower_bound_weight", "down_card_weight", "ace_burial_weight",
        "needed_card_depth_weight", "foundation_withdrawal",
        "coverage", "coverage_area", "par2_nodes", "p95_nodes", "median_moves",
    ]
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for trial in study.trials:
            writer.writerow(
                {
                    "number": trial.number,
                    "state": trial.state.name,
                    "value": trial.value,
                    **{name: trial.params.get(name, "") for name in fields[3:8]},
                    "coverage": trial.user_attrs.get("coverageAtMaxCap", ""),
                    "coverage_area": trial.user_attrs.get("coverageArea", ""),
                    "par2_nodes": trial.user_attrs.get("par2Nodes", ""),
                    "p95_nodes": trial.user_attrs.get("p95SolvedNodes", ""),
                    "median_moves": trial.user_attrs.get("medianCertificateMoves", ""),
                },
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runner", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--storage", default="sqlite:///tools/catalog/data/astar_tuning/studies/ordering.db")
    parser.add_argument("--study", default="astar-ordering-v1")
    parser.add_argument("--split", default="TRAIN")
    parser.add_argument("--stage", action="append", type=parse_stage, default=[])
    parser.add_argument("--trials", type=int, default=100)
    parser.add_argument("--jobs", type=int, default=1)
    # Keep wall time non-binding during desktop fitting so node-count comparisons stay
    # deterministic; finalists get the real 8-second/on-device gate separately.
    parser.add_argument("--duration-ms", type=int, default=60_000)
    parser.add_argument("--summary", type=Path, default=Path("tools/catalog/data/astar_tuning/summary.csv"))
    args = parser.parse_args()
    stages = args.stage or [(256, 25_000), (2_000, 100_000), (7_000, 300_000)]
    if args.storage.startswith("sqlite:///"):
        Path(args.storage.removeprefix("sqlite:///")).parent.mkdir(parents=True, exist_ok=True)

    sampler = optuna.samplers.TPESampler(seed=0x51A7, multivariate=True)
    pruner = optuna.pruners.SuccessiveHalvingPruner(min_resource=1, reduction_factor=4)
    study = optuna.create_study(
        study_name=args.study,
        storage=args.storage,
        direction="maximize",
        sampler=sampler,
        pruner=pruner,
        load_if_exists=True,
    )
    enqueue_baselines(study)

    def objective(trial: optuna.Trial) -> float:
        parameters: dict[str, int | bool] = {
            "lower_bound_weight": trial.suggest_int("lower_bound_weight", 1, 50),
            "down_card_weight": trial.suggest_int("down_card_weight", 0, 200),
            "ace_burial_weight": trial.suggest_int("ace_burial_weight", 0, 150),
            "needed_card_depth_weight": trial.suggest_int("needed_card_depth_weight", 0, 100),
            "foundation_withdrawal": trial.suggest_categorical("foundation_withdrawal", [True, False]),
        }
        final_metrics: dict[str, float | int] = {}
        final_score = float("-inf")
        for step, (seed_count, node_cap) in enumerate(stages):
            final_metrics = evaluate(
                args.runner,
                args.manifest,
                args.split,
                seed_count,
                node_cap,
                args.duration_ms,
                parameters,
            )
            final_score = scalar_score(final_metrics, node_cap)
            trial.report(final_score, step)
            if trial.should_prune():
                raise optuna.TrialPruned()
        for key, value in final_metrics.items():
            trial.set_user_attr(key, value)
        return final_score

    try:
        study.optimize(objective, n_trials=args.trials, n_jobs=args.jobs)
    finally:
        write_summary(study, args.summary)
    print(json.dumps({"best_value": study.best_value, "best_params": study.best_params}, indent=2))


if __name__ == "__main__":
    main()
