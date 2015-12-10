/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.containers

import java.io.{UnsupportedEncodingException, FileNotFoundException, PrintWriter, File}

import scala.sys.process._

import org.midonet.midolman.logging.MidolmanLogging

/**
  * Provides common functions for service containers.
  */
trait ContainerCommons extends MidolmanLogging {

    /**
      * Writes the specified string in a text file at the given path.
      */
    def writeFile(contents: String, path: String): Boolean = {
        val file = new File(path)
        file.getParentFile.mkdirs()
        val writer = new PrintWriter(file, "UTF-8")
        try {
            writer.print(contents)
            true
        } catch {
            case e: FileNotFoundException =>
                log.error(s"File not found when writing to $path", e)
                throw e
            case e: UnsupportedEncodingException =>
                log.error(s"Unsupported encoding when writing to $path", e)
                throw e
        } finally {
            writer.close()
        }
    }

    /*
     * Executes a command and logs the output.
     */
    def execCmd(cmd: String): Unit = {
        log.info(s"Execute: $cmd")
        val cmdLogger = ProcessLogger(line => log.info(line),
                                      line => log.error(line))
        cmd ! cmdLogger
    }
}
