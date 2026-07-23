import hashlib
from Crypto.Cipher import AES
import binascii


SIGNATURE_SHA256 = "B4 F0 17 16 97 DF DB E8 56 0C 2B 16 5F 59 B6 62 CB 67 19 75 52 DC A6 61 02 0B 76 A6 A3 93 BC 36"

def decrypt():
    print("Preparing to decrypt...")

    # 1. Clean the signature (remove spaces/colons and make lowercase)
    # The app code: String.format("%02x", ...) produces lowercase hex
    clean_sig = SIGNATURE_SHA256.replace(":", "").replace(" ", "").lower()

    # 2. Derive the key (First 16 chars of the hex string)
    # The app code: Arrays.copyOf(strValueOf.getBytes(), 16)
    key_string = clean_sig[:16]
    key_bytes = key_string.encode('utf-8')
    print(f"Key found: {key_string}")

    # 3. Setup AES (Mode: CBC, IV: 0000...)
    iv = b'\x00' * 16
    cipher = AES.new(key_bytes, AES.MODE_CBC, iv)

    # 4. Read the encrypted file
    try:
        with open("class.dex", "rb") as f:
            encrypted_data = f.read()
    except FileNotFoundError:
        print("ERROR: Could not find 'class.dex'. Run the curl command first!")
        return

    # 5. Decrypt
    try:
        decrypted_data = cipher.decrypt(encrypted_data)

        # Remove Padding (PKCS5)
        padding_len = decrypted_data[-1]
        decrypted_data = decrypted_data[:-padding_len]

        # 6. Save the result
        with open("decrypted.jar", "wb") as f:
            f.write(decrypted_data)

        print("\nSUCCESS! Created 'decrypted.jar'.")
        print("ACTION: Drag 'decrypted.jar' into JADX now!")

    except Exception as e:
        print(f"Decryption Failed: {e}")

if __name__ == "__main__":
    decrypt()
