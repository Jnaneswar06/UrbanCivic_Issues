import os
import io
import json
import logging
import numpy as np
import tensorflow as tf
from flask import Flask, request, jsonify
from PIL import Image
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("urbanpulse-ai")

app = Flask(__name__)

# Base directory for robust relative resolution
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_MODELS_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "models"))

# Model configuration paths
BINARY_MODEL_PATH = os.environ.get(
    "BINARY_MODEL_PATH",
    os.path.join(DEFAULT_MODELS_DIR, "urbanpulse_binary_v2_best.keras")
)
CLASSIFIER_MODEL_PATH = os.environ.get(
    "CLASSIFIER_MODEL_PATH",
    os.path.join(DEFAULT_MODELS_DIR, "urbanpulse_8class_pothole_stage1.keras")
)
CLASSES_PATH = os.environ.get(
    "CLASSES_PATH",
    os.path.join(DEFAULT_MODELS_DIR, "urbanpulse_8class_class_names.json")
)
MODEL_VERSION = os.environ.get("MODEL_VERSION", "urbanpulse_8class_pothole_stage1")

logger.info(f"Loading Stage 1 Binary Gatekeeper from: {BINARY_MODEL_PATH}")
logger.info(f"Loading Stage 2 8-Class Classifier from: {CLASSIFIER_MODEL_PATH}")
logger.info(f"Loading Class Names from: {CLASSES_PATH}")

# Custom objects required for deserializing Keras models with embedded Lambda layers
custom_objects = {"preprocess_input": preprocess_input}

# 1. Load Stage 1 Binary Model
try:
    binary_model = tf.keras.models.load_model(
        BINARY_MODEL_PATH,
        compile=False,
        custom_objects=custom_objects
    )
    logger.info("Stage 1 Binary Gatekeeper model loaded successfully!")
except Exception as e:
    logger.error(f"Failed to load Stage 1 Binary Gatekeeper: {e}")
    binary_model = None

# 2. Load Stage 2 Eight-Class Classifier
try:
    classifier_model = tf.keras.models.load_model(
        CLASSIFIER_MODEL_PATH,
        compile=False
    )
    logger.info("Stage 2 8-Class Classifier model loaded successfully!")
except Exception as e:
    logger.error(f"Failed to load Stage 2 8-Class Classifier: {e}")
    classifier_model = None

# Legacy alias for backward compatibility
model = classifier_model

# 3. Load 8 Class Names
try:
    with open(CLASSES_PATH, "r", encoding="utf-8") as f:
        class_names = json.load(f)
    logger.info(f"Loaded {len(class_names)} classes: {class_names}")
except Exception as e:
    logger.error(f"Failed to load class names: {e}")
    class_names = []


@app.route("/health", methods=["GET"])
def health():
    binary_ok = binary_model is not None
    classifier_ok = classifier_model is not None

    if not binary_ok and not classifier_ok:
        return jsonify({
            "status": "error",
            "model_loaded": False,
            "binary_model_loaded": False,
            "classifier_model_loaded": False,
            "error": "Both models failed to load"
        }), 503

    return jsonify({
        "status": "ok",
        "pipeline": "two-stage-cascaded",
        "model_loaded": binary_ok and classifier_ok,
        "binary_model_loaded": binary_ok,
        "classifier_model_loaded": classifier_ok,
        "binary_model_name": os.path.basename(BINARY_MODEL_PATH),
        "classifier_model_name": os.path.basename(CLASSIFIER_MODEL_PATH),
        "model_version": MODEL_VERSION,
        "num_classes": len(class_names),
        "classes": class_names,
        "input_dimensions": [224, 224, 3],
        "input_shape": [None, 224, 224, 3]
    }), 200


@app.route("/predict", methods=["POST"])
def predict():
    if binary_model is None or classifier_model is None:
        return jsonify({"error": "Models are not fully loaded in memory"}), 503

    # Support 'file' (Spring Boot default) with 'image' fallback
    uploaded = request.files.get("file")
    if uploaded is None or uploaded.filename == "":
        uploaded = request.files.get("image")

    if uploaded is None or uploaded.filename == "":
        return jsonify({"error": "Upload an image using the 'file' multipart field"}), 400

    try:
        file_bytes = uploaded.read()
        if not file_bytes:
            return jsonify({"error": "Uploaded file is empty"}), 400

        # Validate and open image
        try:
            image = Image.open(io.BytesIO(file_bytes)).convert("RGB")
        except Exception as img_err:
            return jsonify({"error": f"Invalid or unreadable image: {str(img_err)}"}), 400

        image = image.resize((224, 224))
        array = np.asarray(image, dtype=np.float32)
        array = np.expand_dims(array, axis=0)

        # =========================================================================
        # STAGE 1: Binary Relevance Gatekeeper (Civic vs. Non-Civic Issue)
        # =========================================================================
        # Target Encoding: Class 0 = CIVIC_ISSUE, Class 1 = NOT_CIVIC_ISSUE
        # Sigmoid output represents P(NOT_CIVIC_ISSUE | x)
        binary_pred_raw = binary_model.predict(array, verbose=0)
        not_civic_prob = float(binary_pred_raw[0][0])
        civic_prob = float(1.0 - not_civic_prob)

        logger.info(
            f"Stage 1 Gatekeeper: P(NOT_CIVIC)={not_civic_prob:.4f}, P(CIVIC)={civic_prob:.4f}"
        )

        if not_civic_prob >= 0.50:
            # ---------------------------------------------------------------------
            # CASE B: Non-Civic Issue Detected
            # STAGE 2 IS STRICTLY SKIPPED! No 8-class model execution!
            # ---------------------------------------------------------------------
            logger.info("Stage 1 REJECTED as NOT_CIVIC_ISSUE. Halting pipeline before Stage 2.")
            return jsonify({
                "success": True,
                "isCivicIssue": False,
                "stage": "BINARY_RELEVANCE",
                "classificationStatus": "REJECTED",
                "binaryPrediction": "NOT_CIVIC_ISSUE",
                "notCivicProbability": not_civic_prob,
                "civicProbability": civic_prob,
                "predictedClass": None,
                "confidence": not_civic_prob,
                "probabilities": [],
                "classProbabilities": {},
                "message": "Image does not appear to contain a civic issue. Forwarded for Admin manual review.",
                "modelVersion": MODEL_VERSION
            }), 200

        # =========================================================================
        # STAGE 2: Eight-Class Civic Grievance Classifier
        # Executed ONLY when Stage 1 confirms a genuine CIVIC_ISSUE
        # =========================================================================
        logger.info("Stage 1 PASSED as CIVIC_ISSUE. Executing Stage 2 Eight-Class Classifier.")
        predictions = classifier_model.predict(array, verbose=0)[0]
        index = int(np.argmax(predictions))
        confidence = float(predictions[index])
        predicted_class = class_names[index] if index < len(class_names) else f"Class_{index}"

        probabilities = [float(p) for p in predictions]
        class_probabilities = {
            name: float(score)
            for name, score in zip(class_names, predictions)
        }

        logger.info(
            f"Stage 2 Prediction: {predicted_class} (Confidence: {confidence:.4f})"
        )

        return jsonify({
            "success": True,
            "isCivicIssue": True,
            "stage": "EIGHT_CLASS_CLASSIFICATION",
            "classificationStatus": "CLASSIFIED",
            "binaryPrediction": "CIVIC_ISSUE",
            "notCivicProbability": not_civic_prob,
            "civicProbability": civic_prob,
            "predictedClass": predicted_class,
            "confidence": confidence,
            "probabilities": probabilities,
            "classProbabilities": class_probabilities,
            "modelVersion": MODEL_VERSION
        }), 200

    except Exception as exc:
        logger.error(f"Error during two-stage classification pipeline: {exc}", exc_info=True)
        return jsonify({"error": f"Invalid or corrupted image: {str(exc)}"}), 400


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    logger.info(f"Starting Urban Pulse Two-Stage ML Service on port {port}...")
    app.run(host="0.0.0.0", port=port)
