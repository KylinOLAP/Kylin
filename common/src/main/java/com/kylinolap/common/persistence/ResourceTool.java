/*
 * Copyright 2013-2014 eBay Software Foundation
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
package com.kylinolap.common.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.util.StringUtil;

public class ResourceTool {

    private static String[] excludes = null;

    public static void main(String[] args) throws IOException {
        args = StringUtil.filterSystemArgs(args);

        if (args.length == 0) {
            System.out.println("Usage: MetadataTool reset METADATA_URI");
            System.out.println("Usage: MetadataTool copy  METADATA_URI_SRC  METADATA_URI_DST");
            System.out.println("Usage: MetadataTool list  METADATA_URI      PATH");
            return;
        }

        String exclude = System.getProperty("exclude");
        if (exclude != null) {
            excludes = exclude.split("\\s*,\\s*");
        }

        String cmd = args[0];
        if (cmd.equals("reset"))
            reset(args.length == 1 ? KylinConfig.getInstanceFromEnv() : KylinConfig.createInstanceFromUri(args[1]));
        else if (cmd.equals("copy"))
            copy(args[1], args[2]);
        else if (cmd.equals("list"))
            list(args[1], args[2]);
        else if (cmd.equals("download"))
            copy(KylinConfig.getInstanceFromEnv(), KylinConfig.createInstanceFromUri(args[1]));
        else if (cmd.equals("upload"))
            copy(KylinConfig.createInstanceFromUri(args[1]), KylinConfig.getInstanceFromEnv());
        else if (cmd.equals("remove"))
            remove(KylinConfig.getInstanceFromEnv(), args[1]);
        else
            System.out.println("Unknown cmd: " + cmd);
    }

    public static void list(KylinConfig config, String path) throws IOException {
        ResourceStore store = ResourceStore.getStore(config);
        ArrayList<String> result = store.listResources(path);
        System.out.println("" + result);
    }

    private static void list(String metadataUri, String path) throws IOException {
        KylinConfig config = KylinConfig.createInstanceFromUri(metadataUri);
        list(config, path);
    }

    public static void copy(KylinConfig srcConfig, KylinConfig dstConfig) throws IOException {

        ResourceStore src = ResourceStore.getStore(srcConfig);
        ResourceStore dst = ResourceStore.getStore(dstConfig);
        copyR(src, dst, "/");
    }

    private static void copy(String srcUri, String dstUri) throws IOException {

        System.out.println("Copy from " + srcUri + " to " + dstUri);

        KylinConfig srcConfig = KylinConfig.createInstanceFromUri(srcUri);
        KylinConfig dstConfig = KylinConfig.createInstanceFromUri(dstUri);
        copy(srcConfig, dstConfig);

    }

    private static void copyR(ResourceStore src, ResourceStore dst, String path) throws IOException {
        ArrayList<String> children = src.listResources(path);

        // case of resource (not a folder)
        if (children == null) {
            if (matchExclude(path) == false) {
                InputStream content = src.getResource(path);
                long ts = src.getResourceTimestamp(path);
                if (content != null)
                    dst.putResource(path, content, ts);
                else
                    System.out.println("Null inputstream for " + path);
            }
        }
        // case of folder
        else {
            for (String child : children)
                copyR(src, dst, child);
        }
    }

    private static boolean matchExclude(String path) {
        if (excludes == null)
            return false;
        for (String exclude : excludes) {
            if (path.startsWith(exclude))
                return true;
        }
        return false;
    }

    public static void reset(KylinConfig config) throws IOException {
        ResourceStore store = ResourceStore.getStore(config);
        resetR(store, "/");
    }

    private static void resetR(ResourceStore store, String path) throws IOException {
        ArrayList<String> children = store.listResources(path);
        if (children == null) { // path is a resource (not a folder)
            if (matchExclude(path) == false) {
                store.deleteResource(path);
            }
        } else {
            for (String child : children)
                resetR(store, child);
        }
    }
    
    private static void remove(KylinConfig config, String path) throws IOException {
        ResourceStore store = ResourceStore.getStore(config);
        resetR(store, path);
    }
}
