package com.teragrep.rlp_03;

public class ConnectContext {
        /*
    private class ConnectEvent implements KeyEvent {

        public final SocketChannel socketChannel;
        //public final SelectionKey key;


        ConnectEvent(String hostname, int port, Selector selector) throws IOException, TimeoutException {
            socketChannel = SocketChannel.open();
            socketChannel.socket().setKeepAlive(true);
            socketChannel.configureBlocking(false);
            key = socketChannel.register(selector, SelectionKey.OP_CONNECT);
            socketChannel.connect(new InetSocketAddress(hostname, port));
        }
        public void attach(Object o) {
            key.attach(o);
        }

        @Override
        public void handle(SelectionKey selectionKey) {
            if (selectionKey.isConnectable()) {
                SocketChannel channish = (SocketChannel) selectionKey.channel();
                if (channish.finishConnect()) {
                    // Connection established
                    notConnected = false;
                }
                // No need to be longer interested in connect.
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_CONNECT);
                selectionKey.interestOps(SelectionKey.OP_READ);
            }
        }

        @Override
        public void close() throws IOException {

        }
    }

     */
}
