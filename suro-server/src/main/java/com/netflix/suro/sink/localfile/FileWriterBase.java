/*
 * Copyright 2013 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.suro.sink.localfile;

import com.netflix.suro.message.Message;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.util.ReflectionUtils;
import org.slf4j.Logger;

import java.io.DataOutputStream;
import java.io.IOException;

public class FileWriterBase {
    private final FileSystem fs;
    private final Configuration conf = new Configuration();
    private final CompressionCodec codec;
    private final Logger log;

    public FileWriterBase(String codecClass, Logger log) {
        this.log = log;

        try {
            fs = FileSystem.getLocal(conf);
            fs.setVerifyChecksum(false);
            if (codecClass != null) {
                codec = createCodecInstance(codecClass);
                log.info("Codec:" + codec.getDefaultExtension());
            } else {
                codec = null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setDone(String oldName, String newName) throws IOException {
        fs.rename(new Path(oldName), new Path(newName));
    }

    public void createOutputDir(String outputDir) throws IOException {
        Path pLocalOutputDir = new Path(outputDir);
        if (fs.exists(pLocalOutputDir) == false) {
            boolean exist = fs.mkdirs(pLocalOutputDir);
            if (!exist) {
                throw new RuntimeException("Cannot create local dataSink dir: " + outputDir);
            }
        } else {
            FileStatus fsLocalOutputDir = fs.getFileStatus(pLocalOutputDir);
            if (!fsLocalOutputDir.isDir()) {
                throw new RuntimeException("local dataSink dir is not a directory: " + outputDir);
            }
        }
    }

    public static CompressionCodec createCodecInstance(String codecClass) throws ClassNotFoundException {
        Class<?> classDefinition = Class.forName(codecClass);
        return (CompressionCodec) ReflectionUtils.newInstance(classDefinition, new Configuration());
    }

    public SequenceFile.Writer createSequenceFile(String newPath) throws IOException {
        if (codec != null) {
            return SequenceFile.createWriter(
                    fs, conf, new Path(newPath),
                    Text.class, Message.class,
                    SequenceFile.CompressionType.BLOCK, codec);
        } else {
            return SequenceFile.createWriter(
                    fs, conf, new Path(newPath),
                    Text.class, Message.class,
                    SequenceFile.CompressionType.NONE, codec);
        }
    }

    public FSDataOutputStream createFSDataOutputStream(String path) throws IOException {
        return fs.create(new Path(path), false);
    }

    public DataOutputStream createDataOutputStream(FSDataOutputStream outputStream) throws IOException {
        if (codec != null) {
            return new FSDataOutputStream(codec.createOutputStream(outputStream), null);
        } else {
            return outputStream;
        }
    }
}
