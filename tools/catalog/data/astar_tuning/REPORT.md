# A* ordering tuning

Status: complete 10,000-seed manifest and trained-ordering comparison; the Optuna study
stopped after trial 77 when one Kotlin runner invocation exited with code 1.

## Frozen protocol

- Labels: replay-certified `SOLVED`, full-graph `PROVEN_LOST`, or `UNKNOWN`; timeouts are never losses.
- Split: deterministic seed permutation with salt `0x51A7`, 70% train / 15% validation / 15% untouched test.
- Primary objective: solved coverage at the production node cap.
- Secondary objectives: coverage area over lower caps, PAR2 nodes, p95 solved nodes, and certificate length.
- Runtime selection is made by the real Kotlin solver. Offline rank accuracy is diagnostic only.
- Desktop fitting uses a non-binding wall-clock limit so the node cap remains the deterministic budget; finalists separately face the real 8-second and on-device gates.

## Reproduction

Build the desktop runner:

```powershell
.\gradlew.bat :tools:catalog:installDist
```

Prepare the frozen manifest after `solver_benchmark.csv` is complete:

```powershell
tools\catalog\build\install\catalog-tool\bin\catalog-tool.bat prepare-astar-tuning tools/catalog/data/solver_benchmark.csv tools/catalog/data/astar_tuning/seeds.csv
```

Create the local ML environment from `tools/catalog/ml/requirements.txt`, then run `optimize_ordering.py --help` and `train_ranker.py --help` for the experiment commands. Raw studies, ranking rows, and models are reproducible but intentionally ignored; the seed manifest, candidate baselines, summary, and this report are the reviewable evidence.

## Results

The frozen manifest contains 10,000 seeds: 7,000 train, 1,500 validation, and 1,500
test; labels are 8,328 `SOLVED`, 267 `PROVEN_LOST`, and 1,405 `UNKNOWN`. Trial 63 was
the best completed Optuna trial:

`lower=43, down=94, ace=48, needed=51, withdrawal=true`

The study completed 31 trials, pruned 46, and failed trial 77 before reaching the
requested 100 trials. The best candidate was then benchmarked across all 10,000 seeds
at 300,000 nodes and 8 seconds. The checked-in baseline contains searches up to one
million nodes, so comparisons below normalize it to the same 300,000-node cutoff.

| Frozen positive split | Seeds | Baseline wins | Trained wins | Coverage change |
|---|---:|---:|---:|---:|
| Train | 5,818 | 4,631 (79.60%) | 4,843 (83.24%) | +3.64 pp |
| Validation | 1,273 | 1,015 (79.73%) | 1,060 (83.27%) | +3.54 pp |
| Test | 1,237 | 981 (79.31%) | 1,012 (81.81%) | +2.51 pp |

Across all 10,000 seeds, including unresolved labels, wins rose from 6,627 to 7,087
(+460, +4.60 percentage points). The candidate gained 1,214 wins and regressed 754
baseline wins. Average nodes per win fell from 54,387 to 19,328 (-64.5%), and average
time per win fell from 232.5 ms to 88.9 ms (-61.8%). Seed 78 is one regression: the
baseline wins in 118,503 nodes while the trained ordering reaches the 300,001-node
cutoff without a certificate. The benchmark ordering changed; the on-device hint
portfolio remains unchanged.

The CUDA XGBoost smoke study used 8,876 training examples in 2,495 branch groups and
4,925 validation examples in 1,344 groups. Validation top-one agreement with the
observed certificate move was 80.65%. The strongest SHAP signals were change in
needed-card depth, change in face-down count, tableau-to-tableau move type, and change
in the admissible lower bound. Those labels describe one observed certificate, not proof
that every sibling loses, so the ranker remains a feature-discovery tool until stronger
child labels and end-to-end held-out solver results exist.
