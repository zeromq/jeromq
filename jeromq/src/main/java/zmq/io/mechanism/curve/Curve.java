package zmq.io.mechanism.curve;

import java.nio.ByteBuffer;

import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;
import com.neilalexander.jnacl.crypto.xsalsa20poly1305;

import zmq.util.Utils;
import zmq.util.Z85;

// wrapper around the wrapper of libsodium (ahem), for shorter names.
public class Curve
{
    enum Size
    {
        NONCE {
            @Override
            public int bytes()
            {
                return curve25519xsalsa20poly1305.crypto_secretbox_NONCEBYTES;
            }
        },
        ZERO {
            @Override
            public int bytes()
            {
                return curve25519xsalsa20poly1305.crypto_secretbox_ZEROBYTES;
            }
        },
        BOXZERO {
            @Override
            public int bytes()
            {
                return curve25519xsalsa20poly1305.crypto_secretbox_BOXZEROBYTES;
            }
        },
        PUBLICKEY {
            @Override
            public int bytes()
            {
                return curve25519xsalsa20poly1305.crypto_secretbox_PUBLICKEYBYTES;
            }
        },
        SECRETKEY {
            @Override
            public int bytes()
            {
                return curve25519xsalsa20poly1305.crypto_secretbox_SECRETKEYBYTES;
            }
        },
        KEY {
            @Override
            public int bytes()
            {
                return 32;
            }
        },
        BEFORENM {
            @Override
            public int bytes()
            {
                return curve25519xsalsa20poly1305.crypto_secretbox_BEFORENMBYTES;
            }
        };

        public abstract int bytes();
    }

    public Curve()
    {
    }

    public static String z85EncodePublic(byte[] publicKey)
    {
        return Z85.encode(publicKey, Size.PUBLICKEY.bytes());
    }

    /**
     * Generates a pair of Z85-encoded keys for use with this class.
     *
     * @return an array of 2 strings, holding Z85-encoded keys.
     * The first element of the array is the public key,
     * the second element is the private (or secret) key.
     */
    public String[] keypairZ85()
    {
        String[] pair = new String[2];

        byte[] publicKey = new byte[Size.PUBLICKEY.bytes()];
        byte[] secretKey = new byte[Size.SECRETKEY.bytes()];

        int rc = curve25519xsalsa20poly1305.crypto_box_keypair(publicKey, secretKey);
        assert (rc == 0);

        pair[0] = Z85.encode(publicKey, Size.PUBLICKEY.bytes());
        pair[1] = Z85.encode(secretKey, Size.SECRETKEY.bytes());

        return pair;
    }

    /**
     * Generates a pair of keys for use with this class.
     *
     * @return an array of 2 byte arrays, holding keys.
     * The first element of the array is the public key,
     * the second element is the private (or secret) key.
     */
    public byte[][] keypair()
    {
        byte[][] pair = new byte[2][];

        byte[] publicKey = new byte[Size.PUBLICKEY.bytes()];
        byte[] secretKey = new byte[Size.SECRETKEY.bytes()];

        int rc = curve25519xsalsa20poly1305.crypto_box_keypair(publicKey, secretKey);
        assert (rc == 0);

        pair[0] = publicKey;
        pair[1] = secretKey;

        return pair;
    }

    int beforenm(byte[] outSharedKey, byte[] publicKey, byte[] secretKey)
    {
        return curve25519xsalsa20poly1305.crypto_box_beforenm(outSharedKey, publicKey, secretKey);
    }

    int afternm(ByteBuffer ciphered, ByteBuffer plaintext, int length, ByteBuffer nonce, byte[] precom)
    {
        return afternm(ciphered.array(), plaintext.array(), length, nonce.array(), precom);
    }

    int afternm(byte[] ciphered, byte[] plaintext, int length, byte[] nonce, byte[] precomp)
    {
        return curve25519xsalsa20poly1305.crypto_box_afternm(ciphered, plaintext, length, nonce, precomp);
    }

    int openAfternm(ByteBuffer plaintext, ByteBuffer messagebox, int length, ByteBuffer nonce, byte[] precom)
    {
        return openAfternm(plaintext.array(), messagebox.array(), length, nonce.array(), precom);
    }

    int openAfternm(byte[] plaintext, byte[] cipher, int length, byte[] nonce, byte[] precom)
    {
        return curve25519xsalsa20poly1305.crypto_box_open_afternm(plaintext, cipher, length, nonce, precom);
    }

    int open(ByteBuffer plaintext, ByteBuffer messagebox, int length, ByteBuffer nonce, byte[] precom, byte[] secretKey)
    {
        return open(plaintext.array(), messagebox.array(), length, nonce.array(), precom, secretKey);
    }

    int open(byte[] plaintext, byte[] messagebox, int length, byte[] nonce, byte[] publicKey, byte[] secretKey)
    {
        return curve25519xsalsa20poly1305.crypto_box_open(plaintext, messagebox, length, nonce, publicKey, secretKey);
    }

    int secretbox(ByteBuffer ciphertext, ByteBuffer plaintext, int length, ByteBuffer nonce, byte[] key)
    {
        return secretbox(ciphertext.array(), plaintext.array(), length, nonce.array(), key);
    }

    int secretbox(byte[] ciphertext, byte[] plaintext, int length, byte[] nonce, byte[] key)
    {
        return xsalsa20poly1305.crypto_secretbox(ciphertext, plaintext, length, nonce, key);
    }

    int secretboxOpen(ByteBuffer plaintext, ByteBuffer box, int length, ByteBuffer nonce, byte[] key)
    {
        return secretboxOpen(plaintext.array(), box.array(), length, nonce.array(), key);
    }

    int secretboxOpen(byte[] plaintext, byte[] box, int length, byte[] nonce, byte[] key)
    {
        return xsalsa20poly1305.crypto_secretbox_open(plaintext, box, length, nonce, key);
    }

    byte[] random(int length)
    {
        return Utils.randomBytes(length);
    }

    public int box(ByteBuffer ciphertext, ByteBuffer plaintext, int length, ByteBuffer nonce, byte[] publicKey,
                   byte[] secretKey)
    {
        return box(ciphertext.array(), plaintext.array(), length, nonce.array(), publicKey, secretKey);
    }

    public int box(byte[] ciphertext, byte[] plaintext, int length, byte[] nonce, byte[] publicKey, byte[] secretKey)
    {
        return curve25519xsalsa20poly1305.crypto_box(ciphertext, plaintext, length, nonce, publicKey, secretKey);
    }
}
