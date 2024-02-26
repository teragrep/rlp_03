/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021  Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */

package com.teragrep.rlp_03.tls;

import org.junit.jupiter.api.Assertions;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLContextWithCustomTrustAndKeyManagerHelper {

    private static KeyManager[] getKeyManagers(final KeyManagerFactory keyManagerFactory) {
        final KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

        // replace any X509KeyManager with our own implementation
        for (int i = 0; i < keyManagers.length; i++) {
            if (keyManagers[i] instanceof X509ExtendedKeyManager) {
                keyManagers[i] =
                        new CustomKeyManager((X509ExtendedKeyManager) keyManagers[i]);
            }
        }

        return keyManagers;
    }

    private static TrustManager[] getTrustManagers(final TrustManagerFactory trustManagerFactory) {
        final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();


        // replace any X509KeyManager with our own implementation
        for (int i = 0; i < trustManagers.length; i++) {
            if (trustManagers[i] instanceof X509ExtendedTrustManager) {
                trustManagers[i] =
                        new CustomTrustManager((X509ExtendedTrustManager) trustManagers[i]);
            }
        }

        return trustManagers;
    }

    public static SSLContext getSslContext() throws NoSuchAlgorithmException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");

        File serverKeystoreFile = new File("src/test/resources/tls/keystore-server.jks");
        File serverTruststoreFile = new File("src/test/resources/tls/truststore.jks");

        try (FileInputStream serverKeystoreIS = new FileInputStream(serverKeystoreFile)) {
            try (FileInputStream serverTruststoreIS = new FileInputStream(serverTruststoreFile)) {

                // KeyManager
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(serverKeystoreIS, "changeit".toCharArray());
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, "changeit".toCharArray());

                // TrustManager
                KeyStore ts = KeyStore.getInstance("JKS");
                ts.load(serverTruststoreIS, "changeit".toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
                tmf.init(ts);

                sslContext.init(
                        getKeyManagers(kmf),
                        getTrustManagers(tmf),
                        null
                );

                return sslContext;
            } catch (UnrecoverableKeyException | CertificateException | KeyStoreException | IOException | KeyManagementException e) {
                Assertions.fail("Can't construct ssl context: ", e);
            }
        } catch (IOException e) {
            Assertions.fail("Can't construct ssl context: ", e);
        }
        return null;
    }
}
