package com.xshellcode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class XShell {
    private static final String TAG = "XShell";

    private static final String THREAD_PROCESS_NAME = "XProcessThread";
    private static final String THREAD_WAITER_NAME = "XWaiterThread";
    private static final String THREAD_INPUT_STREAM_READER_NAME = "XInputReaderThread";
    private static final String THREAD_ERROR_STREAM_READER_NAME = "XErrorReaderThread";

    private List<String> command;
    private File directory;
    private Map<String, String> environment;
    private boolean redirectErrorStream = false;
    private long timeoutMillis = 0;
    private ProcessStatusCallback statusCallback;
    private AtomicBoolean isProcessRunning = new AtomicBoolean(false);
    private Integer exitValue = null;

    public static class Builder {
        private final XShell shell = new XShell();

        public Builder setCommand(List<String> command) {
            shell.command = command;
            return this;
        }

        public Builder setEnvironment(Map<String, String> environment) {
            shell.environment = environment;
            return this;
        }

        public Builder setWorkingDirectory(File directory) {
            shell.directory = directory;
            return this;
        }

        public Builder setRedirectErrorStream(boolean redirectErrorStream) {
            shell.redirectErrorStream = redirectErrorStream;
            return this;
        }

        public Builder setTimeout(long timeoutMillis) {
            shell.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder setProcessStatusCallback(ProcessStatusCallback callback) {
            shell.statusCallback = callback;
            return this;
        }

        public XShell build() {
            return shell;
        }
    }

    public void start() {
        if (isProcessRunning.get()) {
            throw new RuntimeException("Process has running.");
        }
        Runnable runnable = () -> {
            Process process = null;
            WaiterThread waiter = null;
            try {
                ProcessBuilder builder = new ProcessBuilder();
                builder.redirectErrorStream(redirectErrorStream);
                builder.command(command);
                builder.directory(directory);
                Map<String, String> envMap = builder.environment();
                if (environment != null) {
                    envMap.putAll(environment);
                }
                process = builder.start();
                isProcessRunning.set(true);

                new InputReaderThread(process.getInputStream(), THREAD_INPUT_STREAM_READER_NAME,
                        new InputReaderThread.ReaderCallback() {
                            @Override
                            public void onRead(byte[] bytes, int offset, int readLength) {
                                if (statusCallback != null) {
                                    String s = new String(bytes, offset, readLength);
                                    statusCallback.onReadInputStream(s);
                                }
                            }
                        }).start();
                if (redirectErrorStream) {
                    new InputReaderThread(process.getErrorStream(), THREAD_ERROR_STREAM_READER_NAME,
                            new InputReaderThread.ReaderCallback() {
                                @Override
                                public void onRead(byte[] bytes, int offset, int readLength) {
                                    if (statusCallback != null) {
                                        String s = new String(bytes, offset, readLength);
                                        statusCallback.onReadErrorStream(s);
                                    }
                                }
                            }).start();
                }

                waiter = new WaiterThread(process);
                waiter.start();
                // will block until waiterThread finished or timeout.
                waiter.join(timeoutMillis);
                exitValue = waiter.exit;
                if (statusCallback != null) {
                    statusCallback.onProcessExit(waiter.exit);
                }
                if (waiter.exit == null) {
                    throw new RuntimeException("Process timeout.");
                }
            } catch (InterruptedException e) {
                waiter.interrupt();
                Thread.currentThread().interrupt();
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                isProcessRunning.set(false);
                if (process != null) {
                    process.destroy();
                }
            }
        };
        Thread processThread = new Thread(runnable);
        processThread.setName(THREAD_PROCESS_NAME);
        processThread.start();
    }

    public int getExitValue() {
        if (isProcessRunning.get()) {
            throw new RuntimeException("Process has not yet terminated");
        } else {
            return exitValue;
        }
    }

    private static class WaiterThread extends Thread {
        private final Process process;
        private Integer exit;

        private WaiterThread(Process process) {
            this.process = process;
            setName(THREAD_WAITER_NAME);
        }

        @Override
        public void run() {
            try {
                exit = process.waitFor();
                process.destroy();
            } catch (InterruptedException ignore) {
            }
        }
    }

    private static class InputReaderThread extends Thread {
        private final InputStream in;
        private final ReaderCallback readerCallback;

        public InputReaderThread(InputStream in, String name, ReaderCallback callback) {
            this.in = in;
            this.setName(name);
            this.readerCallback = callback;
        }

        @Override
        public void run() {
            try {
                byte[] bytes = new byte[1024 * 4];
                int hasRead;
                while ((hasRead = in.read(bytes)) > 0) {
                    readerCallback.onRead(bytes, 0, hasRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        interface ReaderCallback {
            void onRead(byte[] bytes, int offset, int readLength);
        }
    }

    public static class SimpleProcessStatusCallback implements ProcessStatusCallback {

        @Override
        public void onReadInputStream(String inputStr) {

        }

        @Override
        public void onReadErrorStream(String errorStr) {

        }

        @Override
        public void onProcessExit(Integer exitCode) {

        }
    }

    interface ProcessStatusCallback {
        void onReadInputStream(String inputStr);

        void onReadErrorStream(String errorStr);

        void onProcessExit(Integer exitCode);
    }
}
