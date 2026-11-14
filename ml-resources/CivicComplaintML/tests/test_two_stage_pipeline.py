import os
import sys
import io
import unittest
import numpy as np
from PIL import Image

# Add Flask app directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "urbanpulse_ai")))
import app

class TwoStagePipelineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        app.app.testing = True
        cls.client = app.app.test_client()

    def create_test_image_bytes(self, color=(128, 128, 128), size=(224, 224), text_noise=False):
        img = Image.new("RGB", size, color=color)
        if text_noise:
            arr = np.array(img)
            # Add some high-contrast patterns (simulating document or non-civic graphic)
            arr[50:180, 50:180] = [255, 255, 255]
            arr[70:160, 70:160] = [0, 0, 0]
            img = Image.fromarray(arr)
        bio = io.BytesIO()
        img.save(bio, format="JPEG")
        bio.seek(0)
        return bio

    def test_01_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data.get("status"), "ok")
        self.assertEqual(data.get("pipeline"), "two-stage-cascaded")
        self.assertTrue(data.get("binary_model_loaded"))
        self.assertTrue(data.get("classifier_model_loaded"))
        self.assertEqual(data.get("num_classes"), 8)
        self.assertEqual(len(data.get("classes", [])), 8)
        print("[TEST 01 PASSED] Health endpoint reports both models healthy.")

    def test_02_empty_file_returns_400(self):
        data = {"file": (io.BytesIO(b""), "empty.jpg")}
        response = self.client.post("/predict", data=data, content_type="multipart/form-data")
        self.assertEqual(response.status_code, 400)
        json_data = response.get_json()
        self.assertIn("error", json_data)
        print("[TEST 02 PASSED] Empty file rejected with HTTP 400.")

    def test_03_no_file_field_returns_400(self):
        response = self.client.post("/predict", data={}, content_type="multipart/form-data")
        self.assertEqual(response.status_code, 400)
        json_data = response.get_json()
        self.assertIn("error", json_data)
        print("[TEST 03 PASSED] Missing file field returns HTTP 400.")

    def test_04_mock_binary_rejection_halts_stage_2(self):
        """
        Verify that when Stage 1 outputs not_civic_prob >= 0.50,
        Stage 2 is NEVER called, isCivicIssue is False, and predictedClass is None.
        """
        original_binary_model = app.binary_model
        original_classifier_model = app.classifier_model

        try:
            # Mock binary model to return 0.92 (NOT_CIVIC_ISSUE)
            class MockBinaryModel:
                def predict(self, array, verbose=0):
                    return np.array([[0.92]], dtype=np.float32)

            # Mock classifier model to raise an exception if called
            class MockClassifierModel:
                def predict(self, array, verbose=0):
                    raise AssertionError("STAGE 2 MODEL MUST NEVER EXECUTE ON NOT_CIVIC_ISSUE!")

            app.binary_model = MockBinaryModel()
            app.classifier_model = MockClassifierModel()

            img_bytes = self.create_test_image_bytes(color=(255, 0, 0))
            data = {"file": (img_bytes, "mock_irrelevant.jpg")}
            response = self.client.post("/predict", data=data, content_type="multipart/form-data")

            self.assertEqual(response.status_code, 200)
            res = response.get_json()
            self.assertTrue(res["success"])
            self.assertFalse(res["isCivicIssue"])
            self.assertEqual(res["classificationStatus"], "REJECTED")
            self.assertEqual(res["stage"], "BINARY_RELEVANCE")
            self.assertEqual(res["binaryPrediction"], "NOT_CIVIC_ISSUE")
            self.assertGreaterEqual(res["notCivicProbability"], 0.50)
            self.assertIsNone(res["predictedClass"])
            self.assertEqual(res["probabilities"], [])
            self.assertIn("does not appear to contain a civic issue", res["message"])
            print("[TEST 04 PASSED] Binary rejection halts pipeline; Stage 2 was not executed.")
        finally:
            app.binary_model = original_binary_model
            app.classifier_model = original_classifier_model

    def test_05_mock_civic_issue_executes_stage_2(self):
        """
        Verify that when Stage 1 outputs not_civic_prob < 0.50,
        Stage 2 executes and returns the predicted civic class.
        """
        original_binary_model = app.binary_model
        original_classifier_model = app.classifier_model

        try:
            # Mock binary model to return 0.04 (CIVIC_ISSUE with 96% confidence)
            class MockBinaryModel:
                def predict(self, array, verbose=0):
                    return np.array([[0.04]], dtype=np.float32)

            # Mock classifier to predict Potholes and RoadCracks (class index 7)
            class MockClassifierModel:
                def predict(self, array, verbose=0):
                    probs = np.array([0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.93], dtype=np.float32)
                    return np.array([probs])

            app.binary_model = MockBinaryModel()
            app.classifier_model = MockClassifierModel()

            img_bytes = self.create_test_image_bytes(color=(40, 40, 40))
            data = {"file": (img_bytes, "mock_pothole.jpg")}
            response = self.client.post("/predict", data=data, content_type="multipart/form-data")

            self.assertEqual(response.status_code, 200)
            res = response.get_json()
            self.assertTrue(res["success"])
            self.assertTrue(res["isCivicIssue"])
            self.assertEqual(res["classificationStatus"], "CLASSIFIED")
            self.assertEqual(res["stage"], "EIGHT_CLASS_CLASSIFICATION")
            self.assertEqual(res["binaryPrediction"], "CIVIC_ISSUE")
            self.assertLess(res["notCivicProbability"], 0.50)
            self.assertGreater(res["civicProbability"], 0.50)
            self.assertEqual(res["predictedClass"], "Potholes and RoadCracks")
            self.assertAlmostEqual(res["confidence"], 0.93, places=2)
            self.assertEqual(len(res["probabilities"]), 8)
            print("[TEST 05 PASSED] Stage 1 pass allowed Stage 2 to classify civic issue.")
        finally:
            app.binary_model = original_binary_model
            app.classifier_model = original_classifier_model

    def test_06_real_model_inference_with_actual_image(self):
        """
        Run actual end-to-end inference using real trained weights on a real uploaded image.
        """
        # Look for an existing uploaded complaint image
        uploads_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "Urban-Pulse", "uploads"))
        if os.path.exists(uploads_dir):
            jpgs = [os.path.join(uploads_dir, f) for f in os.listdir(uploads_dir) if f.lower().endswith((".jpg", ".png"))]
            if jpgs:
                sample_path = jpgs[0]
                with open(sample_path, "rb") as f:
                    file_bytes = f.read()

                response = self.client.post(
                    "/predict",
                    data={"file": (io.BytesIO(file_bytes), os.path.basename(sample_path))},
                    content_type="multipart/form-data"
                )
                self.assertEqual(response.status_code, 200)
                res = response.get_json()
                self.assertIn("isCivicIssue", res)
                self.assertIn("binaryPrediction", res)
                self.assertIn("classificationStatus", res)
                print(f"[TEST 06 PASSED] Real image inference completed: isCivicIssue={res['isCivicIssue']}, stage={res['stage']}, status={res['classificationStatus']}")

if __name__ == "__main__":
    unittest.main()

