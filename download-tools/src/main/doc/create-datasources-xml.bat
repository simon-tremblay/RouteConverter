Tests:

java -jar download-tools\target\CreateBrouterDataSourcesXml.jar "testId" "testName" "http://static.routeconverter.com/test/" "testDirectory" "C:\Temp\test-brouter-datasources.xml"
java -jar download-tools\target\CreateGraphHopperDataSourcesXml.jar "testId" "testName" "http://static.routeconverter.com/test/" "testDirectory" "C:\Temp\test-graphhopper-datasources.xml"
java -jar download-tools\target\CreateHgtDataSourcesXml.jar "testId" "testName" "testUrl" "testDirectory" "C:\Temp\test-hgt" "C:\Temp\test-hgt-datasources.xml"
java -jar download-tools\target\CreateMapDataSourcesXml.jar "testId" "testName" "http://static.routeconverter.com/test/" "testDirectory" "C:\Temp\test-map-datasources.xml"

Production:

java -jar download-tools\target\CreateBrouterDataSourcesXml.jar "brouter-profiles" "BRouter" "http://h2096617.stratoserver.net/brouter/profiles2/" "http://h2096617.stratoserver.net/brouter/profiles2/" "brouter/profiles" "C:\Temp\brouter-profiles-datasources.xml"
java -jar download-tools\target\CreateBrouterDataSourcesXml.jar "brouter-segments" "BRouter" "http://h2096617.stratoserver.net/brouter/segments3/" "http://h2096617.stratoserver.net/brouter/segments3/" "brouter/segments" "C:\Temp\brouter-segments-datasources.xml"

java -jar download-tools\target\CreateGraphHopperDataSourcesXml.jar "graphhopper" "GraphHopper" "http://download.geofabrik.de/" "http://download.geofabrik.de/" "graphhopper" "C:\Temp\graphhopper-datasources.xml"
/ -> ./australia-oceania.html
./australia-oceania.html -> australia-oceania/australia.html
australia-oceania/australia.html

java -jar download-tools\target\CreateHgtDataSourcesXml.jar "srtm3" "NASA SRTM 3" "http://dds.cr.usgs.gov/srtm/version2_1/SRTM3/" "srtm3" "W:\Mirrors\dds.cr.usgs.gov\srtm\version2_1\SRTM3" "C:\Temp\srtm3-datasources.xml"
java -jar download-tools\target\CreateHgtDataSourcesXml.jar "srtm1" "NASA SRTM 1" "http://dds.cr.usgs.gov/srtm/version2_1/SRTM1/" "srtm1" "W:\Mirrors\dds.cr.usgs.gov\srtm\version2_1\SRTM1" "C:\Temp\srtm1-datasources.xml"
java -jar download-tools\target\CreateHgtDataSourcesXml.jar "ferranti3" "Jonathan de Ferranti DEM 3" "http://www.viewfinderpanoramas.org/dem3/" "ferranti3" "W:\Mirrors\www.viewfinderpanoramas.org\dem3" "C:\Temp\ferranti3-datasources.xml"
java -jar download-tools\target\CreateHgtDataSourcesXml.jar "ferranti1" "Jonathan de Ferranti DEM 1" "http://www.viewfinderpanoramas.org/dem1/" "ferranti1" "W:\Mirrors\www.viewfinderpanoramas.org\dem1" "C:\Temp\ferranti1-datasources.xml"

java -jar download-tools\target\CreateMapDataSourcesXml.jar "androidmaps" "Androidmaps" "http://www.androidmaps.co.uk/" "http://www.androidmaps.co.uk/maps/" "maps/androidmaps" "C:\Temp\android-maps-datasources.xml"
java -jar download-tools\target\CreateMapDataSourcesXml.jar "freizeitkarte-maps" "Freizeitkarte Maps" "http://download.freizeitkarte-osm.de/android/1408/" "http://download.freizeitkarte-osm.de/android/1408/" "maps/freizeitkarte" "C:\Temp\freizeitkarte-maps-datasources.xml"
java -jar download-tools\target\CreateMapDataSourcesXml.jar "mapsforge-maps" "Mapsforge Maps" "http://download.mapsforge.org/maps/" "http://download.mapsforge.org/maps/" "maps/mapsforge" "C:\Temp\mapsforge-maps-datasources.xml"
java -jar download-tools\target\CreateMapDataSourcesXml.jar "openandromaps" "Openandromaps Maps" "http://ftp5.gwdg.de/pub/misc/openstreetmap/openandromaps/maps/" "http://ftp5.gwdg.de/pub/misc/openstreetmap/openandromaps/maps/" "maps/openandromaps" "C:\Temp\openandromaps-maps-datasources.xml"

java -jar download-tools\target\CreateWebThemeDataSourcesXml.jar "freizeitkarte-themes" "Freizeitkarte Themes" "http://download.freizeitkarte-osm.de/android/1408/" "http://download.freizeitkarte-osm.de/android/1408/" "themes/freizeitkarte" "C:\Temp\freizeitkarte-themes-datasources.xml"
java -jar download-tools\target\CreateFSThemeDataSourcesXml.jar "openandromaps-themes" "Openandromaps Themes" "http://www.openandromaps.org/wp-content/" "themes/openandromaps" "C:\Temp\OAM" "C:\Temp\openandromaps-themes-datasources.xml"

