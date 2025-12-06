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

package org.apache.commons.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.io.channels.FileChannels;
import org.apache.commons.io.function.IOConsumer;
import org.apache.commons.io.function.IOSupplier;
import org.apache.commons.io.function.IOTriFunction;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.io.input.QueueInputStream;
import org.apache.commons.io.output.AppendableWriter;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.NullWriter;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;


public class IOUtils {
    // NOTE: This class is focused on InputStream, OutputStream, Reader and
    // Writer. Each method should take at least one of these as a parameter,
    // or return one of them.

    
    static final class ScratchBytes implements AutoCloseable {

        
        private static final ThreadLocal<Object[]> LOCAL = ThreadLocal.withInitial(() -> new Object[] { false, new ScratchBytes(byteArray()) });

        
        static ScratchBytes get() {
            final Object[] holder = LOCAL.get();
            // If already in use, return a new array
            if ((boolean) holder[0]) {
                return new ScratchBytes(byteArray());
            }
            holder[0] = true;
            return (ScratchBytes) holder[1];
        }

        private final byte[] buffer;

        private ScratchBytes(final byte[] buffer) {
            this.buffer = buffer;
        }

        byte[] array() {
            return buffer;
        }

        
        @Override
        public void close() {
            final Object[] holder = LOCAL.get();
            if (buffer == ((ScratchBytes) holder[1]).buffer) {
                Arrays.fill(buffer, (byte) 0);
                holder[0] = false;
            }
        }
    }

    
    static final class ScratchChars implements AutoCloseable {

        
        private static final ThreadLocal<Object[]> LOCAL = ThreadLocal.withInitial(() -> new Object[] { false, new ScratchChars(charArray()) });

        
        static ScratchChars get() {
            final Object[] holder = LOCAL.get();
            // If already in use, return a new array
            if ((boolean) holder[0]) {
                return new ScratchChars(charArray());
            }
            holder[0] = true;
            return (ScratchChars) holder[1];
        }

        private final char[] buffer;

        private ScratchChars(final char[] buffer) {
            this.buffer = buffer;
        }

        char[] array() {
            return buffer;
        }

        
        @Override
        public void close() {
            final Object[] holder = LOCAL.get();
            if (buffer == ((ScratchChars) holder[1]).buffer) {
                Arrays.fill(buffer, (char) 0);
                holder[0] = false;
            }
        }
    }

    
    public static final int CR = '\r';

    
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    
    public static final char DIR_SEPARATOR = File.separatorChar;

    
    public static final char DIR_SEPARATOR_UNIX = '/';

    
    public static final char DIR_SEPARATOR_WINDOWS = '\\';

    
    public static final byte[] EMPTY_BYTE_ARRAY = {};

    
    public static final int EOF = -1;

    
    public static final int LF = '\n';

    
    @Deprecated
    public static final String LINE_SEPARATOR = System.lineSeparator();

    
    public static final String LINE_SEPARATOR_UNIX = StandardLineSeparator.LF.getString();

    
    public static final String LINE_SEPARATOR_WINDOWS = StandardLineSeparator.CRLF.getString();

    
    public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    
    @SuppressWarnings("resource") // parameter null check
    public static BufferedInputStream buffer(final InputStream inputStream) {
        // reject null early on rather than waiting for IO operation to fail
        // not checked by BufferedInputStream
        Objects.requireNonNull(inputStream, "inputStream");
        return inputStream instanceof BufferedInputStream ?
                (BufferedInputStream) inputStream : new BufferedInputStream(inputStream);
    }

    
    @SuppressWarnings("resource") // parameter null check
    public static BufferedInputStream buffer(final InputStream inputStream, final int size) {
        // reject null early on rather than waiting for IO operation to fail
        // not checked by BufferedInputStream
        Objects.requireNonNull(inputStream, "inputStream");
        return inputStream instanceof BufferedInputStream ?
                (BufferedInputStream) inputStream : new BufferedInputStream(inputStream, size);
    }

    
    @SuppressWarnings("resource") // parameter null check
    public static BufferedOutputStream buffer(final OutputStream outputStream) {
        // reject null early on rather than waiting for IO operation to fail
        // not checked by BufferedInputStream
        Objects.requireNonNull(outputStream, "outputStream");
        return outputStream instanceof BufferedOutputStream ?
                (BufferedOutputStream) outputStream : new BufferedOutputStream(outputStream);
    }

    
    @SuppressWarnings("resource") // parameter null check
    public static BufferedOutputStream buffer(final OutputStream outputStream, final int size) {
        // reject null early on rather than waiting for IO operation to fail
        // not checked by BufferedInputStream
        Objects.requireNonNull(outputStream, "outputStream");
        return outputStream instanceof BufferedOutputStream ?
                (BufferedOutputStream) outputStream : new BufferedOutputStream(outputStream, size);
    }

    
    public static BufferedReader buffer(final Reader reader) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    }

    
    public static BufferedReader buffer(final Reader reader, final int size) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader, size);
    }

    
    public static BufferedWriter buffer(final Writer writer) {
        return writer instanceof BufferedWriter ? (BufferedWriter) writer : new BufferedWriter(writer);
    }

    
    public static BufferedWriter buffer(final Writer writer, final int size) {
        return writer instanceof BufferedWriter ? (BufferedWriter) writer : new BufferedWriter(writer, size);
    }

    
    public static byte[] byteArray() {
        return byteArray(DEFAULT_BUFFER_SIZE);
    }

    
    public static byte[] byteArray(final int size) {
        return new byte[size];
    }

    
    private static char[] charArray() {
        return charArray(DEFAULT_BUFFER_SIZE);
    }

    
    private static char[] charArray(final int size) {
        return new char[size];
    }

    
    public static void checkFromIndexSize(final byte[] array, final int off, final int len) {
        checkFromIndexSize(off, len, Objects.requireNonNull(array, "byte array").length);
    }

    
    public static void checkFromIndexSize(final char[] array, final int off, final int len) {
        checkFromIndexSize(off, len, Objects.requireNonNull(array, "char array").length);
    }

    static void checkFromIndexSize(final int off, final int len, final int arrayLength) {
        if ((off | len | arrayLength) < 0 || arrayLength - len < off) {
            throw new IndexOutOfBoundsException(String.format("Range [%s, %<s + %s) out of bounds for length %s", off, len, arrayLength));
        }
    }

    
    public static void checkFromIndexSize(final String str, final int off, final int len) {
        checkFromIndexSize(off, len, Objects.requireNonNull(str, "str").length());
    }

    
    public static void checkFromToIndex(final CharSequence seq, final int fromIndex, final int toIndex) {
        checkFromToIndex(fromIndex, toIndex, seq != null ? seq.length() : 4);
    }

    static void checkFromToIndex(final int fromIndex, final int toIndex, final int length) {
        if (fromIndex < 0 || toIndex < fromIndex || length < toIndex) {
            throw new IndexOutOfBoundsException(String.format("Range [%s, %s) out of bounds for length %s", fromIndex, toIndex, length));
        }
    }

    
    static void clear() {
        ScratchBytes.LOCAL.remove();
        ScratchChars.LOCAL.remove();
    }

    
    public static void close(final Closeable closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    
    public static void close(final Closeable... closeables) throws IOExceptionList {
        IOConsumer.forAll(IOUtils::close, closeables);
    }

    
    public static void close(final Closeable closeable, final IOConsumer<IOException> consumer) throws IOException {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException e) {
                if (consumer != null) {
                    consumer.accept(e);
                }
            } catch (final Exception e) {
                if (consumer != null) {
                    consumer.accept(new IOException(e));
                }
            }
        }
    }

    
    public static void close(final URLConnection conn) {
        if (conn instanceof HttpURLConnection) {
            ((HttpURLConnection) conn).disconnect();
        }
    }

    
    private static void closeQ(final Closeable closeable) {
        closeQuietly(closeable, null);
    }

    
    public static void closeQuietly(final Closeable closeable) {
        closeQuietly(closeable, null);
    }

    
    public static void closeQuietly(final Closeable... closeables) {
        if (closeables != null) {
            closeQuietly(Arrays.stream(closeables));
        }
    }

    
    public static void closeQuietly(final Closeable closeable, final Consumer<Exception> consumer) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final Exception e) {
                if (consumer != null) {
                    consumer.accept(e);
                }
            }
        }
    }

    
    public static void closeQuietly(final InputStream input) {
        closeQ(input);
    }

    
    public static void closeQuietly(final Iterable<Closeable> closeables) {
        if (closeables != null) {
            closeables.forEach(IOUtils::closeQuietly);
        }
    }

    
    public static void closeQuietly(final OutputStream output) {
        closeQ(output);
    }

    
    public static void closeQuietly(final Reader reader) {
        closeQ(reader);
    }

    
    public static void closeQuietly(final Selector selector) {
        closeQ(selector);
    }

    
    public static void closeQuietly(final ServerSocket serverSocket) {
        closeQ(serverSocket);
    }

    
    public static void closeQuietly(final Socket socket) {
        closeQ(socket);
    }

    
    public static void closeQuietly(final Stream<Closeable> closeables) {
        if (closeables != null) {
            closeables.forEach(IOUtils::closeQuietly);
        }
    }

    
    public static void closeQuietly(final Writer writer) {
        closeQ(writer);
    }

    
    public static long consume(final InputStream input) throws IOException {
        return copyLarge(input, NullOutputStream.INSTANCE);
    }

    
    public static long consume(final Reader input) throws IOException {
        return copyLarge(input, NullWriter.INSTANCE);
    }

    
    @SuppressWarnings("resource") // Caller closes input streams
    public static boolean contentEquals(final InputStream input1, final InputStream input2) throws IOException {
        // Before making any changes, please test with org.apache.commons.io.jmh.IOUtilsContentEqualsInputStreamsBenchmark
        if (input1 == input2) {
            return true;
        }
        if (input1 == null || input2 == null) {
            return false;
        }
        // We do not close FileChannels because that closes the owning InputStream.
        return FileChannels.contentEquals(Channels.newChannel(input1), Channels.newChannel(input2), DEFAULT_BUFFER_SIZE);
    }

    // TODO Consider making public
    private static boolean contentEquals(final Iterator<?> iterator1, final Iterator<?> iterator2) {
        while (iterator1.hasNext()) {
            if (!iterator2.hasNext()) {
                return false;
            }
            if (!Objects.equals(iterator1.next(), iterator2.next())) {
                return false;
            }
        }
        return !iterator2.hasNext();
    }

    
    public static boolean contentEquals(final Reader input1, final Reader input2) throws IOException {
        if (input1 == input2) {
            return true;
        }
        if (input1 == null || input2 == null) {
            return false;
        }

        // reuse one
        try (ScratchChars scratch = IOUtils.ScratchChars.get()) {
            final char[] array1 = scratch.array();
            // but allocate another
            final char[] array2 = charArray();
            int pos1;
            int pos2;
            int count1;
            int count2;
            while (true) {
                pos1 = 0;
                pos2 = 0;
                for (int index = 0; index < DEFAULT_BUFFER_SIZE; index++) {
                    if (pos1 == index) {
                        do {
                            count1 = input1.read(array1, pos1, DEFAULT_BUFFER_SIZE - pos1);
                        } while (count1 == 0);
                        if (count1 == EOF) {
                            return pos2 == index && input2.read() == EOF;
                        }
                        pos1 += count1;
                    }
                    if (pos2 == index) {
                        do {
                            count2 = input2.read(array2, pos2, DEFAULT_BUFFER_SIZE - pos2);
                        } while (count2 == 0);
                        if (count2 == EOF) {
                            return pos1 == index && input1.read() == EOF;
                        }
                        pos2 += count2;
                    }
                    if (array1[index] != array2[index]) {
                        return false;
                    }
                }
            }
        }
    }

    // TODO Consider making public
    private static boolean contentEquals(final Stream<?> stream1, final Stream<?> stream2) {
        if (stream1 == stream2) {
            return true;
        }
        if (stream1 == null || stream2 == null) {
            return false;
        }
        return contentEquals(stream1.iterator(), stream2.iterator());
    }

    // TODO Consider making public
    private static boolean contentEqualsIgnoreEOL(final BufferedReader reader1, final BufferedReader reader2) {
        if (reader1 == reader2) {
            return true;
        }
        if (reader1 == null || reader2 == null) {
            return false;
        }
        return contentEquals(reader1.lines(), reader2.lines());
    }

    
    @SuppressWarnings("resource")
    public static boolean contentEqualsIgnoreEOL(final Reader reader1, final Reader reader2) throws UncheckedIOException {
        if (reader1 == reader2) {
            return true;
        }
        if (reader1 == null || reader2 == null) {
            return false;
        }
        return contentEqualsIgnoreEOL(toBufferedReader(reader1), toBufferedReader(reader2));
    }

    
    public static int copy(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        final long count = copyLarge(inputStream, outputStream);
        return count > Integer.MAX_VALUE ? EOF : (int) count;
    }

    
    public static long copy(final InputStream inputStream, final OutputStream outputStream, final int bufferSize) throws IOException {
        return copyLarge(inputStream, outputStream, byteArray(bufferSize));
    }

    
    @Deprecated
    public static void copy(final InputStream input, final Writer writer) throws IOException {
        copy(input, writer, Charset.defaultCharset());
    }

    
    public static void copy(final InputStream input, final Writer writer, final Charset inputCharset) throws IOException {
        copy(new InputStreamReader(input, Charsets.toCharset(inputCharset)), writer);
    }

    
    public static void copy(final InputStream input, final Writer writer, final String inputCharsetName) throws IOException {
        copy(input, writer, Charsets.toCharset(inputCharsetName));
    }

    
    @SuppressWarnings("resource") // streams are closed by the caller.
    public static QueueInputStream copy(final java.io.ByteArrayOutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        final QueueInputStream in = new QueueInputStream();
        outputStream.writeTo(in.newQueueOutputStream());
        return in;
    }

    
    public static long copy(final Reader reader, final Appendable output) throws IOException {
        return copy(reader, output, CharBuffer.allocate(DEFAULT_BUFFER_SIZE));
    }

    
    public static long copy(final Reader reader, final Appendable output, final CharBuffer buffer) throws IOException {
        long count = 0;
        int n;
        while (EOF != (n = reader.read(buffer))) {
            buffer.flip();
            output.append(buffer, 0, n);
            count += n;
        }
        return count;
    }

    
    @Deprecated
    public static void copy(final Reader reader, final OutputStream output) throws IOException {
        copy(reader, output, Charset.defaultCharset());
    }

    
    public static void copy(final Reader reader, final OutputStream output, final Charset outputCharset) throws IOException {
        final OutputStreamWriter writer = new OutputStreamWriter(output, Charsets.toCharset(outputCharset));
        copy(reader, writer);
        // XXX Unless anyone is planning on rewriting OutputStreamWriter,
        // we have to flush here.
        writer.flush();
    }

    
    public static void copy(final Reader reader, final OutputStream output, final String outputCharsetName) throws IOException {
        copy(reader, output, Charsets.toCharset(outputCharsetName));
    }

    
    public static int copy(final Reader reader, final Writer writer) throws IOException {
        final long count = copyLarge(reader, writer);
        if (count > Integer.MAX_VALUE) {
            return EOF;
        }
        return (int) count;
    }

    
    public static long copy(final URL url, final File file) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(Objects.requireNonNull(file, "file").toPath())) {
            return copy(url, outputStream);
        }
    }

    
    public static long copy(final URL url, final OutputStream outputStream) throws IOException {
        try (InputStream inputStream = Objects.requireNonNull(url, "url").openStream()) {
            return copyLarge(inputStream, outputStream);
        }
    }

    
    public static long copyLarge(final InputStream inputStream, final OutputStream outputStream)
            throws IOException {
        return copy(inputStream, outputStream, DEFAULT_BUFFER_SIZE);
    }

    
    @SuppressWarnings("resource") // streams are closed by the caller.
    public static long copyLarge(final InputStream inputStream, final OutputStream outputStream, final byte[] buffer)
        throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(outputStream, "outputStream");
        long count = 0;
        int n;
        while (EOF != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    
    public static long copyLarge(final InputStream input, final OutputStream output, final long inputOffset, final long length) throws IOException {
        try (ScratchBytes scratch = ScratchBytes.get()) {
            return copyLarge(input, output, inputOffset, length, scratch.array());
        }
    }

    
    public static long copyLarge(final InputStream input, final OutputStream output,
                                 final long inputOffset, final long length, final byte[] buffer) throws IOException {
        if (inputOffset > 0) {
            skipFully(input, inputOffset);
        }
        if (length == 0) {
            return 0;
        }
        final int bufferLength = buffer.length;
        int bytesToRead = bufferLength;
        if (length > 0 && length < bufferLength) {
            bytesToRead = (int) length;
        }
        int read;
        long totalRead = 0;
        while (bytesToRead > 0 && EOF != (read = input.read(buffer, 0, bytesToRead))) {
            output.write(buffer, 0, read);
            totalRead += read;
            if (length > 0) { // only adjust length if not reading to the end
                // Note the cast must work because buffer.length is an integer
                bytesToRead = (int) Math.min(length - totalRead, bufferLength);
            }
        }
        return totalRead;
    }

    
    public static long copyLarge(final Reader reader, final Writer writer) throws IOException {
        try (ScratchChars scratch = IOUtils.ScratchChars.get()) {
            return copyLarge(reader, writer, scratch.array());
        }
    }

    
    public static long copyLarge(final Reader reader, final Writer writer, final char[] buffer) throws IOException {
        long count = 0;
        int n;
        while (EOF != (n = reader.read(buffer))) {
            writer.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    
    public static long copyLarge(final Reader reader, final Writer writer, final long inputOffset, final long length) throws IOException {
        try (ScratchChars scratch = IOUtils.ScratchChars.get()) {
            return copyLarge(reader, writer, inputOffset, length, scratch.array());
        }
    }

    
    public static long copyLarge(final Reader reader, final Writer writer, final long inputOffset, final long length, final char[] buffer) throws IOException {
        if (inputOffset > 0) {
            skipFully(reader, inputOffset);
        }
        if (length == 0) {
            return 0;
        }
        int bytesToRead = buffer.length;
        if (length > 0 && length < buffer.length) {
            bytesToRead = (int) length;
        }
        int read;
        long totalRead = 0;
        while (bytesToRead > 0 && EOF != (read = reader.read(buffer, 0, bytesToRead))) {
            writer.write(buffer, 0, read);
            totalRead += read;
            if (length > 0) { // only adjust length if not reading to the end
                // Note the cast must work because buffer.length is an integer
                bytesToRead = (int) Math.min(length - totalRead, buffer.length);
            }
        }
        return totalRead;
    }

    
    static UnsynchronizedByteArrayOutputStream copyToOutputStream(
            final InputStream input, final long limit, final int bufferSize) throws IOException {
        try (UnsynchronizedByteArrayOutputStream output = UnsynchronizedByteArrayOutputStream.builder()
                        .setBufferSize(bufferSize)
                        .get();
                InputStream boundedInput = BoundedInputStream.builder()
                        .setMaxCount(limit)
                        .setPropagateClose(false)
                        .setInputStream(input)
                        .get()) {
            output.write(boundedInput);
            return output;
        }
    }

    
    public static int length(final byte[] array) {
        return array == null ? 0 : array.length;
    }

    
    public static int length(final char[] array) {
        return array == null ? 0 : array.length;
    }

    
    public static int length(final CharSequence csq) {
        return csq == null ? 0 : csq.length();
    }

    
    public static int length(final Object[] array) {
        return array == null ? 0 : array.length;
    }

    
    public static LineIterator lineIterator(final InputStream input, final Charset charset) {
        return new LineIterator(new InputStreamReader(input, Charsets.toCharset(charset)));
    }

    
    public static LineIterator lineIterator(final InputStream input, final String charsetName) {
        return lineIterator(input, Charsets.toCharset(charsetName));
    }

    
    public static LineIterator lineIterator(final Reader reader) {
        return new LineIterator(reader);
    }

    
    public static int read(final InputStream input, final byte[] buffer) throws IOException {
        return read(input, buffer, 0, buffer.length);
    }

    
    public static int read(final InputStream input, final byte[] buffer, final int offset, final int length)
            throws IOException {
        checkFromIndexSize(buffer, offset, length);
        int remaining = length;
        while (remaining > 0) {
            final int location = length - remaining;
            final int count = input.read(buffer, offset + location, remaining);
            if (EOF == count) {
                break;
            }
            remaining -= count;
        }
        return length - remaining;
    }

    
    public static int read(final ReadableByteChannel input, final ByteBuffer buffer) throws IOException {
        final int length = buffer.remaining();
        while (buffer.remaining() > 0) {
            final int count = input.read(buffer);
            if (EOF == count) { // EOF
                break;
            }
        }
        return length - buffer.remaining();
    }

    
    public static int read(final Reader reader, final char[] buffer) throws IOException {
        return read(reader, buffer, 0, buffer.length);
    }

    
    public static int read(final Reader reader, final char[] buffer, final int offset, final int length)
            throws IOException {
        checkFromIndexSize(buffer, offset, length);
        int remaining = length;
        while (remaining > 0) {
            final int location = length - remaining;
            final int count = reader.read(buffer, offset + location, remaining);
            if (EOF == count) { // EOF
                break;
            }
            remaining -= count;
        }
        return length - remaining;
    }

    
    public static void readFully(final InputStream input, final byte[] buffer) throws IOException {
        readFully(input, buffer, 0, buffer.length);
    }

    
    public static void readFully(final InputStream input, final byte[] buffer, final int offset, final int length)
            throws IOException {
        final int actual = read(input, buffer, offset, length);
        if (actual != length) {
            throw new EOFException("Length to read: " + length + " actual: " + actual);
        }
    }

    
    @Deprecated
    public static byte[] readFully(final InputStream input, final int length) throws IOException {
        return toByteArray(input, length);
    }

    
    public static void readFully(final ReadableByteChannel input, final ByteBuffer buffer) throws IOException {
        final int expected = buffer.remaining();
        final int actual = read(input, buffer);
        if (actual != expected) {
            throw new EOFException("Length to read: " + expected + " actual: " + actual);
        }
    }

    
    public static void readFully(final Reader reader, final char[] buffer) throws IOException {
        readFully(reader, buffer, 0, buffer.length);
    }

    
    public static void readFully(final Reader reader, final char[] buffer, final int offset, final int length)
            throws IOException {
        final int actual = read(reader, buffer, offset, length);
        if (actual != length) {
            throw new EOFException("Length to read: " + length + " actual: " + actual);
        }
    }

    
    public static List<String> readLines(final CharSequence csq) throws UncheckedIOException {
        try (CharSequenceReader reader = new CharSequenceReader(csq)) {
            return readLines(reader);
        }
    }

    
    @Deprecated
    public static List<String> readLines(final InputStream input) throws UncheckedIOException {
        return readLines(input, Charset.defaultCharset());
    }

    
    public static List<String> readLines(final InputStream input, final Charset charset) throws UncheckedIOException {
        return readLines(new InputStreamReader(input, Charsets.toCharset(charset)));
    }

    
    public static List<String> readLines(final InputStream input, final String charsetName) throws UncheckedIOException {
        return readLines(input, Charsets.toCharset(charsetName));
    }

    
    @SuppressWarnings("resource") // reader wraps input and is the responsibility of the caller.
    public static List<String> readLines(final Reader reader) throws UncheckedIOException {
        return toBufferedReader(reader).lines().collect(Collectors.toList());
    }

    
    public static byte[] resourceToByteArray(final String name) throws IOException {
        return resourceToByteArray(name, null);
    }

    
    public static byte[] resourceToByteArray(final String name, final ClassLoader classLoader) throws IOException {
        return toByteArray(resourceToURL(name, classLoader));
    }

    
    public static String resourceToString(final String name, final Charset charset) throws IOException {
        return resourceToString(name, charset, null);
    }

    
    public static String resourceToString(final String name, final Charset charset, final ClassLoader classLoader) throws IOException {
        return toString(resourceToURL(name, classLoader), charset);
    }

    
    public static URL resourceToURL(final String name) throws IOException {
        return resourceToURL(name, null);
    }

    
    public static URL resourceToURL(final String name, final ClassLoader classLoader) throws IOException {
        // What about the thread context class loader?
        // What about the system class loader?
        final URL resource = classLoader == null ? IOUtils.class.getResource(name) : classLoader.getResource(name);
        if (resource == null) {
            throw new IOException("Resource not found: " + name);
        }
        return resource;
    }

    
    public static long skip(final InputStream input, final long skip) throws IOException {
        try (ScratchBytes scratch = ScratchBytes.get()) {
            return skip(input, skip, scratch::array);
        }
    }

    
    public static long skip(final InputStream input, final long skip, final Supplier<byte[]> skipBufferSupplier) throws IOException {
        if (skip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + skip);
        }
        //
        // No need to synchronize access to SCRATCH_BYTE_BUFFER_WO: We don't care if the buffer is written multiple
        // times or in parallel since the data is ignored. We reuse the same buffer, if the buffer size were variable or read-write,
        // we would need to synch or use a thread local to ensure some other thread safety.
        //
        long remain = skip;
        while (remain > 0) {
            final byte[] skipBuffer = skipBufferSupplier.get();
            // See https://issues.apache.org/jira/browse/IO-203 for why we use read() rather than delegating to skip()
            final long n = input.read(skipBuffer, 0, (int) Math.min(remain, skipBuffer.length));
            if (n < 0) { // EOF
                break;
            }
            remain -= n;
        }
        return skip - remain;
    }

    
    public static long skip(final ReadableByteChannel input, final long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + toSkip);
        }
        final ByteBuffer skipByteBuffer = ByteBuffer.allocate((int) Math.min(toSkip, DEFAULT_BUFFER_SIZE));
        long remain = toSkip;
        while (remain > 0) {
            skipByteBuffer.position(0);
            skipByteBuffer.limit((int) Math.min(remain, DEFAULT_BUFFER_SIZE));
            final int n = input.read(skipByteBuffer);
            if (n == EOF) {
                break;
            }
            remain -= n;
        }
        return toSkip - remain;
    }

    
    public static long skip(final Reader reader, final long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + toSkip);
        }
        long remain = toSkip;
        try (ScratchChars scratch = IOUtils.ScratchChars.get()) {
            final char[] chars = scratch.array();
            while (remain > 0) {
                // See https://issues.apache.org/jira/browse/IO-203 for why we use read() rather than delegating to skip()
                final long n = reader.read(chars, 0, (int) Math.min(remain, chars.length));
                if (n < 0) { // EOF
                    break;
                }
                remain -= n;
            }
        }
        return toSkip - remain;
    }

    
    public static void skipFully(final InputStream input, final long toSkip) throws IOException {
        final long skipped = skip(input, toSkip);
        if (skipped != toSkip) {
            throw new EOFException("Bytes to skip: " + toSkip + " actual: " + skipped);
        }
    }

    
    public static void skipFully(final InputStream input, final long toSkip, final Supplier<byte[]> skipBufferSupplier) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Bytes to skip must not be negative: " + toSkip);
        }
        final long skipped = skip(input, toSkip, skipBufferSupplier);
        if (skipped != toSkip) {
            throw new EOFException("Bytes to skip: " + toSkip + " actual: " + skipped);
        }
    }

    
    public static void skipFully(final ReadableByteChannel input, final long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Bytes to skip must not be negative: " + toSkip);
        }
        final long skipped = skip(input, toSkip);
        if (skipped != toSkip) {
            throw new EOFException("Bytes to skip: " + toSkip + " actual: " + skipped);
        }
    }

    
    public static void skipFully(final Reader reader, final long toSkip) throws IOException {
        final long skipped = skip(reader, toSkip);
        if (skipped != toSkip) {
            throw new EOFException("Chars to skip: " + toSkip + " actual: " + skipped);
        }
    }

    
    public static InputStream toBufferedInputStream(final InputStream input) throws IOException {
        return ByteArrayOutputStream.toBufferedInputStream(input);
    }

    
    public static InputStream toBufferedInputStream(final InputStream input, final int size) throws IOException {
        return ByteArrayOutputStream.toBufferedInputStream(input, size);
    }

    
    public static BufferedReader toBufferedReader(final Reader reader) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    }

    
    public static BufferedReader toBufferedReader(final Reader reader, final int size) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader, size);
    }

    
    public static byte[] toByteArray(final InputStream inputStream) throws IOException {
        // Using SOFT_MAX_ARRAY_LENGTH guarantees that size() will not overflow
        final UnsynchronizedByteArrayOutputStream output = copyToOutputStream(inputStream, SOFT_MAX_ARRAY_LENGTH + 1, DEFAULT_BUFFER_SIZE);
        if (output.size() > SOFT_MAX_ARRAY_LENGTH) {
            throw new IOException(String.format("Cannot read more than %,d into a byte array", SOFT_MAX_ARRAY_LENGTH));
        }
        return output.toByteArray();
    }

    
    public static byte[] toByteArray(final InputStream input, final int size) throws IOException {
        return toByteArray(Objects.requireNonNull(input, "input")::read, size);
    }

    
    public static byte[] toByteArray(final InputStream input, final int size, final int chunkSize) throws IOException {
        Objects.requireNonNull(input, "input");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(String.format("chunkSize <= 0, chunkSize = %,d", chunkSize));
        }
        if (size <= chunkSize) {
            // throws if size < 0
            return toByteArray(input::read, size);
        }
        final UnsynchronizedByteArrayOutputStream output = copyToOutputStream(input, size, chunkSize);
        final int outSize = output.size();
        if (outSize != size) {
            throw new EOFException(String.format("Expected read size: %,d, actual: %,d", size, outSize));
        }
        return output.toByteArray();
    }

    
    public static byte[] toByteArray(final InputStream input, final long size) throws IOException {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(String.format("size > Integer.MAX_VALUE, size = %,d", size));
        }
        return toByteArray(input, (int) size);
    }

    
    static byte[] toByteArray(final IOTriFunction<byte[], Integer, Integer, Integer> input, final int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException(String.format("size < 0, size = %,d", size));
        }
        if (size == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        final byte[] data = byteArray(size);
        int offset = 0;
        int read;
        while (offset < size && (read = input.apply(data, offset, size - offset)) != EOF) {
            offset += read;
        }
        if (offset != size) {
            throw new EOFException(String.format("Expected read size: %,d, actual: %,d", size, offset));
        }
        return data;
    }

    
    @Deprecated
    public static byte[] toByteArray(final Reader reader) throws IOException {
        return toByteArray(reader, Charset.defaultCharset());
    }

    
    public static byte[] toByteArray(final Reader reader, final Charset charset) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(reader, output, charset);
            return output.toByteArray();
        }
    }

    
    public static byte[] toByteArray(final Reader reader, final String charsetName) throws IOException {
        return toByteArray(reader, Charsets.toCharset(charsetName));
    }

    
    @Deprecated
    public static byte[] toByteArray(final String input) {
        // make explicit the use of the default charset
        return input.getBytes(Charset.defaultCharset());
    }

    
    public static byte[] toByteArray(final URI uri) throws IOException {
        return toByteArray(uri.toURL());
    }

    
    public static byte[] toByteArray(final URL url) throws IOException {
        try (CloseableURLConnection urlConnection = CloseableURLConnection.open(url)) {
            return toByteArray(urlConnection);
        }
    }

    
    public static byte[] toByteArray(final URLConnection urlConnection) throws IOException {
        try (InputStream inputStream = urlConnection.getInputStream()) {
            return toByteArray(inputStream);
        }
    }

    
    @Deprecated
    public static char[] toCharArray(final InputStream inputStream) throws IOException {
        return toCharArray(inputStream, Charset.defaultCharset());
    }

    
    public static char[] toCharArray(final InputStream inputStream, final Charset charset)
            throws IOException {
        final CharArrayWriter writer = new CharArrayWriter();
        copy(inputStream, writer, charset);
        return writer.toCharArray();
    }

    
    public static char[] toCharArray(final InputStream inputStream, final String charsetName) throws IOException {
        return toCharArray(inputStream, Charsets.toCharset(charsetName));
    }

    
    public static char[] toCharArray(final Reader reader) throws IOException {
        final CharArrayWriter sw = new CharArrayWriter();
        copy(reader, sw);
        return sw.toCharArray();
    }

    
    @Deprecated
    public static InputStream toInputStream(final CharSequence input) {
        return toInputStream(input, Charset.defaultCharset());
    }

    
    public static InputStream toInputStream(final CharSequence input, final Charset charset) {
        return toInputStream(input.toString(), charset);
    }

    
    public static InputStream toInputStream(final CharSequence input, final String charsetName) {
        return toInputStream(input, Charsets.toCharset(charsetName));
    }

    
    @Deprecated
    public static InputStream toInputStream(final String input) {
        return toInputStream(input, Charset.defaultCharset());
    }

    
    public static InputStream toInputStream(final String input, final Charset charset) {
        return new ByteArrayInputStream(input.getBytes(Charsets.toCharset(charset)));
    }

    
    public static InputStream toInputStream(final String input, final String charsetName) {
        return new ByteArrayInputStream(input.getBytes(Charsets.toCharset(charsetName)));
    }

    
    @Deprecated
    public static String toString(final byte[] input) {
        // make explicit the use of the default charset
        return new String(input, Charset.defaultCharset());
    }

    
    public static String toString(final byte[] input, final String charsetName) {
        return new String(input, Charsets.toCharset(charsetName));
    }

    
    @Deprecated
    public static String toString(final InputStream input) throws IOException {
        return toString(input, Charset.defaultCharset());
    }

    
    public static String toString(final InputStream input, final Charset charset) throws IOException {
        try (StringBuilderWriter sw = new StringBuilderWriter()) {
            copy(input, sw, charset);
            return sw.toString();
        }
    }

    
    public static String toString(final InputStream input, final String charsetName)
            throws IOException {
        return toString(input, Charsets.toCharset(charsetName));
    }

    
    public static String toString(final IOSupplier<InputStream> input, final Charset charset) throws IOException {
        return toString(input, charset, () -> {
            throw new NullPointerException("input");
        });
    }

    
    public static String toString(final IOSupplier<InputStream> input, final Charset charset, final IOSupplier<String> defaultString) throws IOException {
        if (input == null) {
            return defaultString.get();
        }
        try (InputStream inputStream = input.get()) {
            return inputStream != null ? toString(inputStream, charset) : defaultString.get();
        }
    }

    
    public static String toString(final Reader reader) throws IOException {
        try (StringBuilderWriter sw = new StringBuilderWriter()) {
            copy(reader, sw);
            return sw.toString();
        }
    }

    
    @Deprecated
    public static String toString(final URI uri) throws IOException {
        return toString(uri, Charset.defaultCharset());
    }

    
    public static String toString(final URI uri, final Charset encoding) throws IOException {
        return toString(uri.toURL(), Charsets.toCharset(encoding));
    }

    
    public static String toString(final URI uri, final String charsetName) throws IOException {
        return toString(uri, Charsets.toCharset(charsetName));
    }

    
    @Deprecated
    public static String toString(final URL url) throws IOException {
        return toString(url, Charset.defaultCharset());
    }

    
    public static String toString(final URL url, final Charset encoding) throws IOException {
        return toString(url::openStream, encoding);
    }

    
    public static String toString(final URL url, final String charsetName) throws IOException {
        return toString(url, Charsets.toCharset(charsetName));
    }

    
    public static void write(final byte[] data, final OutputStream output)
            throws IOException {
        if (data != null) {
            output.write(data);
        }
    }

    
    @Deprecated
    public static void write(final byte[] data, final Writer writer) throws IOException {
        write(data, writer, Charset.defaultCharset());
    }

    
    public static void write(final byte[] data, final Writer writer, final Charset charset) throws IOException {
        if (data != null) {
            writer.write(new String(data, Charsets.toCharset(charset)));
        }
    }

    
    public static void write(final byte[] data, final Writer writer, final String charsetName) throws IOException {
        write(data, writer, Charsets.toCharset(charsetName));
    }

    
    @Deprecated
    public static void write(final char[] data, final OutputStream output)
            throws IOException {
        write(data, output, Charset.defaultCharset());
    }

    
    public static void write(final char[] data, final OutputStream output, final Charset charset) throws IOException {
        if (data != null) {
            write(new String(data), output, charset);
        }
    }

    
    public static void write(final char[] data, final OutputStream output, final String charsetName)
            throws IOException {
        write(data, output, Charsets.toCharset(charsetName));
    }

    
    public static void write(final char[] data, final Writer writer) throws IOException {
        if (data != null) {
            writer.write(data);
        }
    }

    
    @Deprecated
    public static void write(final CharSequence data, final OutputStream output)
            throws IOException {
        write(data, output, Charset.defaultCharset());
    }

    
    public static void write(final CharSequence data, final OutputStream output, final Charset charset)
            throws IOException {
        if (data != null) {
            write(data.toString(), output, charset);
        }
    }

    
    public static void write(final CharSequence data, final OutputStream output, final String charsetName)
            throws IOException {
        write(data, output, Charsets.toCharset(charsetName));
    }

    
    public static void write(final CharSequence data, final Writer writer) throws IOException {
        if (data != null) {
            write(data.toString(), writer);
        }
    }

    
    @Deprecated
    public static void write(final String data, final OutputStream output)
            throws IOException {
        write(data, output, Charset.defaultCharset());
    }

    
    @SuppressWarnings("resource")
    public static void write(final String data, final OutputStream output, final Charset charset) throws IOException {
        if (data != null) {
            // Use Charset#encode(String), since calling String#getBytes(Charset) might result in
            // NegativeArraySizeException or OutOfMemoryError.
            // The underlying OutputStream should not be closed, so the channel is not closed.
            Channels.newChannel(output).write(Charsets.toCharset(charset).encode(data));
        }
    }

    
    public static void write(final String data, final OutputStream output, final String charsetName)
            throws IOException {
        write(data, output, Charsets.toCharset(charsetName));
    }

    
    public static void write(final String data, final Writer writer) throws IOException {
        if (data != null) {
            writer.write(data);
        }
    }

    
    @Deprecated
    public static void write(final StringBuffer data, final OutputStream output) //NOSONAR
            throws IOException {
        write(data, output, (String) null);
    }

    
    @Deprecated
    public static void write(final StringBuffer data, final OutputStream output, final String charsetName) //NOSONAR
        throws IOException {
        if (data != null) {
            write(data.toString(), output, Charsets.toCharset(charsetName));
        }
    }

    
    @Deprecated
    public static void write(final StringBuffer data, final Writer writer) //NOSONAR
            throws IOException {
        if (data != null) {
            writer.write(data.toString());
        }
    }

    
    public static void writeChunked(final byte[] data, final OutputStream output)
            throws IOException {
        if (data != null) {
            int bytes = data.length;
            int offset = 0;
            while (bytes > 0) {
                final int chunk = Math.min(bytes, DEFAULT_BUFFER_SIZE);
                output.write(data, offset, chunk);
                bytes -= chunk;
                offset += chunk;
            }
        }
    }

    
    public static void writeChunked(final char[] data, final Writer writer) throws IOException {
        if (data != null) {
            int bytes = data.length;
            int offset = 0;
            while (bytes > 0) {
                final int chunk = Math.min(bytes, DEFAULT_BUFFER_SIZE);
                writer.write(data, offset, chunk);
                bytes -= chunk;
                offset += chunk;
            }
        }
    }

    
    @Deprecated
    public static void writeLines(final Collection<?> lines, final String lineEnding, final OutputStream output) throws IOException {
        writeLines(lines, lineEnding, output, Charset.defaultCharset());
    }

    
    public static void writeLines(final Collection<?> lines, String lineEnding, final OutputStream output, Charset charset) throws IOException {
        if (lines == null) {
            return;
        }
        if (lineEnding == null) {
            lineEnding = System.lineSeparator();
        }
        if (StandardCharsets.UTF_16.equals(charset)) {
            // don't write a BOM
            charset = StandardCharsets.UTF_16BE;
        }
        final byte[] eolBytes = lineEnding.getBytes(charset);
        for (final Object line : lines) {
            if (line != null) {
                write(line.toString(), output, charset);
            }
            output.write(eolBytes);
        }
    }

    
    public static void writeLines(final Collection<?> lines, final String lineEnding, final OutputStream output, final String charsetName) throws IOException {
        writeLines(lines, lineEnding, output, Charsets.toCharset(charsetName));
    }

    
    public static void writeLines(final Collection<?> lines, String lineEnding, final Writer writer) throws IOException {
        if (lines == null) {
            return;
        }
        if (lineEnding == null) {
            lineEnding = System.lineSeparator();
        }
        for (final Object line : lines) {
            if (line != null) {
                writer.write(line.toString());
            }
            writer.write(lineEnding);
        }
    }

    
    public static Writer writer(final Appendable appendable) {
        Objects.requireNonNull(appendable, "appendable");
        if (appendable instanceof Writer) {
            return (Writer) appendable;
        }
        if (appendable instanceof StringBuilder) {
            return new StringBuilderWriter((StringBuilder) appendable);
        }
        return new AppendableWriter<>(appendable);
    }

    
    @Deprecated
    public IOUtils() { //NOSONAR
        // empty
    }

}
