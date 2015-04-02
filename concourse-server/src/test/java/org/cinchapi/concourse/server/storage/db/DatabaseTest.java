/*
 * Copyright (c) 2013-2015 Cinchapi, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cinchapi.concourse.server.storage.db;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import org.cinchapi.concourse.server.io.FileSystem;
import org.cinchapi.concourse.server.storage.Store;
import org.cinchapi.concourse.server.storage.StoreTest;
import org.cinchapi.concourse.server.storage.temp.Write;
import org.cinchapi.concourse.thrift.Operator;
import org.cinchapi.concourse.thrift.TObject;
import org.cinchapi.concourse.time.Time;
import org.cinchapi.concourse.util.Convert;
import org.cinchapi.concourse.util.TestData;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link Database}.
 * 
 * @author Jeff Nelson
 */
public class DatabaseTest extends StoreTest {

    private String current;

    @Test
    public void testDatabaseRemovesUnbalancedBlocksOnStartup() throws Exception {
        Database db = (Database) store;
        db.accept(Write.add(TestData.getString(), TestData.getTObject(),
                TestData.getLong()));
        db.triggerSync();
        db.stop();
        FileSystem.deleteDirectory(current + File.separator + "csb");
        FileSystem.mkdirs(current + File.separator + "csb");
        db = new Database(db.getBackingStore()); // simulate server restart
        db.start();
        Field cpb = db.getClass().getDeclaredField("cpb");
        Field csb = db.getClass().getDeclaredField("csb");
        Field ctb = db.getClass().getDeclaredField("ctb");
        cpb.setAccessible(true);
        csb.setAccessible(true);
        ctb.setAccessible(true);
        Assert.assertEquals(1, ((List<?>) ctb.get(db)).size());
        Assert.assertEquals(1, ((List<?>) csb.get(db)).size());
        Assert.assertEquals(1, ((List<?>) cpb.get(db)).size());
    }

    @Test
    public void testDatabaseAppendsToCachedPartialPrimaryRecords() {
        Database db = (Database) store;
        String key = TestData.getString();
        long record = TestData.getLong();
        int count = TestData.getScaleCount();
        for (int i = 0; i < count; i++) {
            db.accept(Write.add(key, Convert.javaToThrift(i), record));
        }
        db.select(key, record);
        int increase = TestData.getScaleCount();
        db.accept(Write.add(key, Convert.javaToThrift(count * increase), record));
        Assert.assertTrue(db.select(key, record).contains(
                Convert.javaToThrift(count * increase)));
    }

    @Test
    public void testDatabaseAppendsToCachedSecondaryRecords() {
        Database db = (Database) store;
        String key = TestData.getString();
        TObject value = TestData.getTObject();
        int count = TestData.getScaleCount();
        for (int i = 0; i < count; i++) {
            db.accept(Write.add(key, value, i));
        }
        db.find(key, Operator.EQUALS, value);
        int increase = TestData.getScaleCount();
        db.accept(Write.add(key, value, count * increase));
        Assert.assertTrue(db.find(key, Operator.EQUALS, value).contains(
                (long) count * increase)); 
    }

    @Override
    protected void add(String key, TObject value, long record) {
        if(!store.verify(key, value, record)) {
            ((Database) store).accept(Write.add(key, value, record));
        }
    }

    @Override
    protected void cleanup(Store store) {
        FileSystem.deleteDirectory(current);
    }

    @Override
    protected Database getStore() {
        current = TestData.DATA_DIR + File.separator + Time.now();
        return new Database(current);
    }

    @Override
    protected void remove(String key, TObject value, long record) {
        if(store.verify(key, value, record)) {
            ((Database) store).accept(Write.remove(key, value, record));
        }
    }

}
