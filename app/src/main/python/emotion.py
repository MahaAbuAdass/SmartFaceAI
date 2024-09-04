import cv2
import numpy as np
import face_recognition
import pickle
from datetime import datetime
import os
import json
from typing import Optional, Dict, Any
import time

def load_known_faces(face_data_file_path: str):
    """Load known faces, their names, and IDs from the face_data file."""
    if os.path.exists(face_data_file_path):
        try:
            with open(face_data_file_path, 'rb') as file:
                face_data = pickle.load(file)
                known_face_encodings = face_data['encodings']
                known_face_names = face_data['names']
                known_face_ids = face_data['ids']
                print(f"Loaded {len(known_face_encodings)} encodings from face_data.pkl.")
        except Exception as e:
            print(f"Failed to load face data: {e}")
            known_face_encodings = []
            known_face_names = []
            known_face_ids = []
    else:
        print(f"Face data file not found at: {face_data_file_path}")
        known_face_encodings = []
        known_face_names = []
        known_face_ids = []

    return known_face_encodings, known_face_names, known_face_ids

def to_dict(status: str, message: Optional[str] = None, attendance_time: Optional[str] = None,
            light_threshold: Optional[float] = None, recognition_threshold: Optional[float] = None,
            liveness_variance: Optional[float] = None, id: Optional[int] = None) -> Dict[str, Any]:
    """Convert the result to a dictionary."""
    return {
        "status": status,
        "message": message,
        "attendance_time": attendance_time,
        "light_threshold": light_threshold if light_threshold is not None else 0.0,
        "recognition_threshold": recognition_threshold if recognition_threshold is not None else 0.0,
        "liveness_variance": liveness_variance if liveness_variance is not None else 0.0,
        "id": id if id is not None else None
    }

def to_json(status: str, message: Optional[str] = None, attendance_time: Optional[str] = None,
            light_threshold: Optional[float] = None, recognition_threshold: Optional[float] = None,
            liveness_variance: Optional[float] = None, id: Optional[int] = None) -> str:
    """Convert the result to a JSON string."""
    return json.dumps(to_dict(status, message, attendance_time, light_threshold, recognition_threshold,
                              liveness_variance, id))

def liveness_check(face_image) -> float:
    """Basic liveness check based on color variance (simple heuristic)."""
    gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
    variance = cv2.Laplacian(gray, cv2.CV_64F).var()
    return variance

def calculate_brightness(image) -> float:
    """Calculate the brightness of an image."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return np.mean(gray)

def estimate_face_angle(face_landmarks) -> float:
    """Estimate the angle of the face based on landmarks."""
    if not face_landmarks:
        return 0.0

    # Use the landmarks to approximate the angle of the face
    left_eye = face_landmarks['left_eye'][0]
    right_eye = face_landmarks['right_eye'][0]

    # Compute the angle between the eyes
    dx = right_eye[0] - left_eye[0]
    dy = right_eye[1] - left_eye[1]
    angle = np.arctan2(dy, dx) * 180 / np.pi

    return angle

def process_image(image_path: str, face_data_file_path: str, liveness_threshold) -> str:
    if liveness_threshold is None:
        liveness_threshold = 0  # Provide a default value
    """Process the image to recognize faces and return attendance time with liveness variance included."""
    start_time = time.time()  # Record the start time

    print(f"Processing image at path: {image_path}")
    print(f"Using face data file at path: {face_data_file_path}")

    # Initialize response variables
    status = "error"
    message = None
    attendance_time = None
    light_threshold = 0.0  # This will store the brightness of the image
    recognition_threshold = 1.0  # Will be updated with the best match accuracy

    recognized_id = None  # ID of the recognized face
    liveness_variance = 0.0

    if not os.path.exists(image_path):
        message = "Image file not found"
        print(message)
    elif not os.path.exists(face_data_file_path):
        message = "Face data file not found"
        print(message)
    else:
        frame = cv2.imread(image_path)

        if frame is None:
            message = "Failed to load image"
            print(message)
        else:
            try:
                # Calculate the brightness of the image
                light_threshold = calculate_brightness(frame)

                # Set a threshold for minimum brightness
                min_brightness_threshold = 50.0  # You can adjust this value based on your needs

                if light_threshold < min_brightness_threshold:
                    message = "Please increase the light"
                    print(message)
                    status = "error"
                else:
                    max_angle_threshold = 15.0  # Maximum allowed angle for face recognition

                    known_face_encodings, known_face_names, known_face_ids = load_known_faces(face_data_file_path)

                    if not known_face_encodings or not known_face_names:
                        message = "No known faces in the system"
                        print(message)
                    else:
                        # Resize the image for faster processing
                        small_frame = cv2.resize(frame, (0, 0), fx=0.25, fy=0.25)

                        # Use the HOG model for faster face detection
                        face_locations = face_recognition.face_locations(small_frame, model="hog")
                        face_landmarks = face_recognition.face_landmarks(small_frame)
                        print(f"Detected face locations: {face_locations}")

                        if not face_locations:
                            message = "No face detected in the image"
                            print(message)
                        else:
                            for (top, right, bottom, left), face_encoding, landmarks in zip(
                                    face_locations,
                                    face_recognition.face_encodings(small_frame, face_locations),
                                    face_landmarks):
                                # Rescale face location
                                top *= 4
                                right *= 4
                                bottom *= 4
                                left *= 4

                                # Extract the upper part of the face (eyes and forehead) for recognition
                                upper_face = frame[top:top + int((bottom - top) / 2), left:right]

                                # Always calculate liveness variance
                                liveness_variance = liveness_check(upper_face)
                                print(f"Liveness check variance: {liveness_variance}")

                                if liveness_variance <= liveness_threshold:
                                    status = "error"
                                    message = "liveness check: Fake face detected"
                                    print(message)
                                    break
                                # Estimate face angle and check if it's within the acceptable range
                                angle = estimate_face_angle(landmarks)
                                print(f"Face angle: {angle} degrees")

                                if abs(angle) > max_angle_threshold:
                                    message = "Face angle is too extreme for recognition"
                                    print(message)
                                    status = "error"
                                    break  # Stop further processing if angle is not acceptable

                                # Proceed with face recognition if angle is okay
                                matches = face_recognition.compare_faces(known_face_encodings, face_encoding)
                                distances = face_recognition.face_distance(known_face_encodings, face_encoding)

                                if any(matches):
                                    best_match_index = np.argmin(distances)
                                    best_match_name = known_face_names[best_match_index]
                                    best_match_id = known_face_ids[best_match_index]
                                    best_match_accuracy = distances[best_match_index]

                                    recognition_threshold = best_match_accuracy  # Update recognition threshold

                                    if best_match_accuracy < 0.45:  # Adjusted threshold for masked face recognition
                                        recognized_id = best_match_id  # Set the recognized ID
                                        # Get current time (hours:minutes:seconds)
                                        current_time = datetime.now().strftime("%H:%M:%S")
                                        attendance_time = current_time
                                        hour = int(datetime.now().strftime("%H"))

                                        if hour < 12:
                                            message = f"Good Morning {best_match_name}"
                                        else:
                                            message = f"Good Evening {best_match_name}"

                                        print(f"Time Attendance: {attendance_time}")
                                        print(f"Match found: {best_match_name} with accuracy {(1 - best_match_accuracy) * 100}%")
                                        status = "success"
                                        break  # Exit the loop as we found a match
                                    else:
                                        message = "No Record Found"
                                        print(message)
                                        break
                                else:
                                    message = "No Record Found, Please try again"
                                    print(message)
                                    break

            except Exception as e:
                message = f"Exception occurred: {str(e)}"
                print(message)

    end_time = time.time()  # Record the end time
    processing_time = end_time - start_time  # Calculate the processing time
    print(f"Image processed in {processing_time:.2f} seconds.")

    # Convert result to JSON and return
    return to_json(status=status, message=message, attendance_time=attendance_time,
                   light_threshold=light_threshold, recognition_threshold=recognition_threshold,
                   liveness_variance=liveness_variance, id=recognized_id)
