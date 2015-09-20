package me.gurinderhans.sfumaps.ui.activities;

import android.content.Context;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.jakewharton.disklrucache.DiskLruCache;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.SaveCallback;
import com.tokenautocomplete.TokenCompleteTextView.TokenListener;

import java.util.List;

import me.gurinderhans.sfumaps.BuildConfig;
import me.gurinderhans.sfumaps.R;
import me.gurinderhans.sfumaps.devtools.PathMaker;
import me.gurinderhans.sfumaps.devtools.placecreator.controllers.PlaceFormDialog;
import me.gurinderhans.sfumaps.factory.classes.MapPlace;
import me.gurinderhans.sfumaps.factory.classes.PathSearch;
import me.gurinderhans.sfumaps.factory.libs.sliding_up_panel.SlidingUpPanel;
import me.gurinderhans.sfumaps.ui.controllers.SlidingUpPanelController;
import me.gurinderhans.sfumaps.ui.views.CustomMapFragment;
import me.gurinderhans.sfumaps.ui.views.MapPlaceSearchCompletionView;
import me.gurinderhans.sfumaps.utils.CachedTileProvider;
import me.gurinderhans.sfumaps.utils.MapTools;
import me.gurinderhans.sfumaps.utils.MercatorProjection;
import me.gurinderhans.sfumaps.utils.SVGTileProvider;

import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_NONE;
import static me.gurinderhans.sfumaps.app.Keys.ParseMapPlace.CLASS;
import static me.gurinderhans.sfumaps.app.Keys.ParseMapPlace.PARENT_PLACE;
import static me.gurinderhans.sfumaps.factory.classes.MapPlace.mAllMapPlaces;
import static me.gurinderhans.sfumaps.utils.MarkerCreator.createPlaceMarker;
import static me.gurinderhans.sfumaps.utils.MercatorProjection.fromPointToLatLng;

public class MainActivity extends AppCompatActivity
		implements
		OnCameraChangeListener,
		OnMapClickListener,
		OnMapLongClickListener,
		OnMarkerClickListener,
		OnMarkerDragListener,
		OnClickListener,
		TokenListener {

	protected static final String TAG = MainActivity.class.getSimpleName();

	/**
	 * The Map View
	 */
	private GoogleMap mMap;


	/**
	 * Custom controller to handle the sliding panel
	 */
	private SlidingUpPanelController mPanelController;


	/**
	 * Main activity toolbar
	 */
	private Toolbar mToolbar;


	/**
	 * Get directions floating action button
	 */
	private FloatingActionButton mDirectionsFAB;


	/**
	 * The main app search box
	 */
	private MapPlaceSearchCompletionView mMapSearchView;


	/**
	 * Toolbar search box, holds the place navigating from
	 */
	private MapPlaceSearchCompletionView mNavigationFromSearchView;


	/**
	 * Toolbar search box, holds the place navigating to
	 */
	private MapPlaceSearchCompletionView mNavigationToSearchView;


	/**
	 * Stores the current map zoom
	 * <p/>
	 * When onCameraChange() is called, this gets compared to the 'new' zoom to see if the zoom
	 * level actually changed.
	 */
	private int mMapZoom;


	/**
	 * Disk cache used to cache map tiles that were not previously in cache.
	 */
	private DiskLruCache mTileCache;


	/**
	 * Search adapter used for searching through map places
	 */
	private ArrayAdapter<MapPlace> mMapSearchAdapter;


	/**
	 * PathSearch.class used to create path searches
	 */
	private PathSearch mPathSearch;


	/**
	 * Holds the current selected place, i.e. the clicked marker
	 */
	private MapPlace mSelectedPlace;


	@Override
	protected void onCreate(Bundle savedInstanceState) {

		this.setTheme(R.style.MainActivity);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// setup from top down
		setupStatusBar();
		setUpSearchAndToolbar();
		setUpMap();
		fetchPlaces();
		setupFABAndSlidingPanel();
		manageDevMode();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.get_directions_fab:
				// show search toolbar asking for place {FROM} and {TO}
				mToolbar.animate()
						.translationY(0)
						.setInterpolator(new AccelerateInterpolator())
						.setDuration(150l)
						.start();

				mDirectionsFAB.hide();

				if (mMapSearchView.getObjects().size() == 1) {
					mNavigationFromSearchView.addObject(mMapSearchView.getObjects().get(0));
				}
				if (mSelectedPlace != null) {
					mNavigationFromSearchView.addObject(mSelectedPlace);
				}

				mPanelController.hidePanel();

				break;
			default:
				break;
		}
	}

	@Override
	public void onCameraChange(CameraPosition cameraPosition) {

		// Temp: set map zoom on textview
		((TextView) findViewById(R.id.map_current_zoom)).setText(cameraPosition.zoom + "");

		// 1. limit map max zoom
		float maxZoom = 8f;
		if (cameraPosition.zoom > maxZoom)
			mMap.animateCamera(CameraUpdateFactory.zoomTo(maxZoom));

		// 2. load this zoom markers
		if (mMapZoom != (int) cameraPosition.zoom) { // on zoom change
			mMapZoom = (int) cameraPosition.zoom;
			syncMarkers();
		}
	}

	@Override
	public void onMapClick(LatLng latLng) {
		mPanelController.hidePanel();
		mSelectedPlace = null;
	}

	@Override
	public void onMapLongClick(LatLng latLng) {
		if (BuildConfig.DEBUG && !PathMaker.isEditingMap) {

			MapPlace newPlace = new MapPlace();
			newPlace.setPosition(MercatorProjection.fromLatLngToPoint(latLng));
			newPlace.setMapGizmo(createPlaceMarker(getApplicationContext(), mMap, newPlace));
			mAllMapPlaces.add(newPlace);

			// send to edit
			new PlaceFormDialog(this,
					getPlaceIndex(fromPointToLatLng(newPlace.getPosition())))
					.show();
		}
	}

	@Override
	public boolean onMarkerClick(Marker marker) {

		// find the clicked marker
		int clickedPlaceIndex = getPlaceIndex(marker.getPosition());
		if (clickedPlaceIndex != -1) {
			if (BuildConfig.DEBUG) { // edit place
				new PlaceFormDialog(this, clickedPlaceIndex).show();
			} else {
				mSelectedPlace = mAllMapPlaces.get(clickedPlaceIndex);

				mPanelController.setPlace(mSelectedPlace);

				// set on place from
				mNavigationFromSearchView.addObject(mSelectedPlace);

				// show search toolbar asking for place {FROM} and {TO}
				mToolbar.animate()
						.translationY(-1 * getToolbarHeight())
						.setInterpolator(new AccelerateInterpolator())
						.setDuration(150l)
						.start();

				mDirectionsFAB.show();
			}
		}

		return true;
	}

	@Override
	public void onMarkerDragStart(Marker marker) {
	}

	@Override
	public void onMarkerDrag(Marker marker) {
	}

	@Override
	public void onMarkerDragEnd(Marker marker) {

		// find the clicked marker
		int draggedPlaceIndex = getPlaceIndex(marker.getPosition());
		if (draggedPlaceIndex != -1) {
			mAllMapPlaces.get(draggedPlaceIndex).setPosition(
					MercatorProjection.fromLatLngToPoint(marker.getPosition())
			);

			mAllMapPlaces.get(draggedPlaceIndex).savePlaceWithCallback(new SaveCallback() {
				@Override
				public void done(ParseException e) {
					Snackbar.make(findViewById(android.R.id.content), "Place location updated", Snackbar.LENGTH_LONG).show();
				}
			});
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:

				int statusBarHeight = getStatusBarHeight();
				int toolbarHeight = getResources().getDimensionPixelSize(R.dimen.activity_main_toolbar_height);
				int extraPadding = getResources().getDimensionPixelOffset(R.dimen.activity_main_toolbar_bottom_padding);

				// show search toolbar asking for place {FROM} and {TO}
				mToolbar.animate()
						.translationY(-(toolbarHeight + statusBarHeight + extraPadding))
						.setInterpolator(new AccelerateInterpolator())
						.setDuration(150l)
						.start();

				mDirectionsFAB.show();

				mPathSearch.clearPaths();

				mNavigationFromSearchView.clear();
				mNavigationToSearchView.clear();

				mSelectedPlace = null;

				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onTokenAdded(Object o) {
		if (mNavigationFromSearchView.getObjects().size() == 1 && mNavigationToSearchView.getObjects().size() == 1) {
			hideKeyboard();
			mPathSearch.newSearch(
					mNavigationFromSearchView.getObjects().get(0),
					mNavigationToSearchView.getObjects().get(0)
			);
		}

		if (mMapSearchView.getObjects().size() == 1) {
			hideKeyboard();
			mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(fromPointToLatLng(mMapSearchView.getObjects().get(0).getPosition()), mMapSearchView.getObjects().get(0).getZooms().get(0)));
		}
	}

	@Override
	public void onTokenRemoved(Object o) {
	}


	/**
	 * Makes the status bar transparent and allows views to be drawn behind the status bar
	 */
	private void setupStatusBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.transparent_status_bar_color));
	}


	/**
	 * Initializes map search box and toolbar search
	 */
	private void setUpSearchAndToolbar() {
		mToolbar = (Toolbar) findViewById(R.id.toolbar);

		setSupportActionBar(mToolbar);

		int statusBarHeight = getStatusBarHeight();
		int toolbarHeight = getResources().getDimensionPixelSize(R.dimen.activity_main_toolbar_height);
		int extraPadding = getResources().getDimensionPixelOffset(R.dimen.activity_main_toolbar_bottom_padding);

		mToolbar.setPadding(0, statusBarHeight + extraPadding, 0, extraPadding);
		mToolbar.setTranslationY(-(toolbarHeight + statusBarHeight + extraPadding));

		// add the search layout
		View view = LayoutInflater.from(this).inflate(R.layout.activity_main_toolbar_search, mToolbar, false);
		mToolbar.addView(view);

		// customize action bar
		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayShowTitleEnabled(false);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		// search

		mMapSearchView = (MapPlaceSearchCompletionView) findViewById(R.id.main_search_view);
		mMapSearchView.setLayoutId(R.layout.activity_main_placesearch_token_layout);

		mNavigationFromSearchView = (MapPlaceSearchCompletionView) mToolbar.findViewById(R.id.place_from);
		mNavigationToSearchView = (MapPlaceSearchCompletionView) mToolbar.findViewById(R.id.place_to);

		mMapSearchAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line);

		// setup search adapter
		mMapSearchView.setAdapter(mMapSearchAdapter);
		mNavigationFromSearchView.setAdapter(mMapSearchAdapter);
		mNavigationToSearchView.setAdapter(mMapSearchAdapter);

		// add token listeners
		mMapSearchView.setTokenListener(this);
		mNavigationFromSearchView.setTokenListener(this);
		mNavigationToSearchView.setTokenListener(this);

	}


	/**
	 * Retrieves the Google Maps fragment and loads the custom settings such as custom map tiles
	 * plus registers different listener events on the map
	 */
	private void setUpMap() {

		// cache for map tiles
		mTileCache = MapTools.openDiskCache(this);

		// map view
		mMap = ((CustomMapFragment) getSupportFragmentManager()
				.findFragmentById(R.id.map)).getMap();

		mMap.setMapType(MAP_TYPE_NONE);
		mMap.setIndoorEnabled(false);

		// hide the marker toolbar - the two buttons on the bottom right that go to google maps
		mMap.getUiSettings().setMapToolbarEnabled(false);

		// event listeners
		mMap.setOnCameraChangeListener(this);
		mMap.setOnMapClickListener(this);
		mMap.setOnMapLongClickListener(this);
		mMap.setOnMarkerClickListener(this);
		mMap.setOnMarkerDragListener(this);

		// base map overlay
		mMap.addTileOverlay(new TileOverlayOptions()
				.tileProvider(
						getTileProvider(1, new SVGTileProvider(MapTools.getBaseMapTiles(this),
								getResources().getDisplayMetrics().densityDpi / 160f)))
				.zIndex(10));

		// overlay tile provider to switch floor level stuff
		mMap.addTileOverlay(new TileOverlayOptions()
				.tileProvider(
						getTileProvider(2, new SVGTileProvider(MapTools.getOverlayTiles(this),
								getResources().getDisplayMetrics().densityDpi / 160f)))
				.zIndex(11));

		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2f));

		mPathSearch = new PathSearch(mMap);
	}


	/**
	 * Makes an asynchronous call to the Parse servers and loads all the @link{MapPlace} objects
	 * into search adapter @link{mMapSearchAdapter}
	 */
	public void fetchPlaces() {
		// TODO: 15-09-17 Use local data-store as well
		ParseQuery<MapPlace> query = ParseQuery.getQuery(CLASS);
		query.include(PARENT_PLACE);
		query.findInBackground(new FindCallback<MapPlace>() {
			@Override
			public void done(List<MapPlace> objects, ParseException e) {
				if (e != null) // There was an error or the network wasn't available.
					return;

				for (MapPlace place : objects) {

					// FIXME: 15-09-20 an expensive call for the UI thread
					Marker marker = createPlaceMarker(getApplicationContext(), mMap, place);
					marker.setVisible(false);

					place.setMapGizmo(marker);

					mAllMapPlaces.add(place);
				}

				mMapSearchAdapter.addAll(objects);

				syncMarkers();
			}
		});
	}


	/**
	 * Sets up the @link{SlidingUpPanel} and @link{FloatingActionButton} to be used for the app
	 */
	private void setupFABAndSlidingPanel() {
		mDirectionsFAB = (FloatingActionButton) findViewById(R.id.get_directions_fab);
		mDirectionsFAB.setOnClickListener(this);

		mPanelController = new SlidingUpPanelController(
				(SlidingUpPanel) findViewById(R.id.sliding_panel), mDirectionsFAB);
	}


	/**
	 * Checks if @link{BuildConfig.DEBUG} is true, if yes, enables the custom dev options
	 */
	private void manageDevMode() {
		if (BuildConfig.DEBUG)
			PathMaker.createPathMaker(this, mMap);
	}


	/**
	 * Helper method to hide the keyboard
	 */
	private void hideKeyboard() {
		// Check if no view has focus:
		View view = this.getCurrentFocus();
		if (view != null) {
			InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}


	/**
	 * Helper method to search through the local places list and find the place mathcing given
	 * input position
	 *
	 * @param placePos - position of the place to find
	 * @return - index of place in List @link{mAllMapPlaces}
	 */
	private int getPlaceIndex(LatLng placePos) {

		for (int i = 0; i < mAllMapPlaces.size(); i++) {
			// level the LatLng to same 'precision'
			PointF thisMarkerPoint = MercatorProjection.fromLatLngToPoint(
					mAllMapPlaces.get(i).getMapGizmo().getPosition());

			if (thisMarkerPoint.equals(MercatorProjection.fromLatLngToPoint(placePos)))
				return i;
		}

		return -1;
	}


	/**
	 * Called on zoom change, syncs the map markers to match current zoom level
	 */
	private void syncMarkers() {
		for (MapPlace el : mAllMapPlaces)
			el.getMapGizmo().setVisible(el.getZooms().contains(mMapZoom));
	}


	/**
	 * Helper method to find height of the system status bar
	 *
	 * @return - height of the status bar
	 */
	public int getStatusBarHeight() {
		int result = 0;
		int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}


	/**
	 * Helper method to find height of the activity toolbar
	 *
	 * @return - the total height of toolbar
	 */
	public int getToolbarHeight() {
		int statusBarHeight = getStatusBarHeight();
		int toolbarHeight = getResources().getDimensionPixelSize(R.dimen.activity_main_toolbar_height);
		int extraPadding = getResources().getDimensionPixelOffset(R.dimen.activity_main_toolbar_bottom_padding);

		return statusBarHeight + toolbarHeight + extraPadding;
	}


	/**
	 * Helper method to choose tile provider
	 *
	 * @param layer           - layer number for overlay tile provider to keep cache tiles for each overlay separate
	 * @param svgTileProvider - an instance of SVGTileProvider.class
	 * @return - IF cache supported, CachedTileProvider object ELSE the given SVGTileProvider object
	 */
	public TileProvider getTileProvider(int layer, SVGTileProvider svgTileProvider) {
		return mTileCache == null
				? svgTileProvider
				: new CachedTileProvider(Integer.toString(layer), svgTileProvider, mTileCache);
	}


}
