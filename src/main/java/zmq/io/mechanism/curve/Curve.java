package zmq.io.mechanism.curve;

import java.nio.ByteBuffer;

import org.abstractj.kalium.NaCl;
import org.abstractj.kalium.NaCl.Sodium;

import zmq.util.Utils;
import zmq.util.Z85;

// wrapper around the wrapper of libsodium (ahem), for shorter names.
public class Curve
{
    static enum Size
    {
        NONCE {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_BOX_CURVE25519XSALSA20POLY1305_NONCEBYTES;
            }
        },
        ZERO {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_BOX_CURVE25519XSALSA20POLY1305_ZEROBYTES;
            }
        },
        BOXZERO {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_BOX_CURVE25519XSALSA20POLY1305_BOXZEROBYTES;
            }
        },
        PUBLICKEY {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_BOX_CURVE25519XSALSA20POLY1305_PUBLICKEYBYTES;
            }
        },
        SECRETKEY {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_BOX_CURVE25519XSALSA20POLY1305_SECRETKEYBYTES;
            }
        },
        KEY {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_SECRETBOX_XSALSA20POLY1305_KEYBYTES;
            }
        },
        BEFORENM {
            @Override
            public int bytes()
            {
                return NaCl.Sodium.CRYPTO_BOX_CURVE25519XSALSA20POLY1305_BEFORENMBYTES;
            }
        };

        public abstract int bytes();
    }

    public static boolean installed()
    {
        try {
            int init = NaCl.init();
            return init >= 0;
        }
        catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public Curve()
    {
        boolean installed = installed();
        if (!installed) {
            throw new UnsupportedOperationException("libsodium does not look lie it is installed.");
        }
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

        int rc = salt().crypto_box_curve25519xsalsa20poly1305_keypair(publicKey, secretKey);
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

        int rc = salt().crypto_box_curve25519xsalsa20poly1305_keypair(publicKey, secretKey);
        assert (rc == 0);

        pair[0] = publicKey;
        pair[1] = secretKey;

        return pair;
    }

    int beforenm(byte[] outSharedKey, byte[] publicKey, byte[] secretKey)
    {
        return salt().crypto_box_curve25519xsalsa20poly1305_beforenm(outSharedKey, publicKey, secretKey);
    }

    int afternm(ByteBuffer ciphered, ByteBuffer plaintext, int length, ByteBuffer nonce, byte[] precom)
    {
        return afternm(ciphered.array(), plaintext.array(), length, nonce.array(), precom);
    }

    int afternm(byte[] ciphered, byte[] plaintext, int length, byte[] nonce, byte[] precomp)
    {
        return salt().crypto_box_curve25519xsalsa20poly1305_afternm(ciphered, plaintext, length, nonce, precomp);
    }

    int openAfternm(ByteBuffer plaintext, ByteBuffer messagebox, int length, ByteBuffer nonce, byte[] precom)
    {
        return openAfternm(plaintext.array(), messagebox.array(), length, nonce.array(), precom);
    }

    int openAfternm(byte[] plaintext, byte[] cipher, int length, byte[] nonce, byte[] precom)
    {
        return salt().crypto_box_curve25519xsalsa20poly1305_open_afternm(plaintext, cipher, length, nonce, precom);
    }

    private Sodium salt()
    {
        return NaCl.sodium();
    }

    int open(ByteBuffer plaintext, ByteBuffer messagebox, int length, ByteBuffer nonce, byte[] precom, byte[] secretKey)
    {
        return open(plaintext.array(), messagebox.array(), length, nonce.array(), precom, secretKey);
    }

    int open(byte[] plaintext, byte[] messagebox, int length, byte[] nonce, byte[] publicKey, byte[] secretKey)
    {
        return salt()
                .crypto_box_curve25519xsalsa20poly1305_open(plaintext, messagebox, length, nonce, publicKey, secretKey);
    }

    int secretbox(ByteBuffer ciphertext, ByteBuffer plaintext, int length, ByteBuffer nonce, byte[] key)
    {
        return secretbox(ciphertext.array(), plaintext.array(), length, nonce.array(), key);
    }

    int secretbox(byte[] ciphertext, byte[] plaintext, int length, byte[] nonce, byte[] key)
    {
        return salt().crypto_secretbox_xsalsa20poly1305(ciphertext, plaintext, length, nonce, key);
    }

    int secretboxOpen(ByteBuffer plaintext, ByteBuffer box, int length, ByteBuffer nonce, byte[] key)
    {
        return secretboxOpen(plaintext.array(), box.array(), length, nonce.array(), key);
    }

    int secretboxOpen(byte[] plaintext, byte[] box, int length, byte[] nonce, byte[] key)
    {
        return salt().crypto_secretbox_xsalsa20poly1305_open(plaintext, box, length, nonce, key);
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
        return salt().crypto_box_curve25519xsalsa20poly1305(ciphertext, plaintext, length, nonce, publicKey, secretKey);
    }
}
