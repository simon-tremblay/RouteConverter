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
package slash.navigation.download.tools;

import org.apache.commons.cli.*;
import slash.navigation.datasources.DataSource;
import slash.navigation.datasources.DataSourceManager;
import slash.navigation.datasources.DataSourceService;
import slash.navigation.datasources.Edition;
import slash.navigation.download.DownloadManager;
import slash.navigation.download.tools.base.BaseDownloadTool;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static slash.navigation.datasources.DataSourceManager.loadDataSources;

/**
 * Performs a filesystem mirror from the DataSources catalog.
 *
 * @author Christian Pesch
 */

public class SnapshotCatalog extends BaseDownloadTool {
    private static final Logger log = Logger.getLogger(SnapshotCatalog.class.getName());
    private static final String RESET_ARGUMENT = "reset";

    private DataSourceManager dataSourceManager = new DataSourceManager(new DownloadManager(new File(getSnapshotDirectory(), "download-queue.xml")));
    private String url;
    private boolean reset = false;

    public void setUrl(String url) {
        this.url = url;
    }

    public void setReset(boolean reset) {
        this.reset = reset;
    }

    void close() {
        dataSourceManager.getDownloadManager().saveQueue();
        dataSourceManager.dispose();
    }

    private void open() throws IOException {
        if(reset) {
            dataSourceManager.getDownloadManager().clearQueue();
            deleteAll(getSnapshotDirectory());
        }  else
            dataSourceManager.getDownloadManager().loadQueue();
    }

    void deleteAll(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    deleteAll(file);
                if (!file.delete())
                    throw new IOException("Could not delete " + file);
            }
        }
    }

    private List<DataSource> createDataSourceSet(List<Edition> editions) {
        Set<DataSource> result = new HashSet<>();
        for(Edition edition : editions) {
            for (DataSource dataSource : edition.getDataSources())
                result.add(dataSource);
        }
        return new ArrayList(result);
    }

    public void snapshot() throws IOException, JAXBException {
        open();

        dataSourceManager.downloadRoot(url, getRootDirectory());
        DataSourceService root = loadDataSources(getRootDirectory());

        dataSourceManager.downloadEditions(root.getEditions(), getEditionsDirectory());
        DataSourceService editions = loadDataSources(getEditionsDirectory());

        dataSourceManager.downloadDataSources(createDataSourceSet(editions.getEditions()), getDataSourcesDirectory());
        DataSourceService dataSources = loadDataSources(getDataSourcesDirectory());
        log.info(String.format("Snapshot contains %d editions and %d datasources", editions.getEditions().size(), dataSources.getDataSources().size()));

        close();
    }

    private void run(String[] args) throws Exception {
        CommandLine line = parseCommandLine(args);
        setUrl(line.getOptionValue(URL_ARGUMENT));
        reset = line.hasOption(RESET_ARGUMENT);
        snapshot();
        System.exit(0);
    }

    @SuppressWarnings("AccessStaticViaInstance")
    private CommandLine parseCommandLine(String[] args) throws ParseException {
        CommandLineParser parser = new GnuParser();
        Options options = new Options();
        options.addOption(OptionBuilder.withArgName(URL_ARGUMENT).hasArgs(1).isRequired().withLongOpt("url").
                withDescription("URL to take a snapshot from").create());
        options.addOption(OptionBuilder.withArgName(RESET_ARGUMENT).withLongOpt("reset").
                withDescription("Reset local snapshot").create());
        try {
            return parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(getClass().getSimpleName(), options);
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        new SnapshotCatalog().run(args);
    }
}
