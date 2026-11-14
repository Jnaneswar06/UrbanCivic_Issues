# Urban Pulse – Duplicate Complaint Detection & Joining Empirical Evaluation

**Date:** 2026-11-14  
**Investigator:** Senior Backend & Testing Research Engineer  
**Component:** `com.urbanpulse.service.ComplaintService` (`checkDuplicates`, `joinComplaint`)  
**Mathematical Formulation:** Haversine Great-Circle Distance Metric  
**Configured Radius:** 30.0 meters (`DEFAULT_DUPLICATE_RADIUS_METERS`)  
**GPS Jitter Tolerance:** 1.0 meter (`GPS_TOLERANCE_METERS`) $\implies$ Effective Threshold = 31.0 meters  
**Primary Dataset:** 50 controlled synthetic geospatial complaint pairs tested across varying distances and bearing angles

---

## 1. Algorithmic Overview

To prevent multiple citizens from submitting redundant tickets for the same visible civic defect (e.g., the same pothole on a busy arterial road), Urban Pulse implements an automated spatial duplicate detection and ticket-joining engine:

1. **Category Filter:** Candidates are pre-filtered by identical `issueType` (`findByIssueTypeOrderByCreatedAtDesc`). Reports belonging to different categories at the exact same location (e.g., an illegal parking defect adjacent to a pothole) are correctly treated as distinct municipal work items.
2. **Haversine Distance Formulation:**
   $$\Delta \text{lat} = \text{radians}(\text{lat}_2 - \text{lat}_1), \quad \Delta \text{lon} = \text{radians}(\text{lon}_2 - \text{lon}_1)$$
   $$a = \sin^2\left(\frac{\Delta \text{lat}}{2}\right) + \cos(\text{lat}_1)\cos(\text{lat}_2)\sin^2\left(\frac{\Delta \text{lon}}{2}\right)$$
   $$c = 2 \cdot \text{atan2}\left(\sqrt{a}, \sqrt{1 - a}\right)$$
   $$d = R_{\text{earth}} \cdot c \quad \text{where } R_{\text{earth}} = 6,371,000 \text{ meters}$$
3. **Decision Boundary:**
   $$\text{isDuplicate} = (d \le 31.0 \text{ m}) \land (\text{Category}_1 = \text{Category}_2) \land (\text{Status} \in \text{ACTIVE\_STATUSES})$$
4. **Community Ticket Joining:** When a duplicate match is confirmed, the citizen can click **Join Issue**, creating a record in `complaint_participants`. This increments `joinCount`, elevates ticket urgency in the supervisor dashboard, and registers the citizen's FCM token for automated progress updates without generating redundant database rows.

---

## 2. Controlled Experimental Results (50 Test Pairs)

Geospatial coordinate offsets were calculated using spherical trigonometry originating from Hyderabad, India ($17.385044^\circ \text{N}, 78.486671^\circ \text{E}$) across 5 compass bearings ($0^\circ, 45^\circ, 90^\circ, 180^\circ, 270^\circ$).

| Target Distance | Category Condition | Evaluated Pairs | Ground Truth Duplicate | System Detected Duplicate | Outcome |
|:---:|:---:|:---:|:---:|:---:|:---:|
| **5.0 meters** | Same (`POTHOLE`) | 5 | 5 | 5 | **5 TP** |
| **20.0 meters** | Same (`POTHOLE`) | 5 | 5 | 5 | **5 TP** |
| **30.0 meters** | Same (`POTHOLE`) | 5 | 5 | 5 | **5 TP** |
| **50.0 meters** | Same (`POTHOLE`) | 5 | 0 | 0 | **5 TN** |
| **100.0 meters** | Same (`POTHOLE`) | 5 | 0 | 0 | **5 TN** |
| **5.0 meters** | Different (`POTHOLE` vs `GARBAGE`) | 5 | 0 | 0 | **5 TN** |
| **20.0 meters** | Different (`POTHOLE` vs `GARBAGE`) | 5 | 0 | 0 | **5 TN** |
| **30.0 meters** | Different (`POTHOLE` vs `GARBAGE`) | 5 | 0 | 0 | **5 TN** |
| **50.0 meters** | Different (`POTHOLE` vs `GARBAGE`) | 5 | 0 | 0 | **5 TN** |
| **100.0 meters** | Different (`POTHOLE` vs `GARBAGE`) | 5 | 0 | 0 | **5 TN** |

---

## 3. Quantitative Performance Summary

- **Total Test Pairs ($N$):** 50
- **True Positives (TP):** 15
- **True Negatives (TN):** 35
- **False Positives (FP):** 0
- **False Negatives (FN):** 0
- **Classification Accuracy:** **100.00%**
- **Precision:** **100.00%**
- **Recall / Sensitivity:** **100.00%**
- **Specificity:** **100.00%**
- **F1 Score:** **1.0000**

---

## 4. Key Academic & Governance Insights

1. **Physical Soundness of the 30-Meter Radius:**
   - In urban civil engineering, a 30-meter radius corresponds to typical two-lane road widths plus sidewalk margins. Potholes or street-light issues reported within this envelope represent the same physical anomaly.
   - Expanding beyond 35 meters risks collapsing two distinct potholes on adjacent city blocks into a single ticket.
2. **Role of the 1.0-Meter Jitter Margin:**
   - Consumer smartphone GPS chips under urban canopy conditions exhibit $\pm 3\text{--}5\text{ m}$ measurement noise. The $1.0\text{ m}$ tolerance ensures that a citizen standing at $30.4\text{ m}$ is not erroneously blocked from joining an existing ticket due to satellite multipath error.
3. **Category Disambiguation:**
   - Testing confirmed that even at identical coordinates ($5.0\text{ m}$ distance), different grievance types are kept distinct, preventing garbage accumulation reports from accidentally closing active electrical hazard tickets.
