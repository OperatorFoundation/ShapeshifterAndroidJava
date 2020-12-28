package org.operatorfoundation.shapeshifter.shadow.java;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

// This abstract class is the superclass of all classes representing an input stream of bytes.
public class ShadowInputStream extends InputStream {
    final InputStream networkInputStream;
    final ShadowCipher decryptionCipher;
    byte[] buffer = new byte[ShadowCipher.maxPayloadSize];

    // Applications that need to define a subclass of InputStream must always provide a method that returns the next byte of input.
    public ShadowInputStream(InputStream networkInputStream, ShadowCipher decryptionCipher) {
        this.networkInputStream = networkInputStream;
        this.decryptionCipher = decryptionCipher;
    }

    @Override
    public void close() throws IOException {
        networkInputStream.close();
    }

    // Reads some number of bytes from the input stream and stores them into the buffer array b.
    @Override
    public int read(byte[] b) throws IOException {
        if (b == null || b.length == 0) {
            return 0;
        }

        // puts the bytes in a buffer
        if (b.length <= buffer.length) {
            int resultSize = b.length;
            System.arraycopy(buffer, 0, b, 0, resultSize);
            buffer = Arrays.copyOfRange(buffer, resultSize, buffer.length);

            return resultSize;
        }


        //get encrypted length
        int lengthDataSize = ShadowCipher.lengthWithTagSize;

        // read bytes up to the size of encrypted lengthSize into a byte buffer
        byte[] encryptedLengthData = Utility.readNBytes(networkInputStream, lengthDataSize);
        if (encryptedLengthData == null) {
            return -1;
        }
        //decrypt encrypted length to find out payload length
        byte[] lengthData = new byte[0];
        try {
            lengthData = decryptionCipher.decrypt(encryptedLengthData);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        //change lengthData from BigEndian representation to in length
        byte leftByte = lengthData[0];
        byte rightByte = lengthData[1];
        int leftInt = (int) leftByte * 256;
        int rightInt = (int) rightByte;
        if (rightInt < 0) {
            rightInt += 256;
        }
//        long ULeftByte = Integer.toUnsignedLong((int) leftByte);
//        System.out.println((int) ULeftByte);
//        long URightByte = Integer.toUnsignedLong((int) rightByte);
//        System.out.println((int) URightByte);
        int payloadLength = (leftInt + rightInt);

        //read and decrypt payload with the resulting length
        byte[] encryptedPayload = Utility.readNBytes(networkInputStream, payloadLength + ShadowCipher.tagSize);
        if (encryptedPayload == null) {
            return -1;
        }
        byte[] payload = new byte[0];
        try {
            payload = decryptionCipher.decrypt(encryptedPayload);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        // put payload into buffer
        buffer = Utility.plusEqualsByteArray(buffer, payload);
        assert b != null;
        int resultSize = Integer.min(b.length, buffer.length);

        // take bytes out of buffer.
        System.arraycopy(buffer, 0, b, 0, resultSize);
        buffer = Arrays.copyOfRange(buffer, resultSize, buffer.length);

        return resultSize;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b != null && b.length != 0) {
            byte[] readbuf = new byte[len];
            int buflen = read(readbuf);
            System.arraycopy(readbuf, off, b, 0, buflen);
            return buflen;
        } else {
            return 0;
        }
    }

    // Reads the next byte of data from the input stream.
    @Override
    public int read() throws IOException {
        byte[] result = new byte[0];
        // read bytes up to payload length (4)
        read(result);
        return ByteBuffer.wrap(result).getInt();
    }
}

