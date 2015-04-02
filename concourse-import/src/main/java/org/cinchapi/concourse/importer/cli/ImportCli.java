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
package org.cinchapi.concourse.importer.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.cinchapi.concourse.cli.CommandLineInterface;
import org.cinchapi.concourse.cli.Options;
import org.cinchapi.concourse.importer.CsvImporter;
import org.cinchapi.concourse.importer.Importer;
import org.cinchapi.concourse.importer.util.Files;

import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 * A CLI that uses the import framework to import data into Concourse.
 * 
 * @author Jeff Nelson
 */
public class ImportCli extends CommandLineInterface {
    /*
     * TODO
     * 1) add flag to specify importer (default will be CSV..or maybe auto
     * detect??)
     * 2) add flags to whitelist or blacklist files in a directory
     */

    /**
     * The importer.
     */
    private final Importer importer;

    /**
     * Construct a new instance.
     * 
     * @param args
     */
    public ImportCli(String[] args) {
        super(new ImportOptions(), args);
        this.importer = new CsvImporter(this.concourse);
    }

    @Override
    protected void doTask() {
        final ImportOptions opts = (ImportOptions) options;
        ExecutorService executor = Executors
                .newFixedThreadPool(((ImportOptions) options).numThreads);
        String data = opts.data;
        List<String> files = scan(Paths.get(Files.expandPath(data)));
        Stopwatch watch = Stopwatch.createStarted();
        for (final String file : files) {
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    importer.importFile(file, opts.resolveKey);
                }

            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
            continue; // block until all tasks are completed
        }
        watch.stop();
        TimeUnit unit = TimeUnit.SECONDS;
        System.out.println(MessageFormat.format("Finished import in {0} {1}",
                watch.elapsed(unit), unit));

    }

    /**
     * Recursively scan and collect all the files in the directory defined by
     * {@code path}.
     * 
     * @param path
     * @return the list of files in the directory
     */
    protected List<String> scan(Path path) {
        try {
            List<String> files = Lists.newArrayList();
            if(java.nio.file.Files.isDirectory(path)) {
                Iterator<Path> it = java.nio.file.Files
                        .newDirectoryStream(path).iterator();
                while (it.hasNext()) {
                    files.addAll(scan(it.next()));
                }
            }
            else {
                files.add(path.toString());

            }
            return files;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Import specific {@link Options}.
     * 
     * @author jnelson
     */
    protected static class ImportOptions extends Options {

        @Parameter(names = { "-d", "--data" }, description = "The path to the file or directory to import", required = true)
        public String data;

        @Parameter(names = "--numThreads", description = "The number of worker threads to use for a multithreaded import")
        public int numThreads = 1;

        @Parameter(names = { "-r", "--resolveKey" }, description = "The key to use when resolving data into existing records")
        public String resolveKey = null;

    }

}
