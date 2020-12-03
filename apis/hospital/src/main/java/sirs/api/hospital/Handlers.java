package sirs.api.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sirs.api.hospital.accessControl.ResourceId;
import sirs.api.hospital.db.Repo;
import sirs.api.hospital.entities.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;


@RestController
public class Handlers {
    Repo repo = new Repo();
    Crypto cr = new Crypto();

    @GetMapping("/patient/{id}/name")
    @ResourceId(resourceId = "getPatientName")
    public ResponseEntity<Patient> getPatientName(@PathVariable int id) {
        Patient name = repo.getPatientName(id);
        if(name == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(name);
    }

    @GetMapping("/patient/{id}/diseases")
    @ResourceId(resourceId = "getPatientDiseases")
    public ResponseEntity<Patient> getPatientDiseases(@PathVariable int id) {
        Patient diseases = repo.getPatientDiseases(id);
        if(diseases == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(diseases);
    }

    @GetMapping("/patient/{id}/testresults")
    @ResourceId(resourceId = "getPatientTestResults")
    public ResponseEntity<Patient> getPatientTestResults(@PathVariable int id) {
        Patient results = repo.getPatientTestResults(id);
        if(results == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/patient/{id}/treatment")
    @ResourceId(resourceId = "getPatientTreatment")
    public ResponseEntity<Patient> getPatientTreatment(@PathVariable int id) {
        Patient treatment = repo.getPatientTreatment(id);
        if(treatment == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(treatment);
    }

    //TODO: Needs further testing
    @GetMapping("/checkauthenticity/{id}")
    @ResourceId(resourceId = "checkAuthenticity")
    public ResponseEntity<AuthenticityCheck> getTestAuthenticity(@PathVariable int id) {
        String[] info = repo.getResult(id);
        if(info[0] == null || info[1] == null || info[2] == null)
            return ResponseEntity.notFound().build();

        if(cr.verifySignature(info[0], info[1], info[2]))
            return ResponseEntity.ok(new AuthenticityCheck("Test result is authentic!"));
        else
            return ResponseEntity.ok(new AuthenticityCheck("Test result test validity is compromised, do not use it."));
    }

    @PostMapping("/login")
    @ResourceId(resourceId = "login")
    public ResponseEntity<TokenEntity> login(@RequestBody LoginBody credentials) {
        // Create new session or return already existing token for session
        String token = repo.getSessionToken(credentials);

        if(!token.equals("")) {
            System.out.println("[Handlers - Login] Authentication succeeded.");
            return ResponseEntity.ok(new TokenEntity(token));
        } else {
            System.out.println("[Handlers - Login] Authentication failed.");
            return ResponseEntity.status(401).build();  // In case the authentication failed
        }
    }

    /**
     *  Requests test results from the lab, this handler must create & handle the custom secure channel.
     *  How will it add confidentiality & integrity?
     *
     *  1º Request the certificate from the Lab by sending the hospital certificate
     *  2º Validate certificate
     *  3º Generate a random string, encrypt it with the public key of the Lab and send it.
     *  4º Hospital and Lab generate the secret key from the random string.
     *  5º Using the generated private key: encrypt the body, generate a tag and encrypt with same key (or generate another? it would be better)
     *  6º After encryption send the message to the Lab and await response.
     *  7º Decrypt, separate message from tag, validate tag & timestamp...
     *  8º Save to db
     *
     */
    @JsonIgnoreProperties
    @GetMapping("/gettestresults/{id}")
    @ResourceId(resourceId = "getTestsResult")
    public ResponseEntity<TestResponse> sendTestToLab(@PathVariable int id) {
        try {
            CustomProtocol customProtocol = new CustomProtocol();

            // Getting the certificate
            File crtFile = new File("src/main/resources/hospital.pem");
            String certificate = Files.readString(crtFile.toPath(), Charset.defaultCharset());
            HandshakeRequest handshakeRequest = new HandshakeRequest(certificate);

            // Write body
            ObjectMapper mapper = new ObjectMapper();

            // Send certificate and receive response that makes possible to create the secret key
            String reqBody = mapper.writeValueAsString(handshakeRequest);
            HttpClient handshakeClient = HttpClient.newHttpClient();
            HttpRequest handshakeReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8082/beginhandshake"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                    .build();

            HttpResponse<String> handshakeResponse = handshakeClient.send(handshakeReq, HttpResponse.BodyHandlers.ofString());
            CustomProtocolResponse2 cp2Response = mapper.readValue(handshakeResponse.body(), CustomProtocolResponse2.class);
            HandshakeResponse hsResponse = cp2Response.getHandshakeResponse();

            //Generate secret key
             customProtocol.generateSecretKey(hsResponse, "src/main/resources/hospitalKeystore.jks");

            if(customProtocol.dataCheck(cp2Response.getMac())) {

                TestRequest testRequest = new TestRequest("RANDOM STUFF THIS DOESNT MATTER IS JUST TO SIMULATE A REQUEST", hsResponse.getNonce());

                // TODO: maybe encrypt the request data with labs pubKey or mac, idk
                // String mac = customProtocol.macMessage(data.getBytes());

                // Sending the testReq (including data and nonce)
                String testReqBody = mapper.writeValueAsString(testRequest);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8082/teststoanalyze/" + id))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(testReqBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                //Read Response
                CustomProtocolResponse cpResponse = mapper.readValue(response.body(), CustomProtocolResponse.class);

                if(customProtocol.dataCheck(cp2Response.getMac())) {
                    TestResponse testResponse = cpResponse.getTestResponse();

                    // TODO: verify nonce

                    // decrypting the results
                    String decryptedResults = customProtocol.decryptWithSecretKey(testResponse.getResults());
                    System.out.println(decryptedResults);

                    return ResponseEntity.ok(testResponse);

                }
            }
            return ResponseEntity.status(500).build();

        } catch(Exception e) {
            System.out.println("Unable to make HTTP Request");
            return ResponseEntity.status(500).build();
        }
    }
}

