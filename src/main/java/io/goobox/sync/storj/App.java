/*
 * Copyright (C) 2017-2018 Kaloyan Raev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.goobox.sync.storj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import com.liferay.nativity.control.NativityControl;
import com.liferay.nativity.control.NativityControlUtil;
import com.liferay.nativity.modules.fileicon.FileIconControl;
import com.liferay.nativity.modules.fileicon.FileIconControlCallback;
import com.liferay.nativity.modules.fileicon.FileIconControlUtil;
import com.liferay.nativity.util.OSDetector;

import io.goobox.sync.common.Utils;
import io.goobox.sync.common.systemtray.ShutdownListener;
import io.goobox.sync.common.systemtray.SystemTrayHelper;
import io.goobox.sync.storj.overlay.OverlayHelper;
import io.storj.libstorj.Bucket;
import io.storj.libstorj.CreateBucketCallback;
import io.storj.libstorj.GetBucketsCallback;
import io.storj.libstorj.KeysNotFoundException;
import io.storj.libstorj.Storj;

public class App implements ShutdownListener {

    private static App instance;

    private Storj storj;
    private Bucket gooboxBucket;
    private TaskQueue tasks;
    private TaskExecutor taskExecutor;
    private FileWatcher fileWatcher;

    public static void main(String[] args) {
        instance = new App();
        instance.init();

        NativityControl nativityControl = NativityControlUtil.getNativityControl();
        nativityControl.connect();

        // Setting filter folders is required for Mac's Finder Sync plugin
        // nativityControl.setFilterFolder(Utils.getSyncDir().toString());

        /* File Icons */

        int testIconId = 1;

        // FileIconControlCallback used by Windows and Mac
        FileIconControlCallback fileIconControlCallback = new FileIconControlCallback() {
            @Override
            public int getIconForFile(String path) {
                return 1; // testIconId;
            }
        };

        FileIconControl fileIconControl = FileIconControlUtil.getFileIconControl(nativityControl,
                fileIconControlCallback);

        fileIconControl.enableFileIcons();

        String testFilePath = Utils.getSyncDir().toString();

        if (OSDetector.isWindows()) {
            // This id is determined when building the DLL
            testIconId = 1;
        } else if (OSDetector.isMinimumAppleVersion(OSDetector.MAC_YOSEMITE_10_10)) {
            // Used by Mac Finder Sync. This unique id can be set at runtime.
            testIconId = 1;

            fileIconControl.registerIconWithId("/tmp/goobox.icns",
                    "test label", "" + testIconId);
        } else if (OSDetector.isLinux()) {
            // Used by Mac Injector and Linux
            testIconId = fileIconControl.registerIcon("/tmp/git-clean.png");
        }

        // FileIconControl.setFileIcon() method only used by Linux
        fileIconControl.setFileIcon(testFilePath, testIconId);
        nativityControl.disconnect();
    }

    public static App getInstance() {
        return instance;
    }

    public Storj getStorj() {
        return storj;
    }

    public Bucket getGooboxBucket() {
        return gooboxBucket;
    }

    public TaskQueue getTaskQueue() {
        return tasks;
    }

    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    public FileWatcher getFileWatcher() {
        return fileWatcher;
    }

    private void init() {
        SystemTrayHelper.setIdle();
        SystemTrayHelper.setShutdownListener(this);

        storj = new Storj();
        storj.setConfigDirectory(StorjUtil.getStorjConfigDir().toFile());
        storj.setDownloadDirectory(Utils.getSyncDir().toFile());

        if (!checkAndCreateSyncDir()) {
            System.exit(1);
        }

        if (!checkAndCreateDataDir()) {
            System.exit(1);
        }

        gooboxBucket = checkAndCreateCloudBucket();
        if (gooboxBucket == null) {
            System.exit(1);
        }

        tasks = new TaskQueue();
        tasks.add(new CheckStateTask());

        taskExecutor = new TaskExecutor(tasks);
        fileWatcher = new FileWatcher();

        fileWatcher.start();
        taskExecutor.start();
    }

    @Override
    public void shutdown() {
        // TODO graceful shutdown
        OverlayHelper.getInstance().shutdown();
        System.exit(0);
    }

    private boolean checkAndCreateSyncDir() {
        System.out.print("Checking if local Goobox sync folder exists... ");
        return checkAndCreateFolder(Utils.getSyncDir());
    }

    private boolean checkAndCreateDataDir() {
        System.out.print("Checking if Goobox data folder exists... ");
        return checkAndCreateFolder(Utils.getDataDir());
    }

    private boolean checkAndCreateFolder(Path path) {
        if (Files.exists(path)) {
            System.out.println("yes");
            return true;
        } else {
            System.out.print("no. ");
            try {
                Files.createDirectory(path);
                System.out.println("Folder created.");
                return true;
            } catch (IOException e) {
                System.out.println("Failed creating folder: " + e.getMessage());
                return false;
            }
        }
    }

    private Bucket checkAndCreateCloudBucket() {
        System.out.print("Checking if cloud Goobox bucket exists... ");
        final Bucket[] result = { null };

        try {
            while (result[0] == null) {
                final CountDownLatch latch = new CountDownLatch(1);

                storj.getBuckets(new GetBucketsCallback() {
                    @Override
                    public void onBucketsReceived(Bucket[] buckets) {
                        for (Bucket bucket : buckets) {
                            if ("Goobox".equals(bucket.getName())) {
                                result[0] = bucket;
                                System.out.println("yes");
                                latch.countDown();
                                return;
                            }
                        }

                        System.out.print("no. ");
                        storj.createBucket("Goobox", new CreateBucketCallback() {
                            @Override
                            public void onError(String message) {
                                System.out.println("Failed creating cloud Goobox bucket.");
                                latch.countDown();
                            }

                            @Override
                            public void onBucketCreated(Bucket bucket) {
                                System.out.println("Cloud Goobox bucket created.");
                                result[0] = bucket;
                                latch.countDown();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        System.out.println(message);
                        latch.countDown();
                    }
                });

                latch.await();

                if (result[0] == null) {
                    // error - wait 3 seconds before trying again
                    Thread.sleep(3000);
                }
            }
        } catch (KeysNotFoundException e) {
            System.out.println(
                    "No keys found. Have your imported your keys using libstorj? Make sure you don't specify a passcode.");
        } catch (InterruptedException e) {
            // do nothing
        }

        return result[0];
    }
}
