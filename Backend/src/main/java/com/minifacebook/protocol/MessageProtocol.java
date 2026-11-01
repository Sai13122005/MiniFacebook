
package com.minifacebook.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MessageProtocol {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Maximum message size: 1 MB
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024;

    // Send a JSON message with a 4-byte length prefix
    public static void writeMessage(OutputStream output, JsonNode message)
            throws IOException {

        byte[] data = mapper.writeValueAsBytes(message);

        if (data.length > MAX_MESSAGE_SIZE) {
            throw new IOException("Message exceeds maximum size");
        }

        DataOutputStream dos = new DataOutputStream(output);

        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }

    // Read a JSON message using its length prefix
    public static JsonNode readMessage(InputStream input)
            throws IOException {

        DataInputStream dis = new DataInputStream(input);

        int length;

        try {
            length = dis.readInt();
        } catch (java.io.EOFException e) {
            return null;
        }

        if (length <= 0 || length > MAX_MESSAGE_SIZE) {
            throw new IOException("Invalid message length: " + length);
        }

        byte[] data = dis.readNBytes(length);

        if (data.length != length) {
            throw new IOException("Incomplete message received");
        }

        return mapper.readTree(data);
    }
}
