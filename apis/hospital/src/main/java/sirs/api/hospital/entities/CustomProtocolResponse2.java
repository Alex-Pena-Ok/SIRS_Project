package sirs.api.hospital.entities;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

public class CustomProtocolResponse2 {


    /**
     *
     * String data:
     *      This string is a result of using mapper on , transforming that object into a string.
     *      It is encrypted in base64, after applying macs algorithm on it.
     *
     **/
    private String mac;


    public CustomProtocolResponse2(String mac) {
        this.mac = mac;
    }

    public String getMac() {
        return mac;
    }

    public HandshakeResponse getHandshakeResponse() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        byte[] decodedMacBytes = Base64.getDecoder().decode(mac);
        byte[] handshakeResponse = Arrays.copyOfRange(decodedMacBytes, 0, decodedMacBytes.length - 32);

        return mapper.readValue(handshakeResponse, HandshakeResponse.class);
    }

}
