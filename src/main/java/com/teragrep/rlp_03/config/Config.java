package com.teragrep.rlp_03.config;

public class Config {

    public final int port;
    public final int numberOfThreads;

    public final int readTimeout;
    public final int writeTimeout;

    public Config(int port, int numberOfThreads, int readTimeout, int writeTimeout) {
        this.port = port;
        this.numberOfThreads = numberOfThreads;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    public Config(int port, int numberOfThreads) {
        this(port, numberOfThreads, 1000, 1000);
    }

    public Config() {
        this(1601, 1);
    }


    public void validate() {
        if (numberOfThreads < 1) {
            throw new IllegalArgumentException("must use at least one thread");
        }
    }
}
