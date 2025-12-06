/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io.input;

import static org.apache.commons.io.IOUtils.EOF;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.build.AbstractStreamBuilder;
import org.apache.commons.io.output.QueueOutputStream;


public class QueueInputStream extends InputStream {

    // @formatter:off
    
    // @formatter:on
    public static class Builder extends AbstractStreamBuilder<QueueInputStream, Builder> {

        private BlockingQueue<Integer> blockingQueue = new LinkedBlockingQueue<>();
        private Duration timeout = Duration.ZERO;

        
        public Builder() {
            // empty
        }

        
        @Override
        public QueueInputStream get() {
            return new QueueInputStream(this);
        }

        
        public Builder setBlockingQueue(final BlockingQueue<Integer> blockingQueue) {
            this.blockingQueue = blockingQueue != null ? blockingQueue : new LinkedBlockingQueue<>();
            return this;
        }

        
        public Builder setTimeout(final Duration timeout) {
            if (timeout != null && timeout.toNanos() < 0) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
            this.timeout = timeout != null ? timeout : Duration.ZERO;
            return this;
        }

    }

    
    public static Builder builder() {
        return new Builder();
    }

    private final BlockingQueue<Integer> blockingQueue;

    private final long timeoutNanos;

    
    public QueueInputStream() {
        this(new LinkedBlockingQueue<>());
    }

    
    @Deprecated
    public QueueInputStream(final BlockingQueue<Integer> blockingQueue) {
        this(builder().setBlockingQueue(blockingQueue));
    }

    
    private QueueInputStream(final Builder builder) {
        this.blockingQueue = Objects.requireNonNull(builder.blockingQueue, "blockingQueue");
        this.timeoutNanos = Objects.requireNonNull(builder.timeout, "timeout").toNanos();
    }

    
    BlockingQueue<Integer> getBlockingQueue() {
        return blockingQueue;
    }

    
    Duration getTimeout() {
        return Duration.ofNanos(timeoutNanos);
    }

    
    public QueueOutputStream newQueueOutputStream() {
        return new QueueOutputStream(blockingQueue);
    }

    
    @Override
    public int read() {
        try {
            final Integer value = blockingQueue.poll(timeoutNanos, TimeUnit.NANOSECONDS);
            return value == null ? EOF : 0xFF & value;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            // throw runtime unchecked exception to maintain signature backward-compatibility of
            // this read method, which does not declare IOException
            throw new IllegalStateException(e);
        }
    }

    
    @Override
    public int read(final byte[] b, final int offset, final int length) {
        IOUtils.checkFromIndexSize(b, offset, length);
        if (length == 0) {
            return 0;
        }
        final List<Integer> drain = new ArrayList<>(Math.min(length, blockingQueue.size()));
        blockingQueue.drainTo(drain, length);
        if (drain.isEmpty()) {
            // no data immediately available. wait for first byte
            final int value = read();
            if (value == EOF) {
                return EOF;
            }
            drain.add(value);
            blockingQueue.drainTo(drain, length - 1);
        }
        int i = 0;
        for (final Integer value : drain) {
            b[offset + i] = (byte) (0xFF & value);
            i++;
        }
        return i;
    }

}
