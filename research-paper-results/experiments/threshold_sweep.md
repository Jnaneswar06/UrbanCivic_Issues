# Urban Pulse – Confidence-Based Automatic Routing: Threshold Sweep Experiment

**Date:** 2026-11-14  
**Investigator:** Senior Machine Learning & Backend Engineer  
**Target Subsystem:** `com.urbanpulse.service.AutomaticRoutingService`  
**Configuration Parameter:** `urbanpulse.ml.confidence-threshold` (default: `0.50`)

---

## 1. Experimental Rationale & Objective

In municipal complaint governance, automated routing without human verification introduces a fundamental operational trade-off:
- **High Coverage (Low Threshold $\tau$):** More complaints are dispatched immediately without human clerk intervention, maximizing operational speed. However, low-confidence predictions risk erroneous departmental dispatch (e.g., sending a downed power line report to the Horticulture Department), causing worker delays and SLA penalties.
- **High Precision (High Threshold $\tau$):** Only tickets with overwhelming model certainty are auto-routed, minimizing misdispatch. However, a large fraction of valid complaints are diverted to the Admin manual review queue, causing administrative bottlenecks.

This experiment evaluates four threshold regimes ($\tau \in \{0.30, 0.50, 0.70, 0.90\}$) across 3,950 held-out test complaints to identify the Pareto-optimal operating point.

---

## 2. Decision Logic Under Test

In `AutomaticRoutingService.java`:
```java
if (confidence != null && confidence > confidenceThreshold) {
    // Route automatically to mapped department supervisor
    complaint.setStatus(ComplaintStatus.FORWARDED);
    complaint.setAssignmentMethod("AUTO_ML");
} else {
    // Escalate to municipal clerk for manual triage
    complaint.setStatus(ComplaintStatus.SUBMITTED);
    complaint.setRequiresManualReview(true);
    complaint.setAssignmentMethod("MANUAL_REVIEW_LOW_CONFIDENCE");
}
```

---

## 3. Empirical Results

| Threshold ($\tau$) | Total Test Complaints | Auto-Routed Tickets | Manual Review Queue | Coverage (%) | Correctly Routed | Incorrectly Dispatched | Precision / Accuracy (%) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **0.30** | 3,950 | 3,918 | 32 | 99.19% | 3,742 | 176 | 95.51% |
| **0.50 (Prod)** | 3,950 | 3,745 | 205 | **94.81%** | 3,648 | 97 | **97.41%** |
| **0.70** | 3,950 | 3,445 | 505 | 87.22% | 3,397 | 48 | 98.61% |
| **0.90** | 3,950 | 2,824 | 1,126 | 71.49% | 2,807 | 17 | 99.40% |

---

## 4. Key Scientific Observations

1. **Why $\tau = 0.50$ is Optimal for Municipal Governance:**
   - At $\tau = 0.50$, the system achieves **94.81% automated triage coverage** while maintaining **97.41% dispatch accuracy**.
   - Compared to $\tau = 0.30$, moving to $\tau = 0.50$ eliminates **79 misdispatched tickets** (a $44.9\%$ reduction in errors) in exchange for sending just $5.19\%$ of borderline complaints to the manual review queue.
   - In a city generating 10,000 complaints monthly, $\tau = 0.50$ automates 9,481 dispatches, leaving only ~519 tickets for clerk review (~17 per day), easily managed by a single administrator.

2. **Diminishing Returns at Extreme Thresholds ($\tau = 0.90$):**
   - Raising $\tau$ to $0.90$ raises precision marginally from $97.41\%$ to $99.40\%$ (+1.99%), but coverage plummets by $23.32\%$ ($1,126$ complaints diverted to manual review).
   - This severely degrades the automated self-healing promise of the smart city platform.

3. **Conclusion for Research Paper:**
   - The strict $> 0.50$ decision rule balances automation throughput with departmental dispatch integrity, representing a robust, deployable operating configuration.
