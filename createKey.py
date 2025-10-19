import json
import os
import sys
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
from base64 import urlsafe_b64encode


def byte_array_to_hex(byte_array):
    return byte_array.hex()


def base64url_encode(data):
    return urlsafe_b64encode(data).rstrip(b'=').decode('utf-8')


def ec_key_to_jwk(private_key, key_id):
    numbers = private_key.private_numbers()
    public_numbers = numbers.public_numbers

    x = public_numbers.x.to_bytes(32, byteorder='big')
    y = public_numbers.y.to_bytes(32, byteorder='big')
    d = numbers.private_value.to_bytes(32, byteorder='big')

    public_jwk = {
        "kty": "EC",
        "crv": "P-256",
        "x": base64url_encode(x),
        "y": base64url_encode(y),
        "use": "sig",
        "kid": key_id
    }

    private_jwk = {
        **public_jwk,
        "d": base64url_encode(d)
    }

    return private_jwk, public_jwk


def main(key_id, output_dir):
    os.makedirs(output_dir, exist_ok=True)

    print((2 ** 64 - 1))  # Equivalent of BigDecimal.valueOf(2).pow(64).subtract(BigDecimal.ONE)

    # Generate EC key pair
    private_key = ec.generate_private_key(ec.SECP256R1(), default_backend())

    # Convert keys to JWK
    private_jwk, public_jwk = ec_key_to_jwk(private_key, key_id)

    # Save to files
    priv_file_path = os.path.join(output_dir, f"{key_id}.key")
    pub_file_path = os.path.join(output_dir, f"{key_id}.pub")

    with open(priv_file_path, 'w') as priv_file:
        json.dump(private_jwk, priv_file, indent=2)

    with open(pub_file_path, 'w') as pub_file:
        json.dump(public_jwk, pub_file, indent=2)

    print(f"Private key saved to {priv_file_path}")
    print(f"Public key saved to {pub_file_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python manual_key_build.py <keyID> <output_directory>")
        sys.exit(1)

    key_id_arg = sys.argv[1]
    output_dir_arg = sys.argv[2]
    main(key_id_arg, output_dir_arg)
