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

package org.apache.commons.io.build;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IORandomAccessFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.RandomAccessFileMode;
import org.apache.commons.io.RandomAccessFiles;
import org.apache.commons.io.channels.ByteArraySeekableByteChannel;
import org.apache.commons.io.input.BufferedFileChannelInputStream;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.io.output.RandomAccessFileOutputStream;
import org.apache.commons.io.output.WriterOutputStream;


public abstract class AbstractOrigin<T, B extends AbstractOrigin<T, B>> extends AbstractSupplier<T, B> {

    
    public abstract static class AbstractRandomAccessFileOrigin<T extends RandomAccessFile, B extends AbstractRandomAccessFileOrigin<T, B>>
            extends AbstractOrigin<T, B> {

        
        public AbstractRandomAccessFileOrigin(final T origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray() throws IOException {
            final long longLen = origin.length();
            if (longLen > Integer.MAX_VALUE) {
                throw new IllegalStateException("Origin too large.");
            }
            return RandomAccessFiles.read(origin, 0, (int) longLen);
        }

        @Override
        public byte[] getByteArray(final long position, final int length) throws IOException {
            return RandomAccessFiles.read(origin, position, length);
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return getRandomAccessFile(options).getChannel();
        }

        @Override
        public CharSequence getCharSequence(final Charset charset) throws IOException {
            return new String(getByteArray(), charset);
        }

        @SuppressWarnings("resource")
        @Override
        public InputStream getInputStream(final OpenOption... options) throws IOException {
            return BufferedFileChannelInputStream.builder().setFileChannel(origin.getChannel()).get();
        }

        @Override
        public OutputStream getOutputStream(final OpenOption... options) throws IOException {
            return RandomAccessFileOutputStream.builder().setRandomAccessFile(origin).get();
        }

        @Override
        public T getRandomAccessFile(final OpenOption... openOption) {
            // No conversion
            return get();
        }

        @Override
        public Reader getReader(final Charset charset) throws IOException {
            return new InputStreamReader(getInputStream(), Charsets.toCharset(charset));
        }

        @Override
        public Writer getWriter(final Charset charset, final OpenOption... options) throws IOException {
            return new OutputStreamWriter(getOutputStream(options), Charsets.toCharset(charset));
        }

        @Override
        public long size() throws IOException {
            return origin.length();
        }
    }

    
    public static class ByteArrayOrigin extends AbstractOrigin<byte[], ByteArrayOrigin> {

        
        public ByteArrayOrigin(final byte[] origin) {
            super(origin);
        }

        
        @Override
        public byte[] getByteArray() {
            // No conversion
            return get();
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            for (final OpenOption option : options) {
                if (option == StandardOpenOption.WRITE) {
                    throw new UnsupportedOperationException("Only READ is supported for byte[] origins: " + Arrays.toString(options));
                }
            }
            return ByteArraySeekableByteChannel.wrap(getByteArray());
        }

        
        @Override
        public InputStream getInputStream(final OpenOption... options) throws IOException {
            return new ByteArrayInputStream(origin);
        }

        @Override
        public Reader getReader(final Charset charset) throws IOException {
            return new InputStreamReader(getInputStream(), Charsets.toCharset(charset));
        }

        @Override
        public long size() throws IOException {
            return origin.length;
        }

    }

    
    public static class ChannelOrigin extends AbstractOrigin<Channel, ChannelOrigin> {

        
        public ChannelOrigin(final Channel origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray() throws IOException {
            return IOUtils.toByteArray(getInputStream());
        }

        
        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            // No conversion
            return get();
        }

        @Override
        public InputStream getInputStream(final OpenOption... options) throws IOException {
            return Channels.newInputStream(getChannel(ReadableByteChannel.class, options));
        }

        @Override
        public OutputStream getOutputStream(final OpenOption... options) throws IOException {
            return Channels.newOutputStream(getChannel(WritableByteChannel.class, options));
        }

        @Override
        public Reader getReader(final Charset charset) throws IOException {
            return Channels.newReader(
                    getChannel(ReadableByteChannel.class),
                    Charsets.toCharset(charset).newDecoder(),
                    -1);
        }

        @Override
        public Writer getWriter(final Charset charset, final OpenOption... options) throws IOException {
            return Channels.newWriter(getChannel(WritableByteChannel.class, options), Charsets.toCharset(charset).newEncoder(), -1);
        }

        @Override
        public long size() throws IOException {
            if (origin instanceof SeekableByteChannel) {
                return ((SeekableByteChannel) origin).size();
            }
            throw unsupportedOperation("size");
        }
    }

    
    public static class CharSequenceOrigin extends AbstractOrigin<CharSequence, CharSequenceOrigin> {

        
        public CharSequenceOrigin(final CharSequence origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray() {
            // TODO Pass in a Charset? Consider if call sites actually need this.
            return origin.toString().getBytes(Charset.defaultCharset());
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            for (final OpenOption option : options) {
                if (option == StandardOpenOption.WRITE) {
                    throw new UnsupportedOperationException("Only READ is supported for CharSequence origins: " + Arrays.toString(options));
                }
            }
            return ByteArraySeekableByteChannel.wrap(getByteArray());
        }

        
        @Override
        public CharSequence getCharSequence(final Charset charset) {
            // No conversion
            return get();
        }

        
        @Override
        public InputStream getInputStream(final OpenOption... options) throws IOException {
            // TODO Pass in a Charset? Consider if call sites actually need this.
            return CharSequenceInputStream.builder().setCharSequence(getCharSequence(Charset.defaultCharset())).get();
        }

        
        @Override
        public Reader getReader(final Charset charset) throws IOException {
            return new CharSequenceReader(get());
        }

        @Override
        public long size() throws IOException {
            return origin.length();
        }

    }

    
    public static class FileOrigin extends AbstractOrigin<File, FileOrigin> {

        
        public FileOrigin(final File origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray(final long position, final int length) throws IOException {
            try (RandomAccessFile raf = RandomAccessFileMode.READ_ONLY.create(origin)) {
                return RandomAccessFiles.read(raf, position, length);
            }
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return Files.newByteChannel(getPath(), options);
        }

        
        @Override
        public File getFile() {
            // No conversion
            return get();
        }

        @Override
        public Path getPath() {
            return get().toPath();
        }
    }

    
    public static class InputStreamOrigin extends AbstractOrigin<InputStream, InputStreamOrigin> {

        
        public InputStreamOrigin(final InputStream origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray() throws IOException {
            return IOUtils.toByteArray(origin);
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return Channels.newChannel(getInputStream(options));
        }

        
        @Override
        public InputStream getInputStream(final OpenOption... options) {
            // No conversion
            return get();
        }

        @Override
        public Reader getReader(final Charset charset) throws IOException {
            return new InputStreamReader(getInputStream(), Charsets.toCharset(charset));
        }

        @Override
        public long size() throws IOException {
            if (origin instanceof FileInputStream) {
                return ((FileInputStream) origin).getChannel().size();
            }
            throw unsupportedOperation("size");
        }
    }

    
    public static class IORandomAccessFileOrigin extends AbstractRandomAccessFileOrigin<IORandomAccessFile, IORandomAccessFileOrigin> {

        
        public IORandomAccessFileOrigin(final IORandomAccessFile origin) {
            super(origin);
        }

        @SuppressWarnings("resource")
        @Override
        public File getFile() {
            return get().getFile();
        }

        @Override
        public Path getPath() {
            return getFile().toPath();
        }

    }

    
    public static class OutputStreamOrigin extends AbstractOrigin<OutputStream, OutputStreamOrigin> {

        
        public OutputStreamOrigin(final OutputStream origin) {
            super(origin);
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return Channels.newChannel(getOutputStream(options));
        }

        
        @Override
        public OutputStream getOutputStream(final OpenOption... options) {
            // No conversion
            return get();
        }

        
        @Override
        public Writer getWriter(final Charset charset, final OpenOption... options) throws IOException {
            return new OutputStreamWriter(origin, Charsets.toCharset(charset));
        }
    }

    
    public static class PathOrigin extends AbstractOrigin<Path, PathOrigin> {

        
        public PathOrigin(final Path origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray(final long position, final int length) throws IOException {
            return RandomAccessFileMode.READ_ONLY.apply(origin, raf -> RandomAccessFiles.read(raf, position, length));
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return Files.newByteChannel(getPath(), options);
        }

        @Override
        public File getFile() {
            return get().toFile();
        }

        
        @Override
        public Path getPath() {
            // No conversion
            return get();
        }
    }

    
    public static class RandomAccessFileOrigin extends AbstractRandomAccessFileOrigin<RandomAccessFile, RandomAccessFileOrigin> {

        
        public RandomAccessFileOrigin(final RandomAccessFile origin) {
            super(origin);
        }

    }

    
    public static class ReaderOrigin extends AbstractOrigin<Reader, ReaderOrigin> {

        
        public ReaderOrigin(final Reader origin) {
            super(origin);
        }

        @Override
        public byte[] getByteArray() throws IOException {
            // TODO Pass in a Charset? Consider if call sites actually need this.
            return IOUtils.toByteArray(origin, Charset.defaultCharset());
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return Channels.newChannel(getInputStream());
        }

        
        @Override
        public CharSequence getCharSequence(final Charset charset) throws IOException {
            return IOUtils.toString(origin);
        }

        
        @Override
        public InputStream getInputStream(final OpenOption... options) throws IOException {
            // TODO Pass in a Charset? Consider if call sites actually need this.
            return ReaderInputStream.builder().setReader(origin).setCharset(Charset.defaultCharset()).get();
        }

        
        @Override
        public Reader getReader(final Charset charset) throws IOException {
            // No conversion
            return get();
        }
    }

    
    public static class URIOrigin extends AbstractOrigin<URI, URIOrigin> {

        private static final String SCHEME_HTTPS = "https";
        private static final String SCHEME_HTTP = "http";

        
        public URIOrigin(final URI origin) {
            super(origin);
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            final URI uri = get();
            final String scheme = uri.getScheme();
            if (SCHEME_HTTP.equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
                return Channels.newChannel(uri.toURL().openStream());
            }
            return Files.newByteChannel(getPath(), options);
        }

        @Override
        public File getFile() {
            return getPath().toFile();
        }

        @Override
        public InputStream getInputStream(final OpenOption... options) throws IOException {
            final URI uri = get();
            final String scheme = uri.getScheme();
            if (SCHEME_HTTP.equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
                return uri.toURL().openStream();
            }
            return Files.newInputStream(getPath(), options);
        }

        @Override
        public Path getPath() {
            return Paths.get(get());
        }
    }

    
    public static class WriterOrigin extends AbstractOrigin<Writer, WriterOrigin> {

        
        public WriterOrigin(final Writer origin) {
            super(origin);
        }

        @Override
        protected Channel getChannel(final OpenOption... options) throws IOException {
            return Channels.newChannel(getOutputStream());
        }

        
        @Override
        public OutputStream getOutputStream(final OpenOption... options) throws IOException {
            // TODO Pass in a Charset? Consider if call sites actually need this.
            return WriterOutputStream.builder().setWriter(origin).setCharset(Charset.defaultCharset()).get();
        }

        
        @Override
        public Writer getWriter(final Charset charset, final OpenOption... options) throws IOException {
            // No conversion
            return get();
        }
    }

    
    final T origin;

    
    protected AbstractOrigin(final T origin) {
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    
    @Override
    public T get() {
        return origin;
    }

    
    public byte[] getByteArray() throws IOException {
        return Files.readAllBytes(getPath());
    }

    
    public byte[] getByteArray(final long position, final int length) throws IOException {
        final byte[] bytes = getByteArray();
        // Checks for int overflow.
        final int start = Math.toIntExact(position);
        if (start < 0 || length < 0 || start + length < 0 || start + length > bytes.length) {
            throw new IllegalArgumentException("Couldn't read array (start: " + start + ", length: " + length + ", data length: " + bytes.length + ").");
        }
        return Arrays.copyOfRange(bytes, start, start + length);
    }

    
    public final <C extends Channel> C getChannel(final Class<C> channelType, final OpenOption... options) throws IOException {
        Objects.requireNonNull(channelType, "channelType");
        final Channel channel = getChannel(options);
        if (channelType.isInstance(channel)) {
            return channelType.cast(channel);
        }
        throw unsupportedChannelType(channelType);
    }

    
    protected Channel getChannel(final OpenOption... options) throws IOException {
        throw unsupportedOperation("getChannel");
    }

    
    public CharSequence getCharSequence(final Charset charset) throws IOException {
        return new String(getByteArray(), charset);
    }

    
    public File getFile() {
        throw unsupportedOperation("getFile");
    }

    
    public InputStream getInputStream(final OpenOption... options) throws IOException {
        return Files.newInputStream(getPath(), options);
    }

    
    public OutputStream getOutputStream(final OpenOption... options) throws IOException {
        return Files.newOutputStream(getPath(), options);
    }

    
    public Path getPath() {
        throw unsupportedOperation("getPath");
    }

    
    public RandomAccessFile getRandomAccessFile(final OpenOption... openOption) throws FileNotFoundException {
        return RandomAccessFileMode.valueOf(openOption).create(getFile());
    }

    
    public Reader getReader(final Charset charset) throws IOException {
        return Files.newBufferedReader(getPath(), Charsets.toCharset(charset));
    }

    
    private String getSimpleClassName() {
        return getClass().getSimpleName();
    }

    
    public Writer getWriter(final Charset charset, final OpenOption... options) throws IOException {
        return Files.newBufferedWriter(getPath(), Charsets.toCharset(charset), options);
    }

    
    public long size() throws IOException {
        return Files.size(getPath());
    }

    @Override
    public String toString() {
        return getSimpleClassName() + "[" + origin.toString() + "]";
    }

    UnsupportedOperationException unsupportedChannelType(final Class<? extends Channel> channelType) {
        return new UnsupportedOperationException(String.format(
                "%s#getChannel(%s) for %s origin %s",
                getSimpleClassName(),
                channelType.getSimpleName(),
                origin.getClass().getSimpleName(),
                origin));
    }

    UnsupportedOperationException unsupportedOperation(final String method) {
        return new UnsupportedOperationException(String.format(
                "%s#%s() for %s origin %s",
                getSimpleClassName(), method, origin.getClass().getSimpleName(), origin));
    }
}
