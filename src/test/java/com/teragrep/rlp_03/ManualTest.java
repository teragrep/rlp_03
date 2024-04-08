/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
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
package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.TLSFactory;
import com.teragrep.rlp_03.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.tls.SSLContextWithCustomTrustAndKeyManagerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class ManualTest {

    @Test // for testing with manual tools
    @EnabledIfSystemProperty(
            named = "runServerTest",
            matches = "true"
    )
    public void runServerTest() throws InterruptedException, IOException {
        final Consumer<FrameContext> cbFunction;
        AtomicLong asd = new AtomicLong();

        cbFunction = (message) -> {
            asd.getAndIncrement();
        };

        int port = 1601;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        ServerFactory serverFactory = new ServerFactory(
                executorService,
                new PlainFactory(),
                () -> new DefaultFrameDelegate(cbFunction)
        );
        Server server = serverFactory.create(port);

        Thread serverThread = new Thread(server);
        serverThread.start();

        serverThread.join();
        executorService.shutdown();
    }

    @Test // for testing with manual tools
    @EnabledIfSystemProperty(
            named = "runServerTlsTest",
            matches = "true"
    )
    public void runServerTlsTest() throws IOException, InterruptedException, GeneralSecurityException {
        final Consumer<FrameContext> cbFunction;

        cbFunction = (serverRX) -> {
            EncryptionInfo encryptionInfo = serverRX
                    .connectionContext()
                    .socket()
                    .getTransportInfo()
                    .getEncryptionInfo();
            if (encryptionInfo.isEncrypted()) {
                System.out.println(encryptionInfo.getSessionCipherSuite());
                try {
                    System.out.println(encryptionInfo.getPeerCertificates()[0].toString());
                }
                catch (SSLPeerUnverifiedException sslPeerUnverifiedException) {
                    sslPeerUnverifiedException.printStackTrace();
                }
            }

            System.out.println(new String(serverRX.relpFrame().payload().toBytes()));
        };

        SSLContext sslContext = SSLContextWithCustomTrustAndKeyManagerHelper.getSslContext();

        Function<SSLContext, SSLEngine> sslEngineFunction = new Function<SSLContext, SSLEngine>() {

            @Override
            public SSLEngine apply(SSLContext sslContext) {
                SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(false);
                sslEngine.setNeedClientAuth(true); // enable client auth
                //sslEngine.setWantClientAuth(false);

                String[] enabledCipherSuites = {
                        "TLS_AES_256_GCM_SHA384"
                };
                sslEngine.setEnabledCipherSuites(enabledCipherSuites);
                String[] enabledProtocols = {
                        "TLSv1.3"
                };
                sslEngine.setEnabledProtocols(enabledProtocols);

                return sslEngine;
            }
        };

        int port = 1602;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        TLSFactory tlsFactory = new TLSFactory(sslContext, sslEngineFunction);

        ServerFactory serverFactory = new ServerFactory(
                executorService,
                tlsFactory,
                () -> new DefaultFrameDelegate(cbFunction)
        );

        Server server = serverFactory.create(port);

        Thread serverThread = new Thread(server);
        serverThread.start();

        serverThread.join();
    }
}
