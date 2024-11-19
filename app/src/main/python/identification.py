import os
import time
import pickle
import json
import numpy as np
import cv2
import face_recognition
from datetime import datetime
from typing import Optional, Dict, Any, Tuple
import logging
from concurrent.futures import ThreadPoolExecutor

# Configure logging
logging.basicConfig(level=logging.INFO)

def load_known_faces(face_data_file_path: str) -> Tuple[np.ndarray, list, list, float]:
    start_time = time.time()
    known_face_encodings, known_face_names, known_face_ids = np.array([]), [], []

    if os.path.exists(face_data_file_path):
        try:
            with open(face_data_file_path, 'rb') as file:
                face_data = pickle.load(file)
                known_face_encodings = np.array(face_data['encodings'])
                known_face_names = face_data['names']
                known_face_ids = face_data['ids']
                logging.info(f"Loaded {len(known_face_encodings)} encodings from face_data.pkl.")
        except Exception as e:
            logging.error(f"Failed to load face data: {e}")
    else:
        logging.warning(f"Face data file not found at: {face_data_file_path}")

    end_time = time.time()
    loading_time = end_time - start_time
    return known_face_encodings, known_face_names, known_face_ids, loading_time

def liveness_check(face_image) -> float:
    gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
    variance = cv2.Laplacian(gray, cv2.CV_64F).var()
    return variance

def calculate_brightness(image) -> float:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return np.mean(gray)

def to_dict(status: str, message: Optional[str] = None, attendance_time: Optional[str] = None,
            light_threshold: Optional[float] = None, recognition_threshold: Optional[float] = None,
            liveness_variance: Optional[float] = None, id: Optional[int] = None,
            processing_time: Optional[float] = None, brightness_time: Optional[float] = None,
            loading_time: Optional[float] = None, detection_time: Optional[float] = None,
            encoding_time: Optional[float] = None, liveness_time: Optional[float] = None,
            comparison_time: Optional[float] = None, resized_frame_size: Optional[Dict[str, int]] = None) -> Dict[str, Any]:
    """Convert the result to a dictionary."""
    return {
        "status": status,
        "message": message,
        "attendance_time": attendance_time,
        "light_threshold": light_threshold if light_threshold is not None else 0.0,
        "recognition_threshold": recognition_threshold if recognition_threshold is not None else 0.0,
        "liveness_variance": liveness_variance if liveness_variance is not None else 0.0,
        "id": id if id is not None else None,
        "processing_time": processing_time if processing_time is not None else 0.0,
        "brightness_time": brightness_time,
        "loading_time": loading_time,
        "detection_time": detection_time,
        "encoding_time": encoding_time,
        "liveness_time": liveness_time,
        "comparison_time": comparison_time,
        "resized_frame_size": resized_frame_size if resized_frame_size is not None else {"width": 0, "height": 0}
    }

def to_json(**kwargs) -> str:
    """Convert the result to a JSON string."""
    return json.dumps(to_dict(**kwargs))

def process_image(image_path: str, face_data_file_path: str, liveness_threshold: Optional[float] = 0) -> str:
    """Process the image to recognize faces and return attendance time with liveness variance included."""
    status, message, attendance_time, recognized_id, liveness_variance = "error", None, None, None, 0.0
    processing_time, resized_frame_size, brightness_time, loading_time, detection_time = 0.0, None, 0.0, 0.0, 0.0
    encoding_time, liveness_time, comparison_time = 0.0, 0.0, 0.0
    light_threshold = 0.0
    recognition_threshold = 1.0

    if liveness_threshold is None:
        liveness_threshold = 0

    if not os.path.exists(image_path):
        message = "Image file not found"
        logging.error(message)
    elif not os.path.exists(face_data_file_path):
        message = "Face data file not found"
        logging.error(message)
    else:
        frame = cv2.imread(image_path)
        if frame is None:
            message = "Failed to load image"
            logging.error(message)
        else:
            start_time = time.time()  # Record the start time of image processing
            try:
                # Measure brightness calculation time in a separate thread
                with ThreadPoolExecutor() as executor:
                    brightness_future = executor.submit(calculate_brightness, frame)
                    light_threshold = brightness_future.result()

                min_brightness_threshold = 50.0
                if light_threshold < min_brightness_threshold:
                    message = "Please increase the light"
                    status = "error"
                else:
                    # Measure face data loading time
                    known_face_encodings, known_face_names, known_face_ids, loading_time = load_known_faces(face_data_file_path)

                    if not known_face_encodings.size or not known_face_names:
                        message = "No known faces in the system"
                        logging.error(message)
                    else:
                        # Measure face detection time
                        small_frame = cv2.resize(frame, (0, 0), fx=0.25, fy=0.25)
                        detection_start_time = time.time()
                        resized_frame_size = small_frame.shape
                        face_locations = face_recognition.face_locations(small_frame)
                        detection_end_time = time.time()
                        detection_time = detection_end_time - detection_start_time
                        logging.info(f"Face detection time: {detection_time:.2f} seconds.")

                        if not face_locations:
                            message = "No face detected in the image"
                            logging.error(message)
                        else:
                            # Measure face encoding time
                            encoding_start_time = time.time()
                            face_encodings = face_recognition.face_encodings(small_frame, face_locations)
                            encoding_end_time = time.time()
                            encoding_time = encoding_end_time - encoding_start_time
                            logging.info(f"Face encoding time: {encoding_time:.2f} seconds.")

                            for face_encoding, (top, right, bottom, left) in zip(face_encodings, face_locations):
                                top *= 4
                                right *= 4
                                bottom *= 4
                                left *= 4

                                upper_face = frame[top:top + int((bottom - top) / 2), left:right]

                                # Measure liveness check time in a separate thread
                                with ThreadPoolExecutor() as executor:
                                    liveness_future = executor.submit(liveness_check, upper_face)
                                    liveness_variance = liveness_future.result()

                                if liveness_variance < liveness_threshold:
                                    logging.error("Fake Face Detected!")
                                    message = "Fake Face Detected!"
                                    status = 'error'
                                    break

                                # Measure face comparison time
                                comparison_start_time = time.time()
                                distances = face_recognition.face_distance(known_face_encodings, face_encoding)
                                comparison_end_time = time.time()
                                comparison_time = comparison_end_time - comparison_start_time
                                logging.info(f"Face comparison time: {comparison_time:.2f} seconds.")

                                if distances.size > 0:
                                    best_match_index = np.argmin(distances)
                                    best_match_name = known_face_names[best_match_index]
                                    best_match_id = known_face_ids[best_match_index]
                                    best_match_accuracy = distances[best_match_index]

                                    recognition_threshold = best_match_accuracy

                                    if best_match_accuracy < 0.45:
                                        recognized_id = best_match_id
                                        current_time = datetime.now().strftime("%H:%M:%S")
                                        attendance_time = current_time
                                        hour = int(datetime.now().strftime("%H"))

                                        message = f"Good Morning {best_match_name}" if hour < 12 else f"Good Evening {best_match_name}"
                                        status = "success"
                                    else:
                                        message = "Face recognition failed"
                                        status = "error"

            except Exception as e:
                logging.error(f"Error during face processing: {e}")
                message = f"Error: {str(e)}"
                status = "error"

            # Calculate processing time
            processing_time = time.time() - start_time
            result_json = to_json(status=status, message=message, attendance_time=attendance_time,
                                  light_threshold=light_threshold, recognition_threshold=recognition_threshold,
                                  liveness_variance=liveness_variance, id=recognized_id,
                                  processing_time=processing_time, brightness_time=brightness_time,
                                  loading_time=loading_time, detection_time=detection_time, encoding_time=encoding_time,
                                  liveness_time=liveness_time, comparison_time=comparison_time,
                                  resized_frame_size=resized_frame_size)
            return result_json
