/*
 * Copyright (c) 2013-2015 Cinchapi, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cinchapi.concourse.server.io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import org.cinchapi.concourse.util.Logger;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

import static com.google.common.base.Preconditions.checkState;

/**
 * Interface to the underlying filesystem which provides methods to perform file
 * based operations without having to deal with the annoyance of checked
 * exceptions or the awkward {@link Path} API. Using this class will help
 * produce more streamlined and readable code.
 * 
 * @author Jeff Nelson
 */
public final class FileSystem {

    /**
     * Close the {@code channel} without throwing a checked exception. If, for
     * some reason, this can't be done the underlying IOException will be
     * re-thrown as a runtime exception.
     * 
     * @param channel
     */
    public static void closeFileChannel(FileChannel channel) {
        try {
            channel.close();
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Copy all the bytes {@code from} one file to {to} another.
     * 
     * @param from
     * @param to
     */
    public static void copyBytes(String from, String to) {
        try {
            Files.copy(Paths.get(from), Files.newOutputStream(Paths.get(to)));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Delete {@code directory}. If files are added to the directory while its
     * being deleted, this method will make a best effort to delete those files
     * as well.
     * 
     * @param directory
     */
    public static void deleteDirectory(String directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths
                .get(directory))) {
            for (Path path : stream) {
                if(Files.isDirectory(path)) {
                    deleteDirectory(path.toString());
                }
                else {
                    Files.delete(path);
                }
            }
            Files.delete(Paths.get(directory));
        }
        catch (IOException e) {
            if(e.getClass() == DirectoryNotEmptyException.class) {
                Logger.warn("It appears that data was added to directory "
                        + "{} while trying to perform a deletion. "
                        + "Trying again...", directory);
                deleteDirectory(directory);
            }
            else {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Delete the {@code file}.
     * 
     * @param file
     */
    public static void deleteFile(String file) {
        try {
            java.nio.file.Files.delete(Paths.get(file));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Expand the given {@code path} so that it contains completely normalized
     * components (e.g. ".", "..", and "~" are resolved to the correct absolute
     * paths).
     * 
     * @param path
     * @return the expanded path
     */
    public static String expandPath(String path) {
        path = path.replaceAll("~", USER_HOME);
        return BASE_PATH.resolve(path).normalize().toString();
    }

    /**
     * Return the random access {@link FileChannel} for {@code file}. The
     * channel will be opened for reading and writing.
     * 
     * @param file
     * @return the FileChannel for {@code file}
     */
    public static FileChannel getFileChannel(String file) {
        try {
            return new RandomAccessFile(openFile(file), "rwd").getChannel();
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            throw Throwables.propagate(e);
        }
    }

    /**
     * Return the size of {@code file}. This method will automatically create
     * {@code file} if it does not already exist.
     * 
     * @param file
     * @return the size in bytes
     */
    public static long getFileSize(String file) {
        try {
            openFile(file);
            return Files.size(Paths.get(file));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Return the simple filename without path information or extension. This
     * method assumes that the filename only contains one extension.
     * 
     * @param filename
     * @return the simple file name
     */
    public static String getSimpleName(String filename) {
        String[] placeholder;
        return (placeholder = (placeholder = filename.split("\\."))[placeholder.length - 2]
                .split(File.separator))[placeholder.length - 1];
    }

    /**
     * Look through {@code dir} and return all the sub directories.
     * 
     * @param dir
     * @return the sub directories under {@code dir}.
     */
    public static Set<String> getSubDirs(String dir) {
        File directory = new File(dir);
        File[] files = directory.listFiles();
        Set<String> subDirs = Sets.newHashSet();
        for (File file : files) {
            if(Files.isDirectory(Paths.get(file.getAbsolutePath()))) {
                subDirs.add(file.getName());
            }
        }
        return subDirs;
    }

    /**
     * Return the home directory of the parent process for this JVM.
     * 
     * @return the home directory
     */
    public static String getUserHome() {
        return USER_HOME;
    }

    /**
     * Get the working directory of this JVM, which is the directory from which
     * the process is launched.
     * 
     * @return the working directory
     */
    public static String getWorkingDirectory() {
        return WORKING_DIRECTORY;
    }

    /**
     * Return {@code true} in the filesystem contains {@code dir} and it is
     * a directory.
     * 
     * @param file
     * @return {@code true} if {@code dir} exists
     */
    public static boolean hasDir(String dir) {
        Path path = Paths.get(dir);
        return Files.exists(path) && Files.isDirectory(path);
    }

    /**
     * Return {@code true} in the filesystem contains {@code file} and it is not
     * a directory.
     * 
     * @param file
     * @return {@code true} if {@code file} exists
     */
    public static boolean hasFile(String file) {
        Path path = Paths.get(file);
        return Files.exists(path) && !Files.isDirectory(path);
    }

    /**
     * Lock the file or directory specified in {@code path} for use in this JVM
     * process. If the lock cannot be acquired, an exception is thrown.
     * 
     * @param path
     */
    public static void lock(String path) {
        if(Files.isDirectory(Paths.get(path))) {
            lock(path + File.separator + "concourse.lock");
        }
        else {
            try {
                checkState(getFileChannel(path).tryLock() != null,
                        "Unable to grab lock for %s because another "
                                + "Concourse Server process is using it", path);
            }
            catch (OverlappingFileLockException e) {
                Logger.warn("Trying to lock {}, but the current "
                        + "JVM is already the owner", path);
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Create a valid path that contains sepearators in the appropriate places
     * by joining all the {@link parts} together with the {@link File#separator}
     * 
     * @param parts
     * @return the path
     */
    public static String makePath(String... parts) {
        StringBuilder path = new StringBuilder();
        for (String part : parts) {
            path.append(part).append(File.separator);
        }
        return path.toString();
    }

    /**
     * Return a {@link MappedByteBuffer} for {@code file} in {@code mode}
     * starting at {@code position} and continuing for {@code size} bytes. This
     * method will automatically create {@code file} if it does not already
     * exist.
     * 
     * @param file
     * @param mode
     * @param position
     * @param size
     * @return the MappedByteBuffer
     */
    public static MappedByteBuffer map(String file, MapMode mode,
            long position, long size) {
        FileChannel channel = getFileChannel(file);
        try {
            return channel.map(mode, position, size);
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        finally {
            closeFileChannel(channel);
        }
    }

    /**
     * Create the directories in {@link path}.
     * 
     * @param path
     */
    public static void mkdirs(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Open {@code file} and return a {@link File} handle. This method will
     * create a new file if and only if it does not already exist.
     * 
     * @param file
     */
    public static File openFile(String file) {
        try {
            File f = new File(file);
            if(f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            f.createNewFile();
            return f;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Read bytes from {@code file} <em>sequentially</em> and return the content
     * as a <strong>read only</strong> {@link ByteBuffer}.
     * 
     * @param file
     * @return the read only ByteBuffer with the content of {@code file}
     */
    public static ByteBuffer readBytes(String file) {
        FileChannel channel = getFileChannel(file);
        try {
            MappedByteBuffer data = channel.map(MapMode.READ_ONLY, 0,
                    channel.size());
            return data;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
        finally {
            closeFileChannel(channel);
        }
    }

    /**
     * Replace the content of {@code original} with that of {@code replacement}
     * and delete {@code replacement} in a single atomic operation.
     * 
     * @param original
     * @param replacement
     */
    public static void replaceFile(String original, String replacement) {
        try {
            java.nio.file.Files.move(Paths.get(replacement),
                    Paths.get(original), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Attempt to force the unmapping of {@code buffer}. This method should be
     * used with <strong>EXTREME CAUTION</strong>. If {@code buffer} is used
     * after this method is invoked, it is likely that the JVM will crash.
     * 
     * @param buffer
     */
    public static void unmap(MappedByteBuffer buffer) {
        Cleaners.freeMappedByteBuffer(buffer);
    }

    /**
     * The user's home directory, which is used to expand path names with "~"
     * (tilde).
     */
    private static final String USER_HOME = System.getProperty("user.home");

    /**
     * The working directory from which the current JVM process was launched.
     */
    private static final String WORKING_DIRECTORY = System
            .getProperty("user.dir");
    
    /**
     * The base path that is used to resolve and normalize other relative paths.
     */
    private static final Path BASE_PATH = FileSystems.getDefault().getPath(
            WORKING_DIRECTORY);

    private FileSystem() {}

}
