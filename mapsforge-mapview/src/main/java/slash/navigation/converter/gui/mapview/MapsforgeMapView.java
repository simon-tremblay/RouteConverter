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
package slash.navigation.converter.gui.mapview;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.model.Dimension;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.LayerManager;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.TileDownloadLayer;
import org.mapsforge.map.layer.download.tilesource.TileSource;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.util.MapViewProjection;
import slash.navigation.base.BaseNavigationPosition;
import slash.navigation.base.BaseRoute;
import slash.navigation.base.RouteCharacteristics;
import slash.navigation.brouter.BRouter;
import slash.navigation.common.BoundingBox;
import slash.navigation.common.LongitudeAndLatitude;
import slash.navigation.common.NavigationPosition;
import slash.navigation.converter.gui.augment.PositionAugmenter;
import slash.navigation.converter.gui.mapview.helpers.MapViewComponentListener;
import slash.navigation.converter.gui.mapview.helpers.MapViewMouseEventListener;
import slash.navigation.converter.gui.mapview.lines.Line;
import slash.navigation.converter.gui.mapview.lines.Polyline;
import slash.navigation.converter.gui.mapview.updater.EventMapUpdater;
import slash.navigation.converter.gui.mapview.updater.PositionPair;
import slash.navigation.converter.gui.mapview.updater.SelectionOperation;
import slash.navigation.converter.gui.mapview.updater.SelectionUpdater;
import slash.navigation.converter.gui.mapview.updater.TrackOperation;
import slash.navigation.converter.gui.mapview.updater.TrackUpdater;
import slash.navigation.converter.gui.mapview.updater.WaypointOperation;
import slash.navigation.converter.gui.mapview.updater.WaypointUpdater;
import slash.navigation.converter.gui.models.CharacteristicsModel;
import slash.navigation.converter.gui.models.PositionsModel;
import slash.navigation.converter.gui.models.PositionsSelectionModel;
import slash.navigation.converter.gui.models.UnitSystemModel;
import slash.navigation.download.DownloadManager;
import slash.navigation.gui.Application;
import slash.navigation.gui.actions.ActionManager;
import slash.navigation.gui.actions.FrameAction;
import slash.navigation.maps.LocalMap;
import slash.navigation.maps.MapManager;
import slash.navigation.maps.Theme;
import slash.navigation.routing.DownloadFuture;
import slash.navigation.routing.RoutingResult;
import slash.navigation.routing.RoutingService;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.abs;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static javax.swing.event.TableModelEvent.ALL_COLUMNS;
import static javax.swing.event.TableModelEvent.DELETE;
import static javax.swing.event.TableModelEvent.INSERT;
import static javax.swing.event.TableModelEvent.UPDATE;
import static org.mapsforge.core.graphics.Color.BLUE;
import static org.mapsforge.core.util.LatLongUtils.zoomForBounds;
import static org.mapsforge.core.util.MercatorProjection.calculateGroundResolution;
import static slash.navigation.base.RouteCharacteristics.Waypoints;
import static slash.navigation.converter.gui.mapview.AwtGraphicMapView.GRAPHIC_FACTORY;
import static slash.navigation.converter.gui.models.PositionColumns.DESCRIPTION_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.LATITUDE_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.LONGITUDE_COLUMN_INDEX;
import static slash.navigation.gui.helpers.JMenuHelper.createItem;
import static slash.navigation.gui.helpers.JTableHelper.isFirstToLastRow;
import static slash.navigation.maps.helpers.MapTransfer.asBoundingBox;
import static slash.navigation.maps.helpers.MapTransfer.asLatLong;
import static slash.navigation.maps.helpers.MapTransfer.asNavigationPosition;
import static slash.navigation.maps.helpers.MapTransfer.toBoundingBox;

/**
 * Implementation for a component that displays the positions of a position list on a map
 * using the rewrite branch of the mapsforge project.
 *
 * @author Christian Pesch
 */

public class MapsforgeMapView implements MapView {
    private static final Preferences preferences = Preferences.userNodeForPackage(MapsforgeMapView.class);
    private static final Logger log = Logger.getLogger(MapsforgeMapView.class.getName());

    private static final String CENTER_LATITUDE_PREFERENCE = "centerLatitude";
    private static final String CENTER_LONGITUDE_PREFERENCE = "centerLongitude";
    private static final String CENTER_ZOOM_PREFERENCE = "centerZoom";

    private PositionsModel positionsModel;
    private PositionsSelectionModel positionsSelectionModel;
    private CharacteristicsModel characteristicsModel;
    private UnitSystemModel unitSystemModel;

    private MapSelector mapSelector;
    private AwtGraphicMapView mapView;
    private MapViewMouseEventListener mapViewMouseEventListener;
    private static Bitmap markerIcon, waypointIcon;
    private static Paint TRACK_PAINT, ROUTE_PAINT, ROUTE_DOWNLOADING_PAINT;

    private boolean recenterAfterZooming;
    private PositionAugmenter positionAugmenter;
    private RoutingService routingService;
    private MapManager mapManager;
    private SelectionUpdater selectionUpdater;
    private EventMapUpdater eventMapUpdater, routeUpdater, trackUpdater, waypointUpdater;
    private ExecutorService executor = newSingleThreadExecutor();

    // initialization

    public void initialize(PositionsModel positionsModel,
                           PositionsSelectionModel positionsSelectionModel,
                           CharacteristicsModel characteristicsModel,
                           PositionAugmenter positionAugmenter,
                           DownloadManager downloadManager, MapManager mapManager,
                           boolean recenterAfterZooming,
                           boolean showCoordinates, boolean showWaypointDescription,
                           TravelMode travelMode, boolean avoidHighways, boolean avoidTolls,
                           UnitSystemModel unitSystemModel) {
        this.mapManager = mapManager;
        initializeActions();
        initializeMapView();
        setModel(positionsModel, positionsSelectionModel, characteristicsModel, unitSystemModel);
        this.positionAugmenter = positionAugmenter;
        BRouter bRouter = new BRouter();
        bRouter.setDownloadManager(downloadManager);
        try {
            bRouter.initialize();
        } catch (IOException e) {
            log.severe("Cannot initialize BRouter: " + e.getMessage());
        }
        this.routingService = bRouter; // TODO need to make this configurable

        this.recenterAfterZooming = recenterAfterZooming;
    }

    private void initializeActions() {
        ActionManager actionManager = Application.getInstance().getContext().getActionManager();
        actionManager.register("select-position", new SelectPositionAction());
        actionManager.register("extend-selection", new ExtendSelectionAction());
        actionManager.register("add-position", new AddPositionAction());
        actionManager.register("delete-position", new DeletePositionAction());
        actionManager.register("center-here", new CenterAction());
        actionManager.register("zoom-in", new ZoomAction(+1));
        actionManager.register("zoom-out", new ZoomAction(-1));
    }

    private void initializeMapView() {
        mapView = createMapView();

        mapViewMouseEventListener = new MapViewMouseEventListener(mapView, createPopupMenu());
        mapView.addMouseListener(mapViewMouseEventListener);
        mapView.addMouseMotionListener(mapViewMouseEventListener);
        mapView.addMouseWheelListener(mapViewMouseEventListener);

        try {
            markerIcon = GRAPHIC_FACTORY.createResourceBitmap(MapsforgeMapView.class.getResourceAsStream("marker.png"), -1);
            waypointIcon = GRAPHIC_FACTORY.createResourceBitmap(MapsforgeMapView.class.getResourceAsStream("waypoint.png"), -1);
        } catch (IOException e) {
            log.severe("Cannot create marker and waypoint icon: " + e.getMessage());
        }
        TRACK_PAINT = GRAPHIC_FACTORY.createPaint();
        TRACK_PAINT.setColor(BLUE);
        TRACK_PAINT.setStrokeWidth(3);
        ROUTE_PAINT = GRAPHIC_FACTORY.createPaint();
        ROUTE_PAINT.setColor(0x993379FF);
        ROUTE_PAINT.setStrokeWidth(5);
        ROUTE_DOWNLOADING_PAINT = GRAPHIC_FACTORY.createPaint();
        ROUTE_DOWNLOADING_PAINT.setColor(0x993379FF);
        ROUTE_DOWNLOADING_PAINT.setStrokeWidth(5);
        ROUTE_DOWNLOADING_PAINT.setDashPathEffect(new float[]{3, 12});

        mapSelector = new MapSelector(mapManager, mapView);

        final MapViewPosition mapViewPosition = mapView.getModel().mapViewPosition;
        mapViewPosition.addObserver(new Observer() {
            public void onChange() {
                mapSelector.zoomChanged(mapViewPosition.getZoomLevel());
            }
        });
        mapViewPosition.setZoomLevelMin((byte) 2);
        mapViewPosition.setZoomLevelMax((byte) 22);

        double longitude = preferences.getDouble(CENTER_LONGITUDE_PREFERENCE, -25.0);
        double latitude = preferences.getDouble(CENTER_LATITUDE_PREFERENCE, 35.0);
        byte zoom = (byte) preferences.getInt(CENTER_ZOOM_PREFERENCE, 2);
        mapViewPosition.setMapPosition(new MapPosition(new LatLong(latitude, longitude), zoom));

        ChangeListener mapAndThemeChangeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                handleMapAndThemeUpdate();
            }
        };
        mapManager.getDisplayedMapModel().addChangeListener(mapAndThemeChangeListener);
        mapManager.getAppliedThemeModel().addChangeListener(mapAndThemeChangeListener);
        handleMapAndThemeUpdate();
    }

    private AwtGraphicMapView createMapView() {
        AwtGraphicMapView mapView = new AwtGraphicMapView();
        mapView.getMapScaleBar().setVisible(true);
        mapView.addComponentListener(new MapViewComponentListener(mapView, mapView.getModel().mapViewDimension));
        return mapView;
    }

    private JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(createItem("select-position"));
        menu.add(createItem("add-position"));    // TODO should be "new-position"
        menu.add(createItem("delete-position")); // TODO should be "delete"
        menu.addSeparator();
        menu.add(createItem("center-here"));
        menu.add(createItem("zoom-in"));
        menu.add(createItem("zoom-out"));
        return menu;
    }

    private TileRendererLayer createTileRendererLayer(LocalMap map, Theme theme) {
        TileRendererLayer tileRendererLayer = new TileRendererLayer(createTileCache(), mapView.getModel().mapViewPosition, false, GRAPHIC_FACTORY);
        tileRendererLayer.setMapFile(map.getFile());
        tileRendererLayer.setXmlRenderTheme(theme.getXmlRenderTheme());
        return tileRendererLayer;
    }

    private TileDownloadLayer createTileDownloadLayer(TileSource tileSource) {
        return new TileDownloadLayer(createTileCache(), mapView.getModel().mapViewPosition, tileSource, GRAPHIC_FACTORY);
    }

    private TileCache createTileCache() {
        TileCache firstLevelTileCache = new InMemoryTileCache(64);
        // TODO think about replacing with file system cache that survives restarts
        // File cacheDirectory = new File(System.getProperty("java.io.tmpdir"), "mapsforge");
        // TileCache secondLevelTileCache = new FileSystemTileCache(1024, cacheDirectory, GRAPHIC_FACTORY);
        // return new TwoLevelTileCache(firstLevelTileCache, secondLevelTileCache);
        return firstLevelTileCache;
    }

    protected void setModel(PositionsModel positionsModel,
                            PositionsSelectionModel positionsSelectionModel,
                            CharacteristicsModel characteristicsModel,
                            UnitSystemModel unitSystemModel) {
        this.positionsModel = positionsModel;
        this.positionsSelectionModel = positionsSelectionModel;
        this.characteristicsModel = characteristicsModel;
        this.unitSystemModel = unitSystemModel;

        this.selectionUpdater = new SelectionUpdater(positionsModel, new SelectionOperation() {
            private java.util.Map<NavigationPosition, Marker> positionsToMarkers = new HashMap<NavigationPosition, Marker>();

            public void add(List<NavigationPosition> positions) {
                for (NavigationPosition position : positions) {
                    Marker marker = new Marker(asLatLong(position), markerIcon, 8, -16);
                    getLayerManager().getLayers().add(marker);
                    positionsToMarkers.put(position, marker);
                }
            }

            public void remove(List<NavigationPosition> positions) {
                for (NavigationPosition position : positions) {
                    Marker marker = positionsToMarkers.get(position);
                    if (marker != null) {
                        getLayerManager().getLayers().remove(marker);
                        positionsToMarkers.remove(position);
                    } else
                        System.out.println("Could not find layer for position");
                }
            }
        });

        this.routeUpdater = new TrackUpdater(positionsModel, new TrackOperation() {
            private java.util.Map<PositionPair, Layer> pairsToLines = new HashMap<PositionPair, Layer>();
            private java.util.Map<PositionPair, Double> pairsToDistances = new HashMap<PositionPair, Double>();
            private java.util.Map<PositionPair, Long> pairsToTimes = new HashMap<PositionPair, Long>();

            public void add(final List<PositionPair> pairs) {
                final DownloadFuture future = routingService.downloadRoutingDataFor(asLongitudeAndLatitude(pairs));
                if (future.isRequiresDownload()) {
                    drawBeeline(pairs);
                    fireDistanceAndTime();

                    executor.execute(new Runnable() {
                        public void run() {
                            future.download();
                            removeLines(pairs);
                            drawRoute(pairs);
                            fireDistanceAndTime();
                        }
                    });
                } else {
                    drawRoute(pairs);
                    fireDistanceAndTime();
                }
            }

            private void drawBeeline(List<PositionPair> pairs) {
                int tileSize = mapView.getModel().displayModel.getTileSize();
                for (PositionPair pair : pairs) {
                    Line line = new Line(asLatLong(pair.getFirst()), asLatLong(pair.getSecond()), ROUTE_DOWNLOADING_PAINT, tileSize);
                    getLayerManager().getLayers().add(line);
                    pairsToLines.put(pair, line);

                    Double distance = pair.getFirst().calculateDistance(pair.getSecond());
                    pairsToDistances.put(pair, distance);
                    Long time = pair.getFirst().calculateTime(pair.getSecond());
                    pairsToTimes.put(pair, time);
                }
            }

            private void removeLines(List<PositionPair> pairs) {
                for (PositionPair pair : pairs) {
                    Layer line = pairsToLines.get(pair);
                    if (line != null) {
                        getLayerManager().getLayers().remove(line);
                        pairsToLines.remove(pair);
                        pairsToDistances.remove(pair);
                        pairsToTimes.remove(pair);
                    } else
                        System.out.println("Could not find layer for position");
                }
            }

            private void drawRoute(List<PositionPair> pairs) {
                int tileSize = mapView.getModel().displayModel.getTileSize();
                for (PositionPair pair : pairs) {
                    List<LatLong> latLongs = calculateRoute(pair);

                    Polyline line = new Polyline(latLongs, ROUTE_PAINT, tileSize);
                    getLayerManager().getLayers().add(line);
                    pairsToLines.put(pair, line);
                }
            }

            private List<LatLong> calculateRoute(PositionPair pair) {
                List<LatLong> latLongs = new ArrayList<LatLong>();
                latLongs.add(asLatLong(pair.getFirst()));
                RoutingResult intermediate = routingService.getRouteBetween(pair.getFirst(), pair.getSecond());
                if (intermediate != null) {
                    latLongs.addAll(asLatLong(intermediate.getPositions()));
                    pairsToDistances.put(pair, intermediate.getDistance());
                    pairsToTimes.put(pair, intermediate.getTime());
                }
                latLongs.add(asLatLong(pair.getSecond()));
                return latLongs;
            }

            private void fireDistanceAndTime() {
                double totalDistance = 0.0;
                for (Double distance : pairsToDistances.values()) {
                    if (distance != null)
                        totalDistance += distance;
                }
                long totalTime = 0;
                for (Long time : pairsToTimes.values()) {
                    if (time != null)
                        totalTime += time;
                }
                fireCalculatedDistance((int) totalDistance, (int)(totalTime > 0 ? totalTime / 1000 : 0));
            }

            public void remove(List<PositionPair> pairs) {
                removeLines(pairs);
                fireDistanceAndTime();
                Set<NavigationPosition> removed = new HashSet<NavigationPosition>();
                for (PositionPair pair : pairs) {
                    removed.add(pair.getFirst());
                    removed.add(pair.getSecond());
                }
                selectionUpdater.removedPositions(new ArrayList<NavigationPosition>(removed));
            }
        });

        this.trackUpdater = new TrackUpdater(positionsModel, new TrackOperation() {
            private java.util.Map<PositionPair, Line> pairsToLines = new HashMap<PositionPair, Line>();

            public void add(List<PositionPair> pairs) {
                int tileSize = mapView.getModel().displayModel.getTileSize();
                for (PositionPair pair : pairs) {
                    Line line = new Line(asLatLong(pair.getFirst()), asLatLong(pair.getSecond()), TRACK_PAINT, tileSize);
                    getLayerManager().getLayers().add(line);
                    pairsToLines.put(pair, line);
                }
                // TODO fireCalculatedDistance((int)sum, 0);
            }

            public void remove(List<PositionPair> pairs) {
                Set<NavigationPosition> removed = new HashSet<NavigationPosition>();
                for (PositionPair pair : pairs) {
                    Line line = pairsToLines.get(pair);
                    if (line != null) {
                        getLayerManager().getLayers().remove(line);
                        pairsToLines.remove(pair);
                    } else
                        System.out.println("Could not find layer for position");
                    removed.add(pair.getFirst());
                    removed.add(pair.getSecond());
                }
                selectionUpdater.removedPositions(new ArrayList<NavigationPosition>(removed));
                // TODO fireCalculatedDistance((int)sum, 0);
            }
        });

        this.waypointUpdater = new WaypointUpdater(positionsModel, new WaypointOperation() {
            private java.util.Map<NavigationPosition, Marker> positionsToMarkers = new HashMap<NavigationPosition, Marker>();

            public void add(List<NavigationPosition> positions) {
                for (NavigationPosition position : positions) {
                    Marker marker = new Marker(asLatLong(position), waypointIcon, 1, 0);
                    getLayerManager().getLayers().add(marker);
                    positionsToMarkers.put(position, marker);
                }
            }

            public void remove(List<NavigationPosition> positions) {
                for (NavigationPosition position : positions) {
                    Marker marker = positionsToMarkers.get(position);
                    if (marker != null) {
                        getLayerManager().getLayers().remove(marker);
                        positionsToMarkers.remove(position);
                    } else
                        System.out.println("Could not find layer for position");
                }
                selectionUpdater.removedPositions(positions);
            }
        });

        this.eventMapUpdater = getEventMapUpdaterFor(Waypoints);

        characteristicsModel.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent e) {
            }

            public void intervalRemoved(ListDataEvent e) {
            }

            public void contentsChanged(ListDataEvent e) {
                updateRouteButDontRecenter();
            }
        });

        getPositionsModel().addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                switch (e.getType()) {
                    case INSERT:
                        eventMapUpdater.handleAdd(e.getFirstRow(), e.getLastRow());
                        break;
                    case UPDATE:
                        if (getPositionsModel().isContinousRange())
                            return;
                        if (!(e.getColumn() == DESCRIPTION_COLUMN_INDEX ||
                                e.getColumn() == LONGITUDE_COLUMN_INDEX ||
                                e.getColumn() == LATITUDE_COLUMN_INDEX ||
                                e.getColumn() == ALL_COLUMNS))
                            return;

                        boolean allRowsChanged = isFirstToLastRow(e);
                        if (!allRowsChanged)
                            eventMapUpdater.handleUpdate(e.getFirstRow(), e.getLastRow());
                        if (allRowsChanged)
                            centerAndZoom(getMapBoundingBox());

                        break;
                    case DELETE:
                        eventMapUpdater.handleRemove(e.getFirstRow(), e.getLastRow());
                        break;
                }
            }
        });
    }

    private java.util.Map<LocalMap, Layer> mapsToLayers = new HashMap<LocalMap, Layer>();

    private void handleMapAndThemeUpdate() {
        Layers layers = getLayerManager().getLayers();

        // remove old map
        for (LocalMap map : mapsToLayers.keySet()) {
            Layer layer = mapsToLayers.get(map);
            layers.remove(layer);
        }
        mapsToLayers.clear();

        // add new map with a theme
        LocalMap map = mapManager.getDisplayedMapModel().getItem();
        Theme theme = mapManager.getAppliedThemeModel().getItem();
        Layer layer = map.isRenderer() ? createTileRendererLayer(map, theme) : createTileDownloadLayer(map.getTileSource());
        mapsToLayers.put(map, layer);

        // add map as the first to be behind all additional layers
        layers.add(0, layer);

        // then start download layer threads
        if (layer instanceof TileDownloadLayer)
            ((TileDownloadLayer) layer).start();

        centerAndZoom(getMapBoundingBox());
        log.info("Using map " + mapsToLayers.keySet() + " and theme " + theme);
    }

    private BaseRoute lastRoute = null;
    private RouteCharacteristics lastCharacteristics = Waypoints; // corresponds to default eventMapUpdater

    private void updateRouteButDontRecenter() {
        // avoid duplicate work
        RouteCharacteristics characteristics = MapsforgeMapView.this.characteristicsModel.getSelectedCharacteristics();
        BaseRoute route = getPositionsModel().getRoute();
        if (lastCharacteristics.equals(characteristics) && lastRoute != null && lastRoute.equals(getPositionsModel().getRoute()))
            return;
        lastCharacteristics = characteristics;
        lastRoute = route;

        // throw away running routing executions      // TODO use signals later
        executor.shutdownNow();
        executor = newSingleThreadExecutor();

        // remove all from previous event map updater
        eventMapUpdater.handleRemove(0, MAX_VALUE);

        // select current event map updater and let him add all
        eventMapUpdater = getEventMapUpdaterFor(characteristics);
        eventMapUpdater.handleAdd(0, getPositionsModel().getRowCount() - 1);
    }

    private LayerManager getLayerManager() {
        return mapView.getLayerManager();
    }

    private EventMapUpdater getEventMapUpdaterFor(RouteCharacteristics characteristics) {
        switch (characteristics) {
            case Route:
                return routeUpdater;
            case Track:
                return trackUpdater;
            case Waypoints:
                return waypointUpdater;
            default:
                throw new IllegalArgumentException("RouteCharacteristics " + characteristics + " is not supported");
        }
    }

    private PositionsModel getPositionsModel() {
        return positionsModel;
    }

    public boolean isSupportedPlatform() {
        return true;
    }

    public boolean isInitialized() {
        return true;
    }

    public Throwable getInitializationCause() {
        return null;
    }

    public void dispose() {
        NavigationPosition center = getCenter();
        preferences.putDouble(CENTER_LONGITUDE_PREFERENCE, center.getLongitude());
        preferences.putDouble(CENTER_LATITUDE_PREFERENCE, center.getLatitude());
        int zoom = getZoom();
        preferences.putInt(CENTER_ZOOM_PREFERENCE, zoom);

        executor.shutdownNow();
        mapView.destroy();
    }

    public Component getComponent() {
        return mapSelector.getComponent();
    }

    public void resize() {
        // intentionally left empty
    }

    public void setRecenterAfterZooming(boolean recenterAfterZooming) {
        this.recenterAfterZooming = recenterAfterZooming;
    }

    public void setShowCoordinates(boolean showCoordinates) {
        // TODO implement me
    }

    public void setShowWaypointDescription(boolean showWaypointDescription) {
        // TODO implement me
    }

    public void setTravelMode(TravelMode travelMode) {
        // TODO implement me
    }

    public void setAvoidHighways(boolean avoidHighways) {
        // TODO implement me
    }

    public void setAvoidTolls(boolean avoidTolls) {
        // TODO implement me
    }

    private Polyline mapBorder, routeBorder;

    public void showMapBorder(BoundingBox mapBoundingBox) {
        if (mapBorder != null) {
            getLayerManager().getLayers().remove(mapBorder);
            mapBorder = null;
        }
        if (routeBorder != null) {
            getLayerManager().getLayers().remove(routeBorder);
            routeBorder = null;
        }

        if (mapBoundingBox != null)
            mapBorder = drawBorder(mapBoundingBox);

        List<BaseNavigationPosition> positions = getPositionsModel().getRoute().getPositions();
        if (positions.size() > 0)
            routeBorder = drawBorder(new BoundingBox(positions));

        centerAndZoom(mapBoundingBox);
    }

    private Polyline drawBorder(BoundingBox boundingBox) {
        Paint paint = GRAPHIC_FACTORY.createPaint();
        paint.setColor(org.mapsforge.core.graphics.Color.BLUE);
        paint.setStrokeWidth(3);
        paint.setDashPathEffect(new float[]{3, 12});
        Polyline polyline = new Polyline(asLatLong(boundingBox), paint, mapView.getModel().displayModel.getTileSize());
        getLayerManager().getLayers().add(polyline);
        return polyline;
    }

    private BoundingBox getMapBoundingBox() {
        Collection<Layer> values = mapsToLayers.values();
        if (!values.isEmpty()) {
            Layer layer = values.iterator().next();
            if (layer instanceof TileRendererLayer) {
                TileRendererLayer tileRendererLayer = (TileRendererLayer) layer;
                return toBoundingBox(tileRendererLayer.getMapDatabase().getMapFileInfo().boundingBox);
            }
        }
        return null;
    }

    private void centerAndZoom(BoundingBox mapBoundingBox) {
        if (getPositionsModel() == null)
            return;

        List<NavigationPosition> positions = new ArrayList<NavigationPosition>();
        BoundingBox routeBoundingBox = new BoundingBox(getPositionsModel().getRoute().getPositions());
        positions.add(routeBoundingBox.getNorthEast());
        positions.add(routeBoundingBox.getSouthWest());

        if (mapBoundingBox != null && !mapBoundingBox.contains(routeBoundingBox)) {
            positions.add(mapBoundingBox.getNorthEast());
            positions.add(mapBoundingBox.getSouthWest());
        }

        BoundingBox both = new BoundingBox(positions);
        zoomToBounds(both);
        setCenter(both.getCenter());
    }

    private LongitudeAndLatitude asLongitudeAndLatitude(NavigationPosition position) {
        return new LongitudeAndLatitude(position.getLongitude(), position.getLatitude());
    }

    private List<LongitudeAndLatitude> asLongitudeAndLatitude(List<PositionPair> pairs) {
        List<LongitudeAndLatitude> result = new ArrayList<>();
        for (PositionPair pair : pairs) {
            result.add(asLongitudeAndLatitude(pair.getFirst()));
            result.add(asLongitudeAndLatitude(pair.getSecond()));
        }
        return result;
    }


    public NavigationPosition getCenter() {
        return asNavigationPosition(mapView.getModel().mapViewPosition.getCenter());
    }

    public void setCenter(LatLong center) {
        MapViewProjection projection = new MapViewProjection(mapView);
        LatLong upperLeft = projection.fromPixels(0, 0);
        Dimension dimension = mapView.getDimension();
        LatLong lowerRight = projection.fromPixels(dimension.width, dimension.height);
        if (upperLeft == null || lowerRight == null || recenterAfterZooming ||
                !new org.mapsforge.core.model.BoundingBox(lowerRight.latitude, upperLeft.longitude, upperLeft.latitude, lowerRight.longitude).contains(center))
            mapView.getModel().mapViewPosition.animateTo(center);
    }

    public void setCenter(NavigationPosition center) {
        setCenter(asLatLong(center));
    }

    private int getZoom() {
        return mapView.getModel().mapViewPosition.getZoomLevel();
    }

    private void setZoom(int zoom) {
        mapView.getModel().mapViewPosition.setZoomLevel((byte) zoom);
    }

    private void zoomToBounds(org.mapsforge.core.model.BoundingBox boundingBox) {
        Dimension dimension = mapView.getModel().mapViewDimension.getDimension();
        if (dimension == null)
            return;
        byte zoom = zoomForBounds(dimension, boundingBox, mapView.getModel().displayModel.getTileSize());
        // zoom out a bit if the bounding box is pretty large since the user selected a wrong map in the MapsDialog
        if (abs(boundingBox.minLatitude - boundingBox.maxLatitude) > 10.0 || abs(boundingBox.maxLongitude - boundingBox.minLongitude) > 10.0)
            zoom -= 1;
        setZoom(zoom);
    }

    private void zoomToBounds(BoundingBox boundingBox) {
        zoomToBounds(asBoundingBox(boundingBox));
    }


    public void print(String title, boolean withDirections) {
        // TODO implement me
    }

    public void insertAllWaypoints(int[] startPositions) {
        // TODO implement me
    }

    public void insertOnlyTurnpoints(int[] startPositions) {
        // TODO implement me
    }

    public void setSelectedPositions(int[] selectedPositions, boolean replaceSelection) {
        if (selectionUpdater == null)
            return;
        selectionUpdater.setSelectedPositions(selectedPositions, replaceSelection);
    }

    // listeners

    private final List<MapViewListener> mapViewListeners = new CopyOnWriteArrayList<MapViewListener>();

    public void addMapViewListener(MapViewListener listener) {
        mapViewListeners.add(listener);
    }

    public void removeMapViewListener(MapViewListener listener) {
        mapViewListeners.remove(listener);
    }

    private void fireCalculatedDistance(int meters, int seconds) {
        for (MapViewListener listener : mapViewListeners) {
            listener.calculatedDistance(meters, seconds);
        }
    }

    private LatLong getMousePosition() {
        Point point = mapViewMouseEventListener.getMousePosition();
        return point != null ? new MapViewProjection(mapView).fromPixels(point.getX(), point.getY()) :
                mapView.getModel().mapViewPosition.getCenter();
    }

    private double getThresholdForPixel(LatLong latLong, int pixel) {
        double metersPerPixel = calculateGroundResolution(latLong.latitude,
                mapView.getModel().mapViewPosition.getZoomLevel(), mapView.getModel().displayModel.getTileSize());
        return metersPerPixel * pixel;
    }

    private void selectPosition(Double longitude, Double latitude, Double threshold, boolean replaceSelection) { // TODO same as in BaseMapView
        int row = positionsModel.getClosestPosition(longitude, latitude, threshold);
        if (row != -1)
            positionsSelectionModel.setSelectedPositions(new int[]{row}, replaceSelection);
    }

    private class SelectPositionAction extends FrameAction {
        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                Double threshold = getThresholdForPixel(latLong, 15);
                selectPosition(latLong.longitude, latLong.latitude, threshold, true);
            }
        }
    }

    private class ExtendSelectionAction extends FrameAction {
        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                Double threshold = getThresholdForPixel(latLong, 15);
                selectPosition(latLong.longitude, latLong.latitude, threshold, false);
            }
        }
    }

    private class AddPositionAction extends FrameAction {
        private int getAddRow() { // TODO same as in BaseMapView
            List<NavigationPosition> lastSelectedPositions = selectionUpdater.getCurrentSelection();
            NavigationPosition position = lastSelectedPositions.size() > 0 ? lastSelectedPositions.get(lastSelectedPositions.size() - 1) : null;
            // quite crude logic to be as robust as possible on failures
            if (position == null && positionsModel.getRowCount() > 0)
                position = positionsModel.getPosition(positionsModel.getRowCount() - 1);
            return position != null ? positionsModel.getIndex(position) + 1 : 0;
        }

        private void insertPosition(int row, Double longitude, Double latitude) { // TODO unify with different code path from AddPositionAction
            positionsModel.add(row, longitude, latitude, null, null, null, positionAugmenter.createDescription(positionsModel.getRowCount() + 1));
            positionsSelectionModel.setSelectedPositions(new int[]{row}, true);

            positionAugmenter.complementDescription(row, longitude, latitude);
            positionAugmenter.complementElevation(row, longitude, latitude);
            positionAugmenter.complementTime(row, null, true);
        }

        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                int row = getAddRow();
                insertPosition(row, latLong.longitude, latLong.latitude);
            }
        }
    }

    private class DeletePositionAction extends FrameAction {
        private void removePosition(Double longitude, Double latitude, Double threshold) {
            int row = positionsModel.getClosestPosition(longitude, latitude, threshold);
            if (row != -1) {
                positionsModel.remove(new int[]{row});
            }
        }

        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                Double threshold = getThresholdForPixel(latLong, 15);
                removePosition(latLong.longitude, latLong.latitude, threshold);
            }
        }
    }

    private class CenterAction extends FrameAction {
        public void run() {
            mapViewMouseEventListener.centerToMousePosition();
        }
    }

    private class ZoomAction extends FrameAction {
        private byte zoomLevelDiff;

        private ZoomAction(int zoomLevelDiff) {
            this.zoomLevelDiff = (byte) zoomLevelDiff;
        }

        public void run() {
            mapViewMouseEventListener.zoomToMousePosition(zoomLevelDiff);
        }
    }
}
