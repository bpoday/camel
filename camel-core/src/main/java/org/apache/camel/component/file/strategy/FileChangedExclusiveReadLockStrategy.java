/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.strategy;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acquires exclusive read lock to the given file by checking whether the file is being
 * changed by scanning the file at different intervals (to detect changes).
 */
public class FileChangedExclusiveReadLockStrategy extends MarkerFileExclusiveReadLockStrategy {
    private static final transient Logger LOG = LoggerFactory.getLogger(FileChangedExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 1000;
    private long minLength = 1;

    public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
        // must call super
        if (!super.acquireExclusiveReadLock(operations, file, exchange)) {
            return false;
        }

        File target = new File(file.getAbsoluteFilePath());
        boolean exclusive = false;

        LOG.trace("Waiting for exclusive read lock to file: {}", file);

        long lastModified = Long.MIN_VALUE;
        long length = Long.MIN_VALUE;
        StopWatch watch = new StopWatch();

        while (!exclusive) {
            // timeout check
            if (timeout > 0) {
                long delta = watch.taken();
                if (delta > timeout) {
                    LOG.warn("Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + file);
                    // we could not get the lock within the timeout period, so return false
                    return false;
                }
            }

            long newLastModified = target.lastModified();
            long newLength = target.length();

            LOG.trace("Previous last modified: {}, new last modified: {}", lastModified, newLastModified);
            LOG.trace("Previous length: {}, new length: {}", length, newLength);

            if (length >= minLength && (newLastModified == lastModified && newLength == length)) {
                LOG.trace("Read lock acquired.");
                exclusive = true;
            } else {
                // set new base file change information
                lastModified = newLastModified;
                length = newLength;

                boolean interrupted = sleep();
                if (interrupted) {
                    // we were interrupted while sleeping, we are likely being shutdown so return false
                    return false;
                }
            }
        }

        return exclusive;
    }

    private boolean sleep() {
        LOG.trace("Exclusive read lock not granted. Sleeping for {} millis.", checkInterval);
        try {
            Thread.sleep(checkInterval);
            return false;
        } catch (InterruptedException e) {
            LOG.debug("Sleep interrupted while waiting for exclusive read lock, so breaking out");
            return true;
        }
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    public long getMinLength() {
        return minLength;
    }

    public void setMinLength(long minLength) {
        this.minLength = minLength;
    }
}
