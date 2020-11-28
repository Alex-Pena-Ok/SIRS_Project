package sirs.api.lab;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import sirs.api.lab.entities.CustomProtocolResponse;
import sirs.api.lab.entities.TestRequest;
import sirs.api.lab.entities.TestResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

import static java.nio.file.Files.readAllBytes;

@RestController
public class Handlers {
    Crypto cr = new Crypto();
    CustomProtocol cp = new CustomProtocol();

    @PostMapping("/teststoanalyze/{id}")
    public ResponseEntity<TestResponse> testsToAnalyze(@PathVariable int id, @RequestBody TestRequest testreq) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, CertificateException {
        // Because for simplicity reasons we only answer to requests with id 1
        // id is only for representation purposes in case this was a real system
        // we would have multiple id's...
        if(id != 1)
            return ResponseEntity.status(404).build();

        String certificate = testreq.getCertificate();
        System.out.println("Certificate received "+ certificate);

        //creates certificate from the TesteRequest
        byte [] decoded = Base64.decodeBase64(certificate.replaceAll("-----BEGIN CERTIFICATE-----\n", "").replaceAll("-----END CERTIFICATE-----", ""));
        Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(decoded));
        try {
           boolean valid= cp.verifyCertificate(cert,"src/main/resources/myCA.crt");
           System.out.println("Certificate is "+ valid);
        } catch (InvalidAlgorithmParameterException e) {
            System.out.println("Ups NOT WORKING");
        }


        //TODO: verify certificate

        // Generating random string
        byte[] randomString = new byte[64];
        new Random().nextBytes(randomString);

        // Extract pub key from certificate
        PublicKey pubKey = cp.extractPubKey(certificate);

        // Encrypt random string with pub key
        byte[] encrypted_data = cp.encryptData(randomString, pubKey);
        byte[] encrypted_base64 = Base64.encodeBase64(encrypted_data);

        String encrypted_string64 = new String(encrypted_base64);

        //TODO: send encrypted random string
        String results = "25/05/2020 Covid19:True,Pneumonia:True...";
        String signature = cr.signData(results);
        TestResponse resp = new TestResponse(results, signature, encrypted_string64);

//        if(signature.equals(""))
//            return ResponseEntity.status(500).build();


        return ResponseEntity.ok(resp);
    }
}
