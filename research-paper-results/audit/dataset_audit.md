# Urban Pulse – Dataset Audit & Provenance Report

**Date:** 2026-11-14  
**Author:** Senior Machine Learning & Research Engineer  
**Project:** Urban Pulse – AI-Powered Smart Civic Intelligence Platform  
**Notebook Reference:** `ml-resources/Civic_Issue_Image_Classification.ipynb`

---

## 1. Overview and Provenance

Urban Pulse employs a two-stage cascaded visual triage pipeline:
1. **Stage 1 (Binary Gatekeeper):** Filters incoming camera uploads into `CIVIC_ISSUE` vs. `NOT_CIVIC_ISSUE`.
2. **Stage 2 (8-Class Multi-class Classifier):** Classifies confirmed civic grievances into 8 actionable municipal categories.

All training data was acquired from open-access academic and civic repositories on Kaggle.

---

## 2. Eight-Class Civic Issues Dataset

### 2.1 Primary Source
- **Kaggle Dataset:** `akinduhiman/urban-issues-dataset`
- **URL:** [https://www.kaggle.com/datasets/akinduhiman/urban-issues-dataset](https://www.kaggle.com/datasets/akinduhiman/urban-issues-dataset)
- **License:** CC0-1.0 (Public Domain Dedication)
- **Archive Size:** 4.53 GB (ZIP compressed)
- **Directory Structure:** Hierarchical category folders containing pre-partitioned `train/images`, `valid/images`, and `test/images`.

### 2.2 Class Inventory & Split Distribution

The dataset provides a held-out test split created prior to model training. The table below outlines the exact image counts for each class:

| Index | Category Name | Train | Validation | Held-out Test | Total | Share (%) |
|:---:|---|:---:|:---:|:---:|:---:|:---:|
| 0 | `Damaged concrete structures` | 9,315 | 1,228 | 1,222 | 11,765 | 26.14% |
| 1 | `DamagedElectricalPoles` | 7,271 | 513 | 328 | 8,112 | 18.02% |
| 2 | `DamagedRoadSigns` | 2,267 | 43 | 39 | 2,349 | 5.22% |
| 3 | `DeadAnimalsPollution` | 172 | 32 | 16 | 220 | 0.49% |
| 4 | `FallenTrees` | 8,500 | 982 | 1,632 | 11,114 | 24.69% |
| 5 | `Garbage` | 3,133 | 660 | 337 | 4,130 | 9.18% |
| 6 | `IllegalParking` | 57 | 3 | 7 | 67 | 0.15% |
| 7 | `Potholes and RoadCracks` (Base) | 5,667 | 1,219 | 369 | 7,255 | 16.12% |
| **Total** | **8-Class Base Dataset** | **36,382** | **4,680** | **3,950** | **45,012** | **100.00%** |
| - | *Fine-tuning Augmentation (Potholes)* | +720 | 0 | 0 | +720 | - |
| **Total** | **8-Class Refined Dataset** | **37,102** | **4,680** | **3,950** | **45,732** | - |

### 2.3 Exclusion of the Graffiti Category
The original 9-class dataset included a `Graffitti` category (1,704 train, 214 val, 210 test, total 2,128). During initial experimental evaluation:
- Graffiti exhibited severe cross-class false positives with `Damaged concrete structures` (75 misclassifications on test data) due to wall texture similarities.
- From a municipal governance perspective, graffiti remediation is handled under general public works rather than an independent rapid-dispatch department.
- Consequently, Graffiti was systematically dropped in Cell 158 of the training notebook to yield the clean 8-class formulation.

---

## 3. Binary Relevance Dataset (Civic vs. Non-Civic Gatekeeper)

### 3.1 Evolution: v1 to v2 Architecture
- **Binary v1 (CIFAR-10 Negatives):**
  - Positive: 8,000 civic images from `urban-issues-dataset`.
  - Negative: 8,000 non-civic images from Kaggle CIFAR-10 (`kaggle_cifar10/train.7z`).
  - *Failure mode:* Upscaling 32×32 CIFAR-10 objects (airplanes, animals) created domain shift artifacts. When presented with clean, unblemished asphalt roads in real-world testing, the v1 model falsely classified them as civic issues (predicting "pothole" with high confidence) because it had never seen clean roads during negative training.
- **Binary v2 (Street-Surface Visual Negatives):**
  - Source: Kaggle `street_surface_vis` image dataset (`street_surface_vis_candidates.csv`).
  - Positives: 4,000 real-world civic images (sampled across all 8 civic issue categories).
  - Negatives: 4,000 real-world street and pavement photographs containing clean, damage-free asphalt, sidewalks, and lane markings.
  - Validation: 1,000 civic issues + 900 clean road negative controls.
  - *Result:* Model learned the precise discriminative boundary between damaged road surfaces and undamaged road surfaces.

---

## 4. Dataset Preprocessing & Data Augmentation

### 4.1 Image Preprocessing Pipeline
- **Resolution:** All images resized to $224 \times 224$ pixels, 3 channels (RGB).
- **Format Normalization:** Handled via PIL / `tf.io.read_file` with automatic decompression of JPEG, PNG, BMP, and WebP.
- **Normalization:**
  - For MobileNetV2: Scaled to $[-1.0, 1.0]$ via standard MobileNetV2 `preprocess_input` (equivalent to `(x / 127.5) - 1.0`).
  - In `urbanpulse_8class_pothole_stage1.keras`, preprocessing is embedded within the model graph as Keras layers (`TrueDivide` and `Subtract`), allowing raw $[0, 255]$ pixel arrays to be directly supplied by the API.

### 4.2 Data Augmentation Techniques
In Cell 73, 159, and 181, data augmentation was applied dynamically using `tf.keras.layers`:
1. `RandomFlip("horizontal")`: Simulates traffic direction variations.
2. `RandomRotation(0.1)`: $\pm 36^\circ$ rotation mimicking angled handheld smartphone photography.
3. `RandomZoom(0.1)`: $\pm 10\%$ zoom accommodating varying distance from the physical civic defect.
4. `RandomContrast(0.1)`: Accommodating varying sunlight, shadow, and overcast weather conditions.

### 4.3 Class Imbalance Mitigation
Due to natural class imbalance (e.g., `DeadAnimalsPollution` with 172 train images vs `Damaged concrete structures` with 9,315), balanced class weighting was computed using scikit-learn:
$$w_j = \frac{N}{K \cdot N_j}$$
Capped at $w_{\max} = 4.0$ to prevent ultra-minority classes (such as `IllegalParking`) from destabilizing Adam gradient updates.
