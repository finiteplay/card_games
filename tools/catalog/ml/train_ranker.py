"""Train a CUDA LambdaMART model over grouped legal successor states."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
import xgboost as xgb


METADATA = {"qid", "seed", "split", "state_index", "label"}


def load_examples(path: Path) -> tuple[pd.DataFrame, np.ndarray, np.ndarray]:
    frame = pd.read_csv(path)
    frame = frame.sort_values(["qid", "label"], kind="stable").reset_index(drop=True)
    labels = frame.pop("label").to_numpy(dtype=np.float32)
    qid = frame.pop("qid").to_numpy(dtype=np.int64)
    features = frame.drop(columns=[column for column in METADATA - {"qid", "label"} if column in frame])
    features = pd.get_dummies(features, columns=["move_type"], dtype=np.float32)
    return features.astype(np.float32), labels, qid


def align_features(
    training: pd.DataFrame,
    validation: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    columns = sorted(set(training.columns) | set(validation.columns))
    return training.reindex(columns=columns, fill_value=0), validation.reindex(columns=columns, fill_value=0)


def top_one_accuracy(labels: np.ndarray, scores: np.ndarray, qid: np.ndarray) -> float:
    correct = 0
    groups = 0
    for group in np.unique(qid):
        mask = qid == group
        group_labels = labels[mask]
        group_scores = scores[mask]
        correct += int(group_scores[group_labels == 1].max() >= group_scores[group_labels == 0].max())
        groups += 1
    return correct / max(groups, 1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", type=Path, required=True)
    parser.add_argument("--validation", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--importance", type=Path, required=True)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--estimators", type=int, default=512)
    args = parser.parse_args()

    train_x, train_y, train_qid = load_examples(args.train)
    valid_x, valid_y, valid_qid = load_examples(args.validation)
    train_x, valid_x = align_features(train_x, valid_x)

    ranker = xgb.XGBRanker(
        objective="rank:ndcg",
        eval_metric=["ndcg@1", "ndcg@3"],
        tree_method="hist",
        device=args.device,
        n_estimators=args.estimators,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.9,
        reg_lambda=2.0,
        lambdarank_pair_method="topk",
        lambdarank_num_pair_per_sample=4,
        random_state=0x51A7,
    )
    ranker.fit(
        train_x,
        train_y,
        qid=train_qid,
        eval_set=[(valid_x, valid_y)],
        eval_qid=[valid_qid],
        verbose=False,
    )

    args.model.parent.mkdir(parents=True, exist_ok=True)
    ranker.save_model(args.model)
    scores = ranker.predict(valid_x)
    report = {
        "device": args.device,
        "training_examples": len(train_x),
        "training_groups": int(np.unique(train_qid).size),
        "validation_examples": len(valid_x),
        "validation_groups": int(np.unique(valid_qid).size),
        "validation_top_one_accuracy": top_one_accuracy(valid_y, scores, valid_qid),
        "features": list(train_x.columns),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")

    booster = ranker.get_booster()
    importance = booster.get_score(importance_type="gain")
    sample = valid_x.iloc[: min(50_000, len(valid_x))]
    contributions = booster.predict(xgb.DMatrix(sample), pred_contribs=True)
    mean_abs_shap = np.abs(contributions[:, :-1]).mean(axis=0)
    rows = pd.DataFrame(
        {
            "feature": train_x.columns,
            "gain": [importance.get(feature, 0.0) for feature in train_x.columns],
            "mean_abs_shap": mean_abs_shap,
        },
    ).sort_values(["mean_abs_shap", "gain"], ascending=False)
    args.importance.parent.mkdir(parents=True, exist_ok=True)
    rows.to_csv(args.importance, index=False)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
