/*
 * Copyright 2018 John Ahlroos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devsoap.plugin;

import io.bit3.jsass.CompilationException;
import io.bit3.jsass.Compiler;
import io.bit3.jsass.Options;
import io.bit3.jsass.Output;
import org.apache.commons.io.FileUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runner for Libsass compiler
 *
 * @author John Ahlroos
 */
public class LibSassCompiler {

    // Usage: 'LibSassCompiler [scss] [css] [unpackedThemes]
    public static void main(String[] args) throws Exception {
        Path inputFile = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);
        if (!Files.exists(outputFile)) {
            Files.createFile(outputFile);
        }

        Path sourceMapFile = Paths.get(args[1]+".map");
        if(!Files.exists(sourceMapFile)) {
            Files.createFile(sourceMapFile);
        }

        Path unpackedThemes = Paths.get(args[2]);
        Path unpackedInputFile = unpackedThemes
                .resolve(inputFile.getParent().getFileName().toString())
                .resolve(inputFile.getFileName().toString());

        Compiler compiler = new Compiler();
        Options options = new Options();

        try {
            Output output = compiler.compileFile(unpackedInputFile.toUri(), outputFile.toUri(), options);
            FileUtils.write(outputFile.toFile(), output.getCss(), StandardCharsets.UTF_8.name());
            FileUtils.write(sourceMapFile.toFile(), output.getSourceMap(), StandardCharsets.UTF_8.name());
        } catch (CompilationException e) {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(sourceMapFile);
            throw e;
        }
    }
}