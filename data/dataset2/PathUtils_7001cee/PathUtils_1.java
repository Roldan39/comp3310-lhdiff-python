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

package org.apache.commons.io.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.RandomAccessFileMode;
import org.apache.commons.io.RandomAccessFiles;
import org.apache.commons.io.ThreadUtils;
import org.apache.commons.io.file.Counters.PathCounters;
import org.apache.commons.io.file.attribute.FileTimes;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.function.IOFunction;
import org.apache.commons.io.function.IOSupplier;


public final class PathUtils {

    
    private static final class RelativeSortedPaths {

        
        private static boolean equalsIgnoreFileSystem(final List<Path> list1, final List<Path> list2) {
            if (list1.size() != list2.size()) {
                return false;
            }
            // compare both lists using iterators
            final Iterator<Path> iterator1 = list1.iterator();
            final Iterator<Path> iterator2 = list2.iterator();
            while (iterator1.hasNext() && iterator2.hasNext()) {
                if (!equalsIgnoreFileSystem(iterator1.next(), iterator2.next())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean equalsIgnoreFileSystem(final Path path1, final Path path2) {
            final FileSystem fileSystem1 = path1.getFileSystem();
            final FileSystem fileSystem2 = path2.getFileSystem();
            if (fileSystem1 == fileSystem2) {
                return path1.equals(path2);
            }
            final String separator1 = fileSystem1.getSeparator();
            final String separator2 = fileSystem2.getSeparator();
            final String string1 = path1.toString();
            final String string2 = path2.toString();
            if (Objects.equals(separator1, separator2)) {
                // Separators are the same, so we can use toString comparison
                return Objects.equals(string1, string2);
            }
            // Compare paths from different file systems component by component.
            return extractKey(separator1, string1).equals(extractKey(separator2, string2));
        }

        static String extractKey(final String separator, final String string) {
            // Replace the file separator in a path string with a string that is not legal in a path on Windows, Linux, and macOS.
            return string.replaceAll("\\" + separator, ">");
        }

        final boolean equals;
        // final List<Path> relativeDirList1; // might need later?
        // final List<Path> relativeDirList2; // might need later?
        final List<Path> relativeFileList1;
        final List<Path> relativeFileList2;

        
        private RelativeSortedPaths(final Path dir1, final Path dir2, final int maxDepth, final LinkOption[] linkOptions,
                final FileVisitOption[] fileVisitOptions) throws IOException {
            final List<Path> tmpRelativeDirList1;
            final List<Path> tmpRelativeDirList2;
            List<Path> tmpRelativeFileList1 = null;
            List<Path> tmpRelativeFileList2 = null;
            if (dir1 == null && dir2 == null) {
                equals = true;
            } else if (dir1 == null ^ dir2 == null) {
                equals = false;
            } else {
                final boolean parentDirNotExists1 = Files.notExists(dir1, linkOptions);
                final boolean parentDirNotExists2 = Files.notExists(dir2, linkOptions);
                if (parentDirNotExists1 || parentDirNotExists2) {
                    equals = parentDirNotExists1 && parentDirNotExists2;
                } else {
                    final AccumulatorPathVisitor visitor1 = accumulate(dir1, maxDepth, fileVisitOptions);
                    final AccumulatorPathVisitor visitor2 = accumulate(dir2, maxDepth, fileVisitOptions);
                    if (visitor1.getDirList().size() != visitor2.getDirList().size() || visitor1.getFileList().size() != visitor2.getFileList().size()) {
                        equals = false;
                    } else {
                        tmpRelativeDirList1 = visitor1.relativizeDirectories(dir1, true, null);
                        tmpRelativeDirList2 = visitor2.relativizeDirectories(dir2, true, null);
                        if (!equalsIgnoreFileSystem(tmpRelativeDirList1, tmpRelativeDirList2)) {
                            equals = false;
                        } else {
                            tmpRelativeFileList1 = visitor1.relativizeFiles(dir1, true, null);
                            tmpRelativeFileList2 = visitor2.relativizeFiles(dir2, true, null);
                            equals = equalsIgnoreFileSystem(tmpRelativeFileList1, tmpRelativeFileList2);
                        }
                    }
                }
            }
            // relativeDirList1 = tmpRelativeDirList1;
            // relativeDirList2 = tmpRelativeDirList2;
            relativeFileList1 = tmpRelativeFileList1;
            relativeFileList2 = tmpRelativeFileList2;
        }
    }

    private static final OpenOption[] OPEN_OPTIONS_TRUNCATE = { StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING };
    private static final OpenOption[] OPEN_OPTIONS_APPEND = { StandardOpenOption.CREATE, StandardOpenOption.APPEND };
    
    public static final CopyOption[] EMPTY_COPY_OPTIONS = {};
    
    public static final DeleteOption[] EMPTY_DELETE_OPTION_ARRAY = {};
    
    public static final FileAttribute<?>[] EMPTY_FILE_ATTRIBUTE_ARRAY = {};
    
    public static final FileVisitOption[] EMPTY_FILE_VISIT_OPTION_ARRAY = {};
    
    public static final LinkOption[] EMPTY_LINK_OPTION_ARRAY = {};
    
    @Deprecated
    public static final LinkOption[] NOFOLLOW_LINK_OPTION_ARRAY = { LinkOption.NOFOLLOW_LINKS };
    
    static final LinkOption NULL_LINK_OPTION = null;
    
    public static final OpenOption[] EMPTY_OPEN_OPTION_ARRAY = {};
    
    public static final Path[] EMPTY_PATH_ARRAY = {};

    
    private static AccumulatorPathVisitor accumulate(final Path directory, final int maxDepth, final FileVisitOption[] fileVisitOptions) throws IOException {
        return visitFileTree(AccumulatorPathVisitor.builder().setDirectoryPostTransformer(PathUtils::stripTrailingSeparator).get(), directory,
                toFileVisitOptionSet(fileVisitOptions), maxDepth);
    }

    
    public static PathCounters cleanDirectory(final Path directory) throws IOException {
        return cleanDirectory(directory, EMPTY_DELETE_OPTION_ARRAY);
    }

    
    public static PathCounters cleanDirectory(final Path directory, final DeleteOption... deleteOptions) throws IOException {
        return visitFileTree(new CleaningPathVisitor(Counters.longPathCounters(), deleteOptions), directory).getPathCounters();
    }

    
    private static int compareLastModifiedTimeTo(final Path file, final FileTime fileTime, final LinkOption... options) throws IOException {
        return getLastModifiedTime(file, options).compareTo(fileTime);
    }

    
    public static boolean contentEquals(final FileSystem fileSystem1, final FileSystem fileSystem2) throws IOException {
        if (Objects.equals(fileSystem1, fileSystem2)) {
            return true;
        }
        final List<Path> sortedList1 = toSortedList(fileSystem1.getRootDirectories());
        final List<Path> sortedList2 = toSortedList(fileSystem2.getRootDirectories());
        if (sortedList1.size() != sortedList2.size()) {
            return false;
        }
        for (int i = 0; i < sortedList1.size(); i++) {
            if (!directoryAndFileContentEquals(sortedList1.get(i), sortedList2.get(i))) {
                return false;
            }
        }
        return true;
    }

    
    public static long copy(final IOSupplier<InputStream> in, final Path target, final CopyOption... copyOptions) throws IOException {
        try (InputStream inputStream = in.get()) {
            return Files.copy(inputStream, target, copyOptions);
        }
    }

    
    public static PathCounters copyDirectory(final Path sourceDirectory, final Path targetDirectory, final CopyOption... copyOptions) throws IOException {
        final Path absoluteSource = sourceDirectory.toAbsolutePath();
        return visitFileTree(new CopyDirectoryVisitor(Counters.longPathCounters(), absoluteSource, targetDirectory, copyOptions), absoluteSource)
                .getPathCounters();
    }

    
    public static Path copyFile(final URL sourceFile, final Path targetFile, final CopyOption... copyOptions) throws IOException {
        copy(sourceFile::openStream, targetFile, copyOptions);
        return targetFile;
    }

    
    public static Path copyFileToDirectory(final Path sourceFile, final Path targetDirectory, final CopyOption... copyOptions) throws IOException {
        // Path.resolve() naturally won't work across FileSystem unless we convert to a String
        final Path sourceFileName = Objects.requireNonNull(sourceFile.getFileName(), "source file name");
        final Path targetFile = resolve(targetDirectory, sourceFileName);
        return Files.copy(sourceFile, targetFile, copyOptions);
    }

    
    public static Path copyFileToDirectory(final URL sourceFile, final Path targetDirectory, final CopyOption... copyOptions) throws IOException {
        final Path resolve = targetDirectory.resolve(FilenameUtils.getName(sourceFile.getFile()));
        copy(sourceFile::openStream, resolve, copyOptions);
        return resolve;
    }

    
    public static PathCounters countDirectory(final Path directory) throws IOException {
        return visitFileTree(CountingPathVisitor.withLongCounters(), directory).getPathCounters();
    }

    
    public static PathCounters countDirectoryAsBigInteger(final Path directory) throws IOException {
        return visitFileTree(CountingPathVisitor.withBigIntegerCounters(), directory).getPathCounters();
    }

    
    public static Path createParentDirectories(final Path path, final FileAttribute<?>... attrs) throws IOException {
        return createParentDirectories(path, LinkOption.NOFOLLOW_LINKS, attrs);
    }

    
    public static Path createParentDirectories(final Path path, final LinkOption linkOption, final FileAttribute<?>... attrs) throws IOException {
        Path parent = getParent(path);
        parent = linkOption == LinkOption.NOFOLLOW_LINKS ? parent : readIfSymbolicLink(parent);
        if (parent == null) {
            return null;
        }
        final boolean exists = linkOption == null ? Files.exists(parent) : Files.exists(parent, linkOption);
        return exists ? parent : Files.createDirectories(parent, attrs);
    }

    
    public static Path current() {
        return Paths.get(".");
    }

    
    public static PathCounters delete(final Path path) throws IOException {
        return delete(path, EMPTY_DELETE_OPTION_ARRAY);
    }

    
    public static PathCounters delete(final Path path, final DeleteOption... deleteOptions) throws IOException {
        // File deletion through Files deletes links, not targets, so use LinkOption.NOFOLLOW_LINKS.
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? deleteDirectory(path, deleteOptions) : deleteFile(path, deleteOptions);
    }

    
    public static PathCounters delete(final Path path, final LinkOption[] linkOptions, final DeleteOption... deleteOptions) throws IOException {
        // File deletion through Files deletes links, not targets, so use LinkOption.NOFOLLOW_LINKS.
        return Files.isDirectory(path, linkOptions) ? deleteDirectory(path, linkOptions, deleteOptions) : deleteFile(path, linkOptions, deleteOptions);
    }

    
    public static PathCounters deleteDirectory(final Path directory) throws IOException {
        return deleteDirectory(directory, EMPTY_DELETE_OPTION_ARRAY);
    }

    
    public static PathCounters deleteDirectory(final Path directory, final DeleteOption... deleteOptions) throws IOException {
        final LinkOption[] linkOptions = noFollowLinkOptionArray();
        // POSIX ops will noop on non-POSIX.
        return withPosixFileAttributes(getParent(directory), linkOptions, overrideReadOnly(deleteOptions),
                pfa -> visitFileTree(new DeletingPathVisitor(Counters.longPathCounters(), linkOptions, deleteOptions), directory).getPathCounters());
    }

    
    public static PathCounters deleteDirectory(final Path directory, final LinkOption[] linkOptions, final DeleteOption... deleteOptions) throws IOException {
        return visitFileTree(new DeletingPathVisitor(Counters.longPathCounters(), linkOptions, deleteOptions), directory).getPathCounters();
    }

    
    public static PathCounters deleteFile(final Path file) throws IOException {
        return deleteFile(file, EMPTY_DELETE_OPTION_ARRAY);
    }

    
    public static PathCounters deleteFile(final Path file, final DeleteOption... deleteOptions) throws IOException {
        // Files.deleteIfExists() never follows links, so use LinkOption.NOFOLLOW_LINKS in other calls to Files.
        return deleteFile(file, noFollowLinkOptionArray(), deleteOptions);
    }

    
    public static PathCounters deleteFile(final Path file, final LinkOption[] linkOptions, final DeleteOption... deleteOptions)
            throws NoSuchFileException, IOException {
        //
        // TODO Needs clean up?
        //
        if (Files.isDirectory(file, linkOptions)) {
            throw new NoSuchFileException(file.toString());
        }
        final PathCounters pathCounts = Counters.longPathCounters();
        boolean exists = exists(file, linkOptions);
        long size = exists && !Files.isSymbolicLink(file) ? Files.size(file) : 0;
        try {
            if (Files.deleteIfExists(file)) {
                pathCounts.getFileCounter().increment();
                pathCounts.getByteCounter().add(size);
                return pathCounts;
            }
        } catch (final AccessDeniedException ignored) {
            // Ignore and try again below.
        }
        final Path parent = getParent(file);
        PosixFileAttributes posixFileAttributes = null;
        try {
            if (overrideReadOnly(deleteOptions)) {
                posixFileAttributes = readPosixFileAttributes(parent, linkOptions);
                setReadOnly(file, false, linkOptions);
            }
            // Read size _after_ having read/execute access on POSIX.
            exists = exists(file, linkOptions);
            size = exists && !Files.isSymbolicLink(file) ? Files.size(file) : 0;
            if (Files.deleteIfExists(file)) {
                pathCounts.getFileCounter().increment();
                pathCounts.getByteCounter().add(size);
            }
        } finally {
            if (posixFileAttributes != null) {
                Files.setPosixFilePermissions(parent, posixFileAttributes.permissions());
            }
        }
        return pathCounts;
    }

    
    public static void deleteOnExit(final Path path) {
        Objects.requireNonNull(path).toFile().deleteOnExit();
    }

    
    public static boolean directoryAndFileContentEquals(final Path path1, final Path path2) throws IOException {
        return directoryAndFileContentEquals(path1, path2, EMPTY_LINK_OPTION_ARRAY, EMPTY_OPEN_OPTION_ARRAY, EMPTY_FILE_VISIT_OPTION_ARRAY);
    }

    
    public static boolean directoryAndFileContentEquals(final Path path1, final Path path2, final LinkOption[] linkOptions, final OpenOption[] openOptions,
            final FileVisitOption[] fileVisitOption) throws IOException {
        // First walk both file trees and gather normalized paths.
        if (path1 == null && path2 == null) {
            return true;
        }
        if (path1 == null || path2 == null) {
            return false;
        }
        if (notExists(path1) && notExists(path2)) {
            return true;
        }
        final RelativeSortedPaths relativeSortedPaths = new RelativeSortedPaths(path1, path2, Integer.MAX_VALUE, linkOptions, fileVisitOption);
        // If the normalized path names and counts are not the same, no need to compare contents.
        if (!relativeSortedPaths.equals) {
            return false;
        }
        // Both visitors contain the same normalized paths, we can compare file contents.
        final List<Path> fileList1 = relativeSortedPaths.relativeFileList1;
        final List<Path> fileList2 = relativeSortedPaths.relativeFileList2;
        final boolean sameFileSystem = isSameFileSystem(path1, path2);
        for (final Path path : fileList1) {
            final int binarySearch = sameFileSystem ? Collections.binarySearch(fileList2, path)
                    : Collections.binarySearch(fileList2, path,
                            Comparator.comparing(p -> RelativeSortedPaths.extractKey(p.getFileSystem().getSeparator(), p.toString())));
            if (binarySearch < 0) {
                throw new IllegalStateException("Unexpected mismatch.");
            }
            if (sameFileSystem && !fileContentEquals(path1.resolve(path), path2.resolve(path), linkOptions, openOptions)) {
                return false;
            }
            if (!fileContentEquals(path1.resolve(path.toString()), path2.resolve(path.toString()), linkOptions, openOptions)) {
                return false;
            }
        }
        return true;
    }

    
    public static boolean directoryContentEquals(final Path path1, final Path path2) throws IOException {
        return directoryContentEquals(path1, path2, Integer.MAX_VALUE, EMPTY_LINK_OPTION_ARRAY, EMPTY_FILE_VISIT_OPTION_ARRAY);
    }

    
    public static boolean directoryContentEquals(final Path path1, final Path path2, final int maxDepth, final LinkOption[] linkOptions,
            final FileVisitOption[] fileVisitOptions) throws IOException {
        return new RelativeSortedPaths(path1, path2, maxDepth, linkOptions, fileVisitOptions).equals;
    }

    private static boolean exists(final Path path, final LinkOption... options) {
        return path != null && (options != null ? Files.exists(path, options) : Files.exists(path));
    }

    
    public static boolean fileContentEquals(final Path path1, final Path path2) throws IOException {
        return fileContentEquals(path1, path2, EMPTY_LINK_OPTION_ARRAY, EMPTY_OPEN_OPTION_ARRAY);
    }

    
    public static boolean fileContentEquals(final Path path1, final Path path2, final LinkOption[] linkOptions, final OpenOption[] openOptions)
            throws IOException {
        if (path1 == null && path2 == null) {
            return true;
        }
        if (path1 == null || path2 == null) {
            return false;
        }
        final Path nPath1 = path1.normalize();
        final Path nPath2 = path2.normalize();
        final boolean path1Exists = exists(nPath1, linkOptions);
        if (path1Exists != exists(nPath2, linkOptions)) {
            return false;
        }
        if (!path1Exists) {
            // Two not existing files are equal?
            // Same as FileUtils
            return true;
        }
        if (Files.isDirectory(nPath1, linkOptions)) {
            // don't compare directory contents.
            throw new IOException("Can't compare directories, only files: " + nPath1);
        }
        if (Files.isDirectory(nPath2, linkOptions)) {
            // don't compare directory contents.
            throw new IOException("Can't compare directories, only files: " + nPath2);
        }
        if (Files.size(nPath1) != Files.size(nPath2)) {
            // lengths differ, cannot be equal
            return false;
        }
        if (isSameFileSystem(path1, path2) && path1.equals(path2)) {
            // same file
            return true;
        }
        // Faster:
        try (RandomAccessFile raf1 = RandomAccessFileMode.READ_ONLY.create(path1.toRealPath(linkOptions));
                RandomAccessFile raf2 = RandomAccessFileMode.READ_ONLY.create(path2.toRealPath(linkOptions))) {
            return RandomAccessFiles.contentEquals(raf1, raf2);
        } catch (final UnsupportedOperationException e) {
            // Slower:
            // Handle
            // java.lang.UnsupportedOperationException
            // at com.sun.nio.zipfs.ZipPath.toFile(ZipPath.java:656)
            try (InputStream inputStream1 = Files.newInputStream(nPath1, openOptions);
                    InputStream inputStream2 = Files.newInputStream(nPath2, openOptions)) {
                return IOUtils.contentEquals(inputStream1, inputStream2);
            }
        }
    }

    
    public static Path[] filter(final PathFilter filter, final Path... paths) {
        Objects.requireNonNull(filter, "filter");
        if (paths == null) {
            return EMPTY_PATH_ARRAY;
        }
        return filterPaths(filter, Stream.of(paths), Collectors.toList()).toArray(EMPTY_PATH_ARRAY);
    }

    private static <R, A> R filterPaths(final PathFilter filter, final Stream<Path> stream, final Collector<? super Path, A, R> collector) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(collector, "collector");
        if (stream == null) {
            return Stream.<Path>empty().collect(collector);
        }
        return stream.filter(p -> {
            try {
                return p != null && filter.accept(p, readBasicFileAttributes(p)) == FileVisitResult.CONTINUE;
            } catch (final IOException e) {
                return false;
            }
        }).collect(collector);
    }

    
    public static List<AclEntry> getAclEntryList(final Path sourcePath) throws IOException {
        final AclFileAttributeView fileAttributeView = getAclFileAttributeView(sourcePath);
        return fileAttributeView == null ? null : fileAttributeView.getAcl();
    }

    
    public static AclFileAttributeView getAclFileAttributeView(final Path path, final LinkOption... options) {
        return Files.getFileAttributeView(path, AclFileAttributeView.class, options);
    }

    
    public static String getBaseName(final Path path) {
        if (path == null) {
            return null;
        }
        final Path fileName = path.getFileName();
        return fileName != null ? FilenameUtils.removeExtension(fileName.toString()) : null;
    }

    
    public static DosFileAttributeView getDosFileAttributeView(final Path path, final LinkOption... options) {
        return Files.getFileAttributeView(path, DosFileAttributeView.class, options);
    }

    
    public static String getExtension(final Path path) {
        final String fileName = getFileNameString(path);
        return fileName != null ? FilenameUtils.getExtension(fileName) : null;
    }

    
    public static <R> R getFileName(final Path path, final Function<Path, R> function) {
        final Path fileName = path != null ? path.getFileName() : null;
        return fileName != null ? function.apply(fileName) : null;
    }

    
    public static String getFileNameString(final Path path) {
        return getFileName(path, Path::toString);
    }

    
    public static FileTime getLastModifiedFileTime(final File file) throws IOException {
        return getLastModifiedFileTime(file.toPath(), null, EMPTY_LINK_OPTION_ARRAY);
    }

    
    public static FileTime getLastModifiedFileTime(final Path path, final FileTime defaultIfAbsent, final LinkOption... options) throws IOException {
        return Files.exists(path) ? getLastModifiedTime(path, options) : defaultIfAbsent;
    }

    
    public static FileTime getLastModifiedFileTime(final Path path, final LinkOption... options) throws IOException {
        return getLastModifiedFileTime(path, null, options);
    }

    
    public static FileTime getLastModifiedFileTime(final URI uri) throws IOException {
        return getLastModifiedFileTime(Paths.get(uri), null, EMPTY_LINK_OPTION_ARRAY);
    }

    
    public static FileTime getLastModifiedFileTime(final URL url) throws IOException, URISyntaxException {
        return getLastModifiedFileTime(url.toURI());
    }

    private static FileTime getLastModifiedTime(final Path path, final LinkOption... options) throws IOException {
        return Files.getLastModifiedTime(Objects.requireNonNull(path, "path"), options);
    }

    private static Path getParent(final Path path) {
        return path == null ? null : path.getParent();
    }

    
    public static Path getPath(final String key, final String defaultPath) {
        final String property = key != null && !key.isEmpty() ? System.getProperty(key, defaultPath) : defaultPath;
        return property != null ? Paths.get(property) : null;
    }

    
    public static PosixFileAttributeView getPosixFileAttributeView(final Path path, final LinkOption... options) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class, options);
    }

    
    public static Path getTempDirectory() {
        return Paths.get(FileUtils.getTempDirectoryPath());
    }

    
    public static boolean isDirectory(final Path path, final LinkOption... options) {
        return path != null && Files.isDirectory(path, options);
    }

    
    public static boolean isEmpty(final Path path) throws IOException {
        return Files.isDirectory(path) ? isEmptyDirectory(path) : isEmptyFile(path);
    }

    
    public static boolean isEmptyDirectory(final Path directory) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
            return !directoryStream.iterator().hasNext();
        }
    }

    
    public static boolean isEmptyFile(final Path file) throws IOException {
        return Files.size(file) <= 0;
    }

    
    public static boolean isNewer(final Path file, final ChronoZonedDateTime<?> czdt, final LinkOption... options) throws IOException {
        Objects.requireNonNull(czdt, "czdt");
        return isNewer(file, czdt.toInstant(), options);
    }

    
    public static boolean isNewer(final Path file, final FileTime fileTime, final LinkOption... options) throws IOException {
        if (notExists(file)) {
            return false;
        }
        return compareLastModifiedTimeTo(file, fileTime, options) > 0;
    }

    
    public static boolean isNewer(final Path file, final Instant instant, final LinkOption... options) throws IOException {
        return isNewer(file, FileTime.from(instant), options);
    }

    
    public static boolean isNewer(final Path file, final long timeMillis, final LinkOption... options) throws IOException {
        return isNewer(file, FileTime.fromMillis(timeMillis), options);
    }

    
    public static boolean isNewer(final Path file, final Path reference) throws IOException {
        return isNewer(file, getLastModifiedTime(reference));
    }

    
    public static boolean isOlder(final Path file, final FileTime fileTime, final LinkOption... options) throws IOException {
        if (notExists(file)) {
            return false;
        }
        return compareLastModifiedTimeTo(file, fileTime, options) < 0;
    }

    
    public static boolean isOlder(final Path file, final Instant instant, final LinkOption... options) throws IOException {
        return isOlder(file, FileTime.from(instant), options);
    }

    
    public static boolean isOlder(final Path file, final long timeMillis, final LinkOption... options) throws IOException {
        return isOlder(file, FileTime.fromMillis(timeMillis), options);
    }

    
    public static boolean isOlder(final Path file, final Path reference) throws IOException {
        return isOlder(file, getLastModifiedTime(reference));
    }

    
    public static boolean isPosix(final Path test, final LinkOption... options) {
        return exists(test, options) && readPosixFileAttributes(test, options) != null;
    }

    
    public static boolean isRegularFile(final Path path, final LinkOption... options) {
        return path != null && Files.isRegularFile(path, options);
    }

    static boolean isSameFileSystem(final Path path1, final Path path2) {
        return path1.getFileSystem() == path2.getFileSystem();
    }

    
    public static DirectoryStream<Path> newDirectoryStream(final Path dir, final PathFilter pathFilter) throws IOException {
        return Files.newDirectoryStream(dir, new DirectoryStreamFilter(pathFilter));
    }

    
    public static OutputStream newOutputStream(final Path path, final boolean append) throws IOException {
        return newOutputStream(path, EMPTY_LINK_OPTION_ARRAY, append ? OPEN_OPTIONS_APPEND : OPEN_OPTIONS_TRUNCATE);
    }

    static OutputStream newOutputStream(final Path path, final LinkOption[] linkOptions, final OpenOption... openOptions) throws IOException {
        if (!exists(path, linkOptions)) {
            createParentDirectories(path, linkOptions != null && linkOptions.length > 0 ? linkOptions[0] : NULL_LINK_OPTION);
        }
        final List<OpenOption> list = new ArrayList<>(Arrays.asList(openOptions != null ? openOptions : EMPTY_OPEN_OPTION_ARRAY));
        list.addAll(Arrays.asList(linkOptions != null ? linkOptions : EMPTY_LINK_OPTION_ARRAY));
        return Files.newOutputStream(path, list.toArray(EMPTY_OPEN_OPTION_ARRAY));
    }

    
    public static LinkOption[] noFollowLinkOptionArray() {
        return NOFOLLOW_LINK_OPTION_ARRAY.clone();
    }

    private static boolean notExists(final Path path, final LinkOption... options) {
        return Files.notExists(Objects.requireNonNull(path, "path"), options);
    }

    
    private static boolean overrideReadOnly(final DeleteOption... deleteOptions) {
        if (deleteOptions == null) {
            return false;
        }
        return Stream.of(deleteOptions).anyMatch(e -> e == StandardDeleteOption.OVERRIDE_READ_ONLY);
    }

    
    public static <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type, final LinkOption... options) {
        try {
            return path == null ? null : Files.readAttributes(path, type, options);
        } catch (final UnsupportedOperationException | IOException e) {
            // For example, on Windows.
            return null;
        }
    }

    
    public static BasicFileAttributes readBasicFileAttributes(final Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class);
    }

    
    public static BasicFileAttributes readBasicFileAttributes(final Path path, final LinkOption... options) {
        return readAttributes(path, BasicFileAttributes.class, options);
    }

    
    @Deprecated
    public static BasicFileAttributes readBasicFileAttributesUnchecked(final Path path) {
        return readBasicFileAttributes(path, EMPTY_LINK_OPTION_ARRAY);
    }

    
    public static DosFileAttributes readDosFileAttributes(final Path path, final LinkOption... options) {
        return readAttributes(path, DosFileAttributes.class, options);
    }

    private static Path readIfSymbolicLink(final Path path) throws IOException {
        return path != null ? Files.isSymbolicLink(path) ? Files.readSymbolicLink(path) : path : null;
    }

    
    public static BasicFileAttributes readOsFileAttributes(final Path path, final LinkOption... options) {
        final PosixFileAttributes fileAttributes = readPosixFileAttributes(path, options);
        return fileAttributes != null ? fileAttributes : readDosFileAttributes(path, options);
    }

    
    public static PosixFileAttributes readPosixFileAttributes(final Path path, final LinkOption... options) {
        return readAttributes(path, PosixFileAttributes.class, options);
    }

    
    public static String readString(final Path path, final Charset charset) throws IOException {
        return new String(Files.readAllBytes(path), Charsets.toCharset(charset));
    }

    
    static List<Path> relativize(final Collection<Path> collection, final Path parent, final boolean sort, final Comparator<? super Path> comparator) {
        Stream<Path> stream = collection.stream().map(parent::relativize);
        if (sort) {
            stream = comparator == null ? stream.sorted() : stream.sorted(comparator);
        }
        return stream.collect(Collectors.toList());
    }

    
    private static Path requireExists(final Path file, final String fileParamName, final LinkOption... options) {
        Objects.requireNonNull(file, fileParamName);
        if (!exists(file, options)) {
            throw new IllegalArgumentException("File system element for parameter '" + fileParamName + "' does not exist: '" + file + "'");
        }
        return file;
    }

    static Path resolve(final Path targetDirectory, final Path otherPath) {
        final FileSystem fileSystemTarget = targetDirectory.getFileSystem();
        final FileSystem fileSystemSource = otherPath.getFileSystem();
        if (fileSystemTarget == fileSystemSource) {
            return targetDirectory.resolve(otherPath);
        }
        final String separatorSource = fileSystemSource.getSeparator();
        final String separatorTarget = fileSystemTarget.getSeparator();
        final String otherString = otherPath.toString();
        return targetDirectory.resolve(Objects.equals(separatorSource, separatorTarget) ? otherString : otherString.replace(separatorSource, separatorTarget));
    }

    private static boolean setDosReadOnly(final Path path, final boolean readOnly, final LinkOption... linkOptions) throws IOException {
        final DosFileAttributeView dosFileAttributeView = getDosFileAttributeView(path, linkOptions);
        if (dosFileAttributeView != null) {
            dosFileAttributeView.setReadOnly(readOnly);
            return true;
        }
        return false;
    }

    
    public static void setLastModifiedTime(final Path sourceFile, final Path targetFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Files.setLastModifiedTime(targetFile, getLastModifiedTime(sourceFile));
    }

    
    private static boolean setPosixDeletePermissions(final Path parent, final boolean enableDeleteChildren, final LinkOption... linkOptions)
            throws IOException {
        // To delete a file in POSIX, you need write and execute permissions on its parent directory.
        // @formatter:off
        return setPosixPermissions(parent, enableDeleteChildren, Arrays.asList(
            PosixFilePermission.OWNER_WRITE,
            //PosixFilePermission.GROUP_WRITE,
            //PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OWNER_EXECUTE
            //PosixFilePermission.GROUP_EXECUTE,
            //PosixFilePermission.OTHERS_EXECUTE
            ), linkOptions);
        // @formatter:on
    }

    
    private static boolean setPosixPermissions(final Path path, final boolean addPermissions, final List<PosixFilePermission> updatePermissions,
            final LinkOption... linkOptions) throws IOException {
        if (path != null) {
            final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, linkOptions);
            final Set<PosixFilePermission> newPermissions = new HashSet<>(permissions);
            if (addPermissions) {
                newPermissions.addAll(updatePermissions);
            } else {
                newPermissions.removeAll(updatePermissions);
            }
            if (!newPermissions.equals(permissions)) {
                Files.setPosixFilePermissions(path, newPermissions);
            }
            return true;
        }
        return false;
    }

    private static void setPosixReadOnlyFile(final Path path, final boolean readOnly, final LinkOption... linkOptions) throws IOException {
        // Not Windows 10
        final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, linkOptions);
        // @formatter:off
        final List<PosixFilePermission> readPermissions = Arrays.asList(
                PosixFilePermission.OWNER_READ
                //PosixFilePermission.GROUP_READ,
                //PosixFilePermission.OTHERS_READ
            );
        final List<PosixFilePermission> writePermissions = Arrays.asList(
                PosixFilePermission.OWNER_WRITE
                //PosixFilePermission.GROUP_WRITE,
                //PosixFilePermission.OTHERS_WRITE
            );
        // @formatter:on
        if (readOnly) {
            // RO: We can read, we cannot write.
            permissions.addAll(readPermissions);
            permissions.removeAll(writePermissions);
        } else {
            // Not RO: We can read, we can write.
            permissions.addAll(readPermissions);
            permissions.addAll(writePermissions);
        }
        Files.setPosixFilePermissions(path, permissions);
    }

    
    public static Path setReadOnly(final Path path, final boolean readOnly, final LinkOption... linkOptions) throws IOException {
        try {
            // Windows is simplest
            if (setDosReadOnly(path, readOnly, linkOptions)) {
                return path;
            }
        } catch (final IOException ignored) {
            // Retry with POSIX below.
        }
        final Path parent = getParent(path);
        if (!isPosix(parent, linkOptions)) { // Test parent because we may not the permissions to test the file.
            throw new IOException(String.format("DOS or POSIX file operations not available for '%s', linkOptions %s", path, Arrays.toString(linkOptions)));
        }
        // POSIX
        if (readOnly) {
            // RO
            // File, then parent dir (if any).
            setPosixReadOnlyFile(path, readOnly, linkOptions);
            setPosixDeletePermissions(parent, false, linkOptions);
        } else {
            // RE
            // Parent dir (if any), then file.
            setPosixDeletePermissions(parent, true, linkOptions);
        }
        return path;
    }

    
    public static long sizeOf(final Path path) throws IOException {
        requireExists(path, "path");
        return Files.isDirectory(path) ? sizeOfDirectory(path) : Files.size(path);
    }

    
    public static BigInteger sizeOfAsBigInteger(final Path path) throws IOException {
        requireExists(path, "path");
        return Files.isDirectory(path) ? sizeOfDirectoryAsBigInteger(path) : BigInteger.valueOf(Files.size(path));
    }

    
    public static long sizeOfDirectory(final Path directory) throws IOException {
        return countDirectory(directory).getByteCounter().getLong();
    }

    
    public static BigInteger sizeOfDirectoryAsBigInteger(final Path directory) throws IOException {
        return countDirectoryAsBigInteger(directory).getByteCounter().getBigInteger();
    }

    private static Path stripTrailingSeparator(final Path dir) {
        final String separator = dir.getFileSystem().getSeparator();
        final String fileName = getFileNameString(dir);
        return fileName != null && fileName.endsWith(separator) ? dir.resolveSibling(fileName.substring(0, fileName.length() - 1)) : dir;
    }

    
    static Set<FileVisitOption> toFileVisitOptionSet(final FileVisitOption... fileVisitOptions) {
        return fileVisitOptions == null ? EnumSet.noneOf(FileVisitOption.class) : Stream.of(fileVisitOptions).collect(Collectors.toSet());
    }

    private static <T> List<T> toList(final Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }

    private static List<Path> toSortedList(final Iterable<Path> rootDirectories) {
        final List<Path> list = toList(rootDirectories);
        Collections.sort(list);
        return list;
    }

    
    public static Path touch(final Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) {
            createParentDirectories(file);
            Files.createFile(file);
        } else {
            FileTimes.setLastModifiedTime(file);
        }
        return file;
    }

    
    public static <T extends FileVisitor<? super Path>> T visitFileTree(final T visitor, final Path directory) throws IOException {
        Files.walkFileTree(directory, visitor);
        return visitor;
    }

    
    public static <T extends FileVisitor<? super Path>> T visitFileTree(final T visitor, final Path start, final Set<FileVisitOption> options,
            final int maxDepth) throws IOException {
        Files.walkFileTree(start, options, maxDepth, visitor);
        return visitor;
    }

    
    public static <T extends FileVisitor<? super Path>> T visitFileTree(final T visitor, final String first, final String... more) throws IOException {
        return visitFileTree(visitor, Paths.get(first, more));
    }

    
    public static <T extends FileVisitor<? super Path>> T visitFileTree(final T visitor, final URI uri) throws IOException {
        return visitFileTree(visitor, Paths.get(uri));
    }

    
    public static boolean waitFor(final Path file, final Duration timeout, final LinkOption... options) {
        Objects.requireNonNull(file, "file");
        final Instant finishInstant = Instant.now().plus(timeout);
        boolean interrupted = false;
        final long minSleepMillis = 100;
        try {
            while (!exists(file, options)) {
                final Instant now = Instant.now();
                if (now.isAfter(finishInstant)) {
                    return false;
                }
                try {
                    ThreadUtils.sleep(Duration.ofMillis(Math.min(minSleepMillis, finishInstant.minusMillis(now.toEpochMilli()).toEpochMilli())));
                } catch (final InterruptedException ignore) {
                    interrupted = true;
                } catch (final Exception ex) {
                    break;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return exists(file, options);
    }

    
    @SuppressWarnings("resource") // Caller closes
    public static Stream<Path> walk(final Path start, final PathFilter pathFilter, final int maxDepth, final boolean readAttributes,
            final FileVisitOption... options) throws IOException {
        return Files.walk(start, maxDepth, options).filter(
                path -> pathFilter.accept(path, readAttributes ? readBasicFileAttributes(path, EMPTY_LINK_OPTION_ARRAY) : null) == FileVisitResult.CONTINUE);
    }

    private static <R> R withPosixFileAttributes(final Path path, final LinkOption[] linkOptions, final boolean overrideReadOnly,
            final IOFunction<PosixFileAttributes, R> function) throws IOException {
        final PosixFileAttributes posixFileAttributes = overrideReadOnly ? readPosixFileAttributes(path, linkOptions) : null;
        try {
            return function.apply(posixFileAttributes);
        } finally {
            if (posixFileAttributes != null && path != null && Files.exists(path, linkOptions)) {
                Files.setPosixFilePermissions(path, posixFileAttributes.permissions());
            }
        }
    }

    
    public static Path writeString(final Path path, final CharSequence charSequence, final Charset charset, final OpenOption... openOptions)
            throws IOException {
        // Check the text is not null before opening file.
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(charSequence, "charSequence");
        Files.write(path, String.valueOf(charSequence).getBytes(Charsets.toCharset(charset)), openOptions);
        return path;
    }

    
    private PathUtils() {
        // do not instantiate.
    }
}
