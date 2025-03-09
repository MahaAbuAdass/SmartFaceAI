import os
import face_recognition
import pickle
import json
import numpy as np
import cv2
import time
from typing import Optional, Dict, Any

def load_face_data(face_data_file):
    """Load face data containing encodings, names, and IDs."""
    if os.path.exists(face_data_file) and os.path.getsize(face_data_file) > 0:
        with open(face_data_file, 'rb') as file:
            return pickle.load(file)
    else:
        return {"encodings": np.array([]), "names": [], "ids": []}

def save_face_data_to_file(face_data, face_data_file):
    """Save face data to the file."""
    with open(face_data_file, 'wb') as file:
        pickle.dump(face_data, file)

def preprocess_image(image_path):
    """Load and resize the image for faster encoding."""
    image = cv2.imread(image_path)
    small_image = cv2.resize(image, (0, 0), fx=0.5, fy=0.5)  # Resize by 50%
    rgb_small_image = cv2.cvtColor(small_image, cv2.COLOR_BGR2RGB)
    return rgb_small_image

def IDcheck(face_data_file, user_id , image_path):
    """
    Check if the given user ID already exists in the face data.
    Returns a JSON response indicating the result.
    """
    face_data = load_face_data(face_data_file)

    if user_id in face_data['ids']:
        response = {
            "message": f"User ID {user_id} already exists."
        }

    else:
        response = update_face_encodings(image_path, face_data_file, user_id)

    return json.dumps(response)


def update_face_encodings(new_photo_path, face_data_file, user_id=None):
    response = {'message': None}
    start_time = time.time()  # Start timing

    if user_id is None:
        response['message'] = "Please enter a valid user ID."
        return json.dumps(response)

    # Load face data
    face_data = load_face_data(face_data_file)
    print(f"Loaded {len(face_data['encodings'])} faces.")

    if not os.path.isfile(new_photo_path) or os.path.getsize(new_photo_path) == 0:
        response['message'] = f"File not found or empty: {new_photo_path}"
        return json.dumps(response)

    # Preprocess and encode the image
    rgb_small_image = preprocess_image(new_photo_path)
    encode_start = time.time()
    encodings = face_recognition.face_encodings(rgb_small_image)
    encode_end = time.time()
    print(f"Encoding time: {encode_end - encode_start:.2f} seconds")

    if not encodings:
        response['message'] = "No faces found in the image."
        return json.dumps(response)

    new_encoding = encodings[0]

    # Check if the user ID exists
    if user_id in face_data['ids']:
        idx = face_data['ids'].index(user_id)
        existing_encoding = face_data['encodings'][idx]

        # Compare the new encoding with the existing encoding for the user ID
        distance = np.linalg.norm(existing_encoding - new_encoding)
        print(f"Distances: {distance}")
        accuracy = 1 - distance

        if accuracy >= 0.6:
            # Update the encoding for the existing user ID
            face_data['encodings'][idx] = new_encoding
            face_data['names'][idx] = os.path.splitext(os.path.basename(new_photo_path))[0]
            response['message'] = f"Updated face for user ID {user_id}."
        else:
            response['message'] = f"The new photo does not match the face for user ID {user_id}."
    else:
        # Check if the face is already enrolled under another ID
        if len(face_data['encodings']) > 0:
            distance_start = time.time()
            distances = np.linalg.norm(face_data['encodings'] - new_encoding, axis=1)
            distance_end = time.time()
            print(f"Distance calculation time: {distance_end - distance_start:.2f} seconds")

            # Use the minimum distance for accuracy calculation
            min_distance = np.min(distances)
            print(f"Distances: {min_distance}")
            accuracy = 1 - min_distance  # Calculate accuracy based on the minimum distance

            if accuracy > 0.55:  # Threshold for matching
                matched_id = face_data['ids'][np.argmin(distances)]
                response['message'] = f"The face is already enrolled under another ID: {matched_id}."
                return json.dumps(response)


        # Add as a new user
        if len(face_data['encodings']) == 0:
            face_data['encodings'] = np.array([new_encoding])
        else:
            face_data['encodings'] = np.append(face_data['encodings'], [new_encoding], axis=0)
        face_data['names'].append(os.path.splitext(os.path.basename(new_photo_path))[0])
        face_data['ids'].append(user_id)
        response['message'] = f"Added new user with ID {user_id}."

    # Save updated face data to file
    save_start = time.time()
    save_face_data_to_file(face_data, face_data_file)
    save_end = time.time()
    print(f"Save time: {save_end - save_start:.2f} seconds")

    end_time = time.time()
    print(f"Total processing time: {end_time - start_time:.2f} seconds")

    return json.dumps(response)



# Assuming `face_data.pkl` is your data file
#face_data_file = "face_data.pkl"
#new_photo_path = r"C:\Users\osama\Pictures\2.png"
#user_id = 10001  # The ID to update or add

# Call the function
#result = update_face_encodings(new_photo_path, face_data_file, user_id=user_id)
#print(result)