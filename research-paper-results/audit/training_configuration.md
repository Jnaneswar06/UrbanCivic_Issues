# Urban Pulse – Deep Learning Training Configuration & Hyperparameter Audit

**Date:** 2026-11-14  
**Project:** Urban Pulse – AI-Powered Smart Civic Intelligence Platform  
**Inspection Source:** `ml-resources/Civic_Issue_Image_Classification.ipynb` (Cells 71–94, 158–184, 247–251, 306–320)

---

## 1. Summary of Model Configurations

The Urban Pulse inference stack utilizes two complementary MobileNetV2-based convolutional neural networks operating in sequence:
1. **Gatekeeper:** Binary Relevance Filter (`urbanpulse_binary_v2_best.keras`)
2. **Classifier:** Eight-Class Civic Grievance Model (`urbanpulse_8class_pothole_stage1.keras`)

---

## 2. Eight-Class Model Training Configuration

### 2.1 Base Architecture
- **Base Backbone:** `MobileNetV2` (Sandler et al., CVPR 2018)
- **Pretrained Weights:** ImageNet (`imagenet_weights_tf_dim_ordering_tf_kernels_1.0_224_no_top.h5`, 9.4 MB)
- **Input Layer:** Shape `(None, 224, 224, 3)`, dtype `float32`
- **Internal Preprocessing:** `TrueDivide(127.5)` followed by `Subtract(1.0)` to map $[0, 255] \to [-1, 1]$
- **Backbone Feature Extractor:** 155 layers ending in `out_relu` (Depthwise Separable Convolutions, inverted residual bottlenecks)
- **Feature Map Dimension:** $7 \times 7 \times 1280$
- **Pooling Layer:** `GlobalAveragePooling2D()` (produces 1,280-dimensional feature embedding)
- **Regularization:** `Dropout(rate=0.2)`
- **Classification Head:** `Dense(units=8, activation="softmax", name="predictions")`
- **Total Parameter Count:** 2,268,232 parameters
  - Backbone parameters: 2,257,984
  - Top classification head parameters: $1,280 \times 8 + 8 = 10,248$

### 2.2 Hyperparameters & Training Protocol
- **Loss Function:** `sparse_categorical_crossentropy` (with integer encoded targets)
- **Optimization Algorithm:** `Adam`
  - Base Learning Rate: $\eta = 1 \times 10^{-4}$ ($0.0001$)
  - $\beta_1 = 0.9$, $\beta_2 = 0.999$, $\epsilon = 1 \times 10^{-7}$
- **Batch Size:** $32$
- **Image Resolution:** $224 \times 224$ pixels, RGB
- **Random Seed:** $42$ (enforced across NumPy, Python random, and TensorFlow dataset shuffling)
- **Dataset Size:** 37,102 training images, 4,680 validation images, 3,950 test images
- **Number of Epochs:** 12 epochs in phase 1; 8 epochs in stage-1 fine-tuning with 720 additional real-world pothole samples
- **Backbone Freezing Strategy:**
  - *Phase 1:* Backbone completely frozen (`trainable=False`), allowing top dense weights to stabilize.
  - *Stage 1 Fine-Tuning:* Preserved frozen backbone with targeted adaptation of classification head under low learning rate ($1 \times 10^{-4}$) to prevent catastrophic forgetting.

### 2.3 Training History (12-Epoch Baseline)

| Epoch | Train Accuracy | Train Loss | Val Accuracy | Val Loss | Learning Rate |
|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | 0.8925 | 0.3071 | 0.9436 | 0.1654 | 1e-4 |
| 2 | 0.9416 | 0.1669 | 0.9549 | 0.1379 | 1e-4 |
| 3 | 0.9480 | 0.1464 | 0.9515 | 0.1406 | 1e-4 |
| 4 | 0.9499 | 0.1405 | 0.9581 | 0.1269 | 1e-4 |
| 5 | 0.9527 | 0.1341 | 0.9571 | 0.1302 | 1e-4 |
| 6 | 0.9544 | 0.1298 | 0.9592 | 0.1251 | 1e-4 |
| 7 | 0.9560 | 0.1257 | 0.9600 | 0.1218 | 1e-4 |
| 8 | 0.9572 | 0.1221 | 0.9615 | 0.1192 | 1e-4 |
| 9 | 0.9585 | 0.1190 | 0.9632 | 0.1165 | 1e-4 |
| 10 | 0.9598 | 0.1162 | 0.9639 | 0.1148 | 1e-4 |
| 11 | 0.9608 | 0.1139 | 0.9645 | 0.1132 | 1e-4 |
| 12 | 0.9615 | 0.1120 | 0.9652 | 0.1119 | 1e-4 |

---

## 3. Binary Relevance Gatekeeper Model Configuration

### 3.1 Base Architecture
- **Model:** `urbanpulse_binary_v2_best.keras`
- **Base Backbone:** `MobileNetV2` (ImageNet pretrained weights)
- **Input Layer:** `(None, 224, 224, 3)`
- **Input Preprocessing:** Embedded `preprocess_input` custom lambda function
- **Pooling & Regularization:** `GlobalAveragePooling2D()` + `Dropout(rate=0.2)`
- **Classification Head:** `Dense(units=1, activation="sigmoid", name="binary_output")`
- **Total Parameters:** 2,259,265
  - Trainable parameters: $1,280 \times 1 + 1 = 1,281$
  - Non-trainable parameters: 2,257,984 (frozen backbone)

### 3.2 Hyperparameters & Training Protocol
- **Loss Function:** `BinaryCrossentropy()`
- **Optimizer:** `Adam(learning_rate=1e-4)`
- **Batch Size:** $32$
- **Epochs:** $8$
- **Decision Threshold:** $\tau = 0.50$
- **Patience:** 3 epochs
- **Target Encoding:**
  - Class 0: `CIVIC_ISSUE` (Label = 0)
  - Class 1: `NOT_CIVIC_ISSUE` (Label = 1)
  - Output equation: $\hat{y} = \sigma(W^T x + b) = P(\text{NOT\_CIVIC\_ISSUE} \mid x)$
  - Rule: If $\hat{y} < 0.50 \implies \text{CIVIC\_ISSUE}$; if $\hat{y} \ge 0.50 \implies \text{NOT\_CIVIC\_ISSUE}$.

### 3.3 Training & Validation History (Binary v2)

| Epoch | Train Loss | Validation Loss | Civic Recall | Non-Civic Recall | Checkpoint Action |
|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | 2.0971 | 0.4814 | 0.9000 | 0.6289 | Saved best model |
| 2 | 0.4232 | 0.2668 | 0.9080 | 0.8900 | Saved best model |
| 3 | 0.2602 | 0.1867 | 0.9210 | 0.9567 | Saved best model |
| 4 | 0.1817 | 0.1459 | 0.9430 | 0.9689 | Saved best model |
| 5 | 0.1432 | 0.1213 | 0.9480 | 0.9756 | Saved best model |
| 6 | 0.1198 | 0.1054 | 0.9550 | 0.9822 | Saved best model |
| 7 | 0.1012 | 0.0945 | 0.9590 | 0.9878 | Saved best model |
| 8 | 0.0889 | 0.0862 | 0.9600 | 0.9900 | **Saved best model (Final checkpoint: 0.0862)** |
