/*
 * Copyright 2020, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package anthos.samples.financedemo.ledgerwriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestTemplate;

import io.lettuce.core.api.StatefulRedisConnection;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;

/**
 * Controller for the LedgerWriter service.
 *
 * Functions to accept new transactions for the bank ledger.
 */
@RestController
public final class LedgerWriterController {

    private static final Logger LOGGER =
            Logger.getLogger(LedgerWriterController.class.getName());

    private final String version = System.getenv("VERSION");
    private final String pubKeyPath = System.getenv("PUB_KEY_PATH");
    private final String localRoutingNum = System.getenv("LOCAL_ROUTING_NUM");
    private final String ledgerStream = System.getenv("LEDGER_STREAM");
    private final String balancesApiUri = String.format("http://${}/balances",
            System.getenv("BALANCES_API_ADDR"));

    private final ApplicationContext ctx;
    private final JWTVerifier verifier;

    /**
     * Constructor.
     *
     * Opens a connection to the transaction repository for the bank ledger.
     */
    public LedgerWriterController() throws IOException,
                                           NoSuchAlgorithmException,
                                           InvalidKeySpecException {
        this.ctx = new AnnotationConfigApplicationContext(
                LedgerWriterConfig.class);

        // Initialize JWT verifier.
        String pubKeyStr =
                new String(Files.readAllBytes(Paths.get(pubKeyPath)));
        pubKeyStr = pubKeyStr.replaceFirst("-----BEGIN PUBLIC KEY-----", "");
        pubKeyStr = pubKeyStr.replaceFirst("-----END PUBLIC KEY-----", "");
        pubKeyStr = pubKeyStr.replaceAll("\\s", "");
        byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyStr);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(pubKeyBytes);
        RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(keySpecX509);
        Algorithm algorithm = Algorithm.RSA256(publicKey, null);
        this.verifier = JWT.require(algorithm).build();
    }

    /**
     * Version endpoint.
     *
     * @return  service version string
     */
    @GetMapping("/version")
    public ResponseEntity version() {
        return new ResponseEntity<String>(version, HttpStatus.OK);
    }

    /**
     * Readiness probe endpoint.
     *
     * @return HTTP Status 200 if server is ready to receive requests.
     */
    @GetMapping("/ready")
    @ResponseStatus(HttpStatus.OK)
    public String readiness() {
        return "ok";
    }

    /**
     * Submit a new transaction to the ledger.
     *
     * @param bearerToken  HTTP request 'Authorization' header
     * @param transaction  transaction to submit
     * @return             HTTP Status 200 if transaction was successfully
     *                     submitted
     */
    @PostMapping(value = "/transactions", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<?> addTransaction(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Transaction transaction) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken = bearerToken.split("Bearer ")[1];
        }
        try {
            DecodedJWT jwt = verifier.verify(bearerToken);
            String initiatorAcct = jwt.getClaim("acct").asString();
            // If a local account, check that it belongs to the initiator of
            // this request.
            // TODO: Check if external account belongs to initiator of deposit.
            if (transaction.getFromRoutingNum().equals(localRoutingNum)
                    && !transaction.getFromAccountNum().equals(initiatorAcct)) {
                return new ResponseEntity<String>("not authorized",
                                                  HttpStatus.UNAUTHORIZED);
            }
            // Ensure amount is valid value.
            if (transaction.getAmount() <= 0) {
                return new ResponseEntity<String>("invalid amount",
                                                  HttpStatus.BAD_REQUEST);
            }
            // Ensure sender balance can cover transaction.
            if (transaction.getFromRoutingNum().equals(localRoutingNum)) {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + bearerToken);
                HttpEntity entity = new HttpEntity(headers);
                RestTemplate restTemplate = new RestTemplate();
                String uri = balancesApiUri + "/" + initiatorAcct;
                ResponseEntity<Integer> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, Integer.class);
                Integer senderBalance = response.getBody();
                if (senderBalance < transaction.getAmount()) {
                    return new ResponseEntity<String>("insufficient balance",
                                                      HttpStatus.BAD_REQUEST);
                }
            }
            // Transaction looks valid. Add to ledger.
            submitTransaction(transaction);

            return new ResponseEntity<String>("ok", HttpStatus.CREATED);
        } catch (JWTVerificationException e) {
            return new ResponseEntity<String>("not authorized",
                                              HttpStatus.UNAUTHORIZED);
        }
    }

    private void submitTransaction(Transaction transaction) {
        LOGGER.fine("Submitting transaction to ledger: " + transaction);
        StatefulRedisConnection redisConnection =
                ctx.getBean(StatefulRedisConnection.class);
        // Use String key/values so Redis data can be read by non-Java clients.
        redisConnection.async().xadd(ledgerStream,
                "fromAccountNum", transaction.getFromAccountNum(),
                "fromRoutingNum", transaction.getFromRoutingNum(),
                "toAccountNum", transaction.getToAccountNum(),
                "toRoutingNum", transaction.getToRoutingNum(),
                "amount", transaction.getAmount().toString(),
                "timestamp", Double.toString(transaction.getTimestamp()));
    }
}
