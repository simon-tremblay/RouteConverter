/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/

package slash.navigation.converter.cmdline;

import slash.common.system.Version;
import slash.navigation.base.BaseNavigationFormat;
import slash.navigation.base.MultipleRoutesFormat;
import slash.navigation.base.NavigationFormat;
import slash.navigation.base.NavigationFormatParser;
import slash.navigation.base.NavigationFormats;
import slash.navigation.base.ParserResult;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static slash.common.io.Files.absolutize;
import static slash.common.io.Files.createTargetFiles;
import static slash.common.io.Files.removeExtension;
import static slash.common.system.Platform.getJava;
import static slash.common.system.Platform.getMaximumMemory;
import static slash.common.system.Platform.getPlatform;
import static slash.common.system.Version.parseVersionFromManifest;
import static slash.navigation.base.NavigationFormatParser.getNumberOfFilesToWriteFor;
import static slash.navigation.base.NavigationFormats.getReadFormatsSortedByName;
import static slash.navigation.base.NavigationFormats.getWriteFormatsSortedByName;

/**
 * A simple command line user interface for the route conversion
 * running under Java 5.
 *
 * @author Christian Pesch
 */

public class RouteConverterCmdLine {
    private static final Logger log = Logger.getLogger(RouteConverterCmdLine.class.getName());

    private void initializeLogging() {
        try {
            LogManager.getLogManager().readConfiguration(RouteConverterCmdLine.class.getResourceAsStream("cmdline.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logFormatNames(List<NavigationFormat> formats) {
        log.info("Supported formats:");
        for (NavigationFormat format : formats)
            log.info(format.getClass().getSimpleName() + " for " + format.getName());
    }

    private BaseNavigationFormat findFormat(String formatName) {
        List<NavigationFormat> formats = NavigationFormats.getWriteFormats();
        for (NavigationFormat format : formats)
            if (formatName.equals(format.getClass().getSimpleName()))
                return (BaseNavigationFormat) format;
        return null;
    }

    private void run(String[] args) {
        Version version = parseVersionFromManifest();
        log.info("Started RouteConverter " + version.getVersion() + " from " + version.getDate() +
                " on " + getJava() + " and " + getPlatform() + " with " + getMaximumMemory() + " MByte heap");
        if (args.length != 3) {
            log.info("Usage: java -jar RouteConverterCmdLine.jar <source file> <target format> <target file>");
            logFormatNames(getWriteFormatsSortedByName());
            System.exit(5);
        }

        File source = absolutize(new File(args[0]));
        if (!source.exists()) {
            log.severe("Source '" + source.getAbsolutePath() + "' does not exist; stopping.");
            System.exit(10);
        }

        BaseNavigationFormat format = findFormat(args[1]);
        if (format == null) {
            log.severe("Format '" + args[1] + "' does not exist; stopping.");
            logFormatNames(getWriteFormatsSortedByName());
            System.exit(12);
        }

        String baseName = removeExtension(args[2]);
        File target = absolutize(new File(baseName + format.getExtension()));
        if (target.exists()) {
            log.severe("Target '" + target.getAbsolutePath() + "' already exists; stopping.");
            System.exit(13);
        }

        try {
            convert(source, format, target);
        } catch (IOException e) {
            log.severe("Error while converting: " + e);
            System.exit(15);
        }

        System.exit(0);
    }

    private void convert(File source, NavigationFormat format, File target) throws IOException {
        NavigationFormatParser parser = new NavigationFormatParser();
        ParserResult result = parser.read(source);
        if (!result.isSuccessful()) {
            log.severe("Could not read source '" + source.getAbsolutePath() + "'");
            logFormatNames(getReadFormatsSortedByName());
            System.exit(20);
        }

        if (format.isSupportsMultipleRoutes()) {
            parser.write(result.getAllRoutes(), (MultipleRoutesFormat) format, target);
        } else {
            int fileCount = getNumberOfFilesToWriteFor(result.getTheRoute(), format, false);
            File[] targets = createTargetFiles(target, fileCount, format.getExtension(), format.getMaximumFileNameLength());
            for (File t : targets) {
                if (t.exists()) {
                    log.severe("Target '" + t.getAbsolutePath() + "' already exists; stopping.");
                    System.exit(13);
                }
            }
            parser.write(result.getTheRoute(), format, false, false, null, targets);
        }
    }

    public static void main(String[] args) {
        RouteConverterCmdLine cmdLine = new RouteConverterCmdLine();
        cmdLine.initializeLogging();
        cmdLine.run(args);
    }
}
