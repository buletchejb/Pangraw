package com.home.movieappserverside;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.*;
import com.home.movieappserverside.Model.Episode;
import com.home.movieappserverside.Model.VideoDetailUpload;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.Collections;
import java.util.Comparator;
import androidx.annotation.NonNull;  // DITO
import androidx.annotation.Nullable; // DITO
import android.content.Context; // DITO
import android.view.ViewGroup;    // DITO
import com.home.movieappserverside.Adapter.VideoAdapter;
import com.bumptech.glide.Glide;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.app.Activity;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import com.google.android.material.button.MaterialButton;
import androidx.annotation.Nullable;

import android.widget.ImageView;

import java.util.Arrays; // DAGDAG TO SA TAAS
public class MainActivity extends AppCompatActivity {
    String videoCategory = "Movies";
    String videoType = "video";
    String selectedSeason = "Season 1";
    DatabaseReference referenceVideos;
    RecyclerView recyclerVideos;
    List<VideoDetailUpload> videoList;
    VideoAdapter adapter;
    FloatingActionButton fabAdd;
    SearchView searchView;
    ProgressBar progressBar;
    TextView tvEmpty;
    RequestQueue queue;
	private RecyclerView rvEpisodes;
	
	//NEW
	private static final int PICK_IMAGE_REQUEST = 200;
	private Cloudinary cloudinary;
	private MaterialButton btnUploadIptvIcon;
	private TextInputEditText targetThumbField;
	//END NEW
	
	private EpisodeRecyclerAdapter episodeAdapter = new EpisodeRecyclerAdapter(new ArrayList<>(), MainActivity.this);
    // <--- KEYS DITO
    private final String OMDB_API_KEY = "6314061c";
    private final String TMDB_API_KEY = "a389568d89e3ef5ecd253ef9e29299dc"; // <--- KUHA KA DITO: https://www.themoviedb.org/settings/api

    HashMap<String, List<Episode>> tempEpisodesMap = new HashMap<>();
    //HashMap<String, List<String>> tmdbEpisodesMap = new HashMap<>(); // <--- BAGO: Para sa TMDB Ep Titles
    private int tmdbSeriesId = 0;

	// BAGO
	private HashMap<String, List<Episode>> tmdbEpisodesMap = new HashMap<>();
	
	
	
    TextView tvEpCountGlobal;
	AlertDialog dialog; 
	AutoCompleteTextView acSeasonGlobal;
	TextView tvEpisodeCount; // <--- pinalitan ko
	Button btnAddEpisodePopup; // <--- DAGDAG MO TO

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        queue = Volley.newRequestQueue(this);
        referenceVideos = FirebaseDatabase.getInstance().getReference().child("videos");
        recyclerVideos = findViewById(R.id.recyclerVideos);
        fabAdd = findViewById(R.id.fabAdd);
        searchView = findViewById(R.id.searchView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tv_empty);
        recyclerVideos.setLayoutManager(new LinearLayoutManager(this));
        videoList = new ArrayList<>();
        loadVideos();
        setupSearch();
        fabAdd.setOnClickListener(v -> showVideoDialog(null));
    }

    private void setupSearch(){
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
				@Override public boolean onQueryTextSubmit(String query) { return false; }
				@Override public boolean onQueryTextChange(String newText) {
					if(adapter!= null){ adapter.getFilter().filter(newText); }
					return false;
				}
			});
    }

    private void loadVideos(){
        progressBar.setVisibility(View.VISIBLE);
        referenceVideos.addValueEventListener(new ValueEventListener() {
				@Override public void onDataChange(@NonNull DataSnapshot snapshot) {
					progressBar.setVisibility(View.GONE);
					videoList.clear();
					for(DataSnapshot ds : snapshot.getChildren()){
						VideoDetailUpload video = ds.getValue(VideoDetailUpload.class);
						if(video!= null) videoList.add(video);
					}
					if(videoList.isEmpty()){ tvEmpty.setVisibility(View.VISIBLE); recyclerVideos.setVisibility(View.GONE); }
					else { tvEmpty.setVisibility(View.GONE); recyclerVideos.setVisibility(View.VISIBLE); }
					if(adapter == null){
						adapter = new VideoAdapter(MainActivity.this, videoList, new VideoAdapter.OnItemClickListener() {
								@Override public void onEditClick(VideoDetailUpload video) { showVideoDialog(video); }
								@Override public void onDeleteClick(String videoId) { confirmDelete(videoId); }
							});
						recyclerVideos.setAdapter(adapter);
					} else { adapter.updateList(videoList); }
				}
				@Override public void onCancelled(@NonNull DatabaseError error) { progressBar.setVisibility(View.GONE); }
			});
    }

    private void showVideoDialog(VideoDetailUpload video){
		tempEpisodesMap.clear();
		tmdbEpisodesMap.clear();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video, null);
		builder.setView(dialogView);

		// INIT CLOUDINARY
		if(cloudinary == null){
			Map config = new HashMap();
			config.put("cloud_name", "vkouyk3j");
			cloudinary = new Cloudinary(config);
		}

		LinearLayout layoutTitleFetch = dialogView.findViewById(R.id.layout_title_fetch);
		TextInputEditText etTitle = dialogView.findViewById(R.id.video_title);
		Button btnFetch = dialogView.findViewById(R.id.btn_fetch);
		ProgressBar pbFetch = dialogView.findViewById(R.id.pb_fetch);
		LinearLayout layoutMovieFields = dialogView.findViewById(R.id.layout_movie_fields);
		LinearLayout layoutEpisodeFields = dialogView.findViewById(R.id.layout_episode_fields);
		LinearLayout layoutIptvFields = dialogView.findViewById(R.id.layout_iptv_fields);
		TextInputEditText etChannelName = dialogView.findViewById(R.id.et_channel_name);
		TextInputLayout tilChannelName = dialogView.findViewById(R.id.til_channel_name);
		TextInputEditText etIptvUrl = dialogView.findViewById(R.id.et_iptv_url);
		TextInputEditText etIptvIcon = dialogView.findViewById(R.id.et_iptv_icon);
		TextInputEditText etUrl = dialogView.findViewById(R.id.video_url);
		TextInputEditText etThumb = dialogView.findViewById(R.id.thumbnail_url);
		TextInputEditText etDesc = dialogView.findViewById(R.id.movies_description);
		AutoCompleteTextView acCategory = dialogView.findViewById(R.id.ac_category);
		AutoCompleteTextView acType = dialogView.findViewById(R.id.ac_type);
		AutoCompleteTextView acSeason = dialogView.findViewById(R.id.ac_season);
		TextInputLayout tilTitle = dialogView.findViewById(R.id.til_title);
		TextInputLayout tilType = dialogView.findViewById(R.id.til_type);
		TextInputLayout tilDesc = dialogView.findViewById(R.id.til_desc);
		TextInputLayout tilThumb = dialogView.findViewById(R.id.til_thumb);
		Button btnAddEpisode = dialogView.findViewById(R.id.btn_add_episode_popup);
		TextView tvEpCount = dialogView.findViewById(R.id.tv_episode_count);
		rvEpisodes = dialogView.findViewById(R.id.rv_episodes);
		rvEpisodes.setLayoutManager(new LinearLayoutManager(this));
		episodeAdapter = new EpisodeRecyclerAdapter(new ArrayList<>(), MainActivity.this);
		rvEpisodes.setAdapter(episodeAdapter);
		acSeasonGlobal = acSeason;
		tvEpisodeCount = tvEpCount;
		btnAddEpisodePopup = btnAddEpisode;
		View seasonParentLayout = (View) acSeason.getParent().getParent();
		setupDropdowns(acCategory, acType, acSeason);

		// UPLOAD BUTTON - ISA NALANG
		btnUploadIptvIcon = dialogView.findViewById(R.id.btn_upload_iptv_icon);
		btnUploadIptvIcon.setOnClickListener(v -> {
			targetThumbField = etIptvIcon;
			openGallery();
		});

		String title = "Add New Video";
		String btnText = "SAVE";
		selectedSeason = "Season 1";
		boolean isEdit = video != null;
		if(isEdit){
			title = "Edit: " + video.videoTitle;
			btnText = "UPDATE";
			etTitle.setText(video.videoTitle);
			etChannelName.setText(video.channelName);
			etUrl.setText(video.videoUrl);
			etThumb.setText(video.thumbnailUrl);
			etDesc.setText(video.videoDescription);
			acCategory.setText(video.category, false);
			acType.setText(video.video_type, false);
			videoCategory = video.category;
			videoType = video.video_type;
			if(videoCategory.equals("IPTV")){
				etIptvUrl.setText(video.videoUrl);
				etIptvIcon.setText(video.thumbnailUrl);
			}
			loadExistingEpisodesToTemp(video.id);
			loadSeasonsFromFirebase(video.id, acSeason);
		} else {
			ArrayList<String> defaultSeasons = new ArrayList<>();
			for(int i=1; i<=10; i++) defaultSeasons.add("Season " + i);
			ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item, defaultSeasons);
			acSeason.setAdapter(adapter);
		}
		updateFormVisibility(layoutTitleFetch, layoutMovieFields, layoutEpisodeFields, layoutIptvFields, tilType, tilDesc, tilThumb, seasonParentLayout, rvEpisodes, videoCategory);

		btnFetch.setOnClickListener(v -> {
			String movieTitle = etTitle.getText().toString().trim();
			if(movieTitle.isEmpty()){
				Toast.makeText(this, "Ilagay mo muna title", Toast.LENGTH_SHORT).show();
				return;
			}
			if(videoCategory.equals("IPTV")){
				Toast.makeText(this, "OMDB/TMDB is for Movies/Series only", Toast.LENGTH_SHORT).show();
				return;
			}
			pbFetch.setVisibility(View.VISIBLE);
			if(videoCategory.equals("TV series")){
				fetchSeriesFromTMDB(movieTitle, etTitle, etDesc, etThumb, pbFetch);
			} else {
				fetchMovieFromOMDB(movieTitle, etTitle, etDesc, etThumb, pbFetch);
			}
		});

		acCategory.setOnItemClickListener((parent, view, position, id) -> {
			videoCategory = parent.getItemAtPosition(position).toString();
			updateFormVisibility(layoutTitleFetch, layoutMovieFields, layoutEpisodeFields, layoutIptvFields, tilType, tilDesc, tilThumb, seasonParentLayout, rvEpisodes, videoCategory);
		});
		acType.setOnItemClickListener((parent, view, position, id) -> videoType = parent.getItemAtPosition(position).toString());
		acSeason.setOnItemClickListener((parent, view, position, id) -> {
			selectedSeason = parent.getItemAtPosition(position).toString();
			btnAddEpisodePopup.setText("+ Add Episode to " + selectedSeason);
			refreshEpisodeList();
		});
		btnAddEpisodePopup.setOnClickListener(v -> showBulkEpisodeDialog(tvEpisodeCount));

		builder.setTitle(title);
		builder.setPositiveButton(btnText, null);
		builder.setNegativeButton("CANCEL", (d, which) -> d.dismiss());
		dialog = builder.create();
		dialog.show();
		updateFormVisibility(layoutTitleFetch, layoutMovieFields, layoutEpisodeFields, layoutIptvFields, tilType, tilDesc, tilThumb, seasonParentLayout, rvEpisodes, videoCategory);

		dialog.setOnShowListener(d -> {
			if(videoCategory.equals("TV series")) refreshEpisodeList();
		});

		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
			String t = etTitle.getText().toString().trim();
			String channel = etChannelName.getText().toString().trim();
			String u = etUrl.getText().toString().trim();
			String th = etThumb.getText().toString().trim();
			String iptvU = etIptvUrl.getText().toString().trim();
			String iptvTh = etIptvIcon.getText().toString().trim();
			String d = etDesc.getText().toString().trim();

			if(videoCategory.equals("IPTV")){
				if(channel.isEmpty()){
					tilChannelName.setError("Channel Name is required");
					return;
				} else tilChannelName.setError(null);
				if(iptvU.isEmpty()){
					etIptvUrl.setError("IPTV URL is required");
					return;
				} else etIptvUrl.setError(null);
				t = channel;
				u = iptvU;
				th = iptvTh; // GAMIT YUNG IPTV ICON AS THUMB
				videoType = "m3u8";
			} else {
				if(t.isEmpty()){
					tilTitle.setError("Title is required");
					return;
				} else tilTitle.setError(null);
				// MOVIES/TV: REQUIRED YUNG THUMB GALING FETCH
				if(th.isEmpty()){
					tilThumb.setError("Thumbnail required. Click FETCH muna");
					Toast.makeText(this, "Mag FETCH ka muna para sa Thumbnail", Toast.LENGTH_SHORT).show();
					return;
				} else tilThumb.setError(null);
			}

			if(videoCategory.equals("TV series") && tempEpisodesMap.isEmpty()){
				Toast.makeText(this, "Mag add ka muna ng episodes", Toast.LENGTH_SHORT).show();
				return;
			}
			if(videoCategory.equals("Movies") && u.isEmpty()){
				Toast.makeText(this, "Video URL required", Toast.LENGTH_SHORT).show();
				return;
			}

			String uploadId = isEdit ? video.id : referenceVideos.push().getKey();
			long time = System.currentTimeMillis();
			if(isEdit && video.uploadTime != null && video.uploadTime != 0){
				time = video.uploadTime;
			}
			VideoDetailUpload newVideo = new VideoDetailUpload(uploadId, u, t, d, videoCategory, th, videoType, channel, time);
			referenceVideos.child(uploadId).setValue(newVideo);
			referenceVideos.child(uploadId).child("seasons").removeValue().addOnCompleteListener(task -> {
				for(Map.Entry<String, List<Episode>> entry : tempEpisodesMap.entrySet()){
					String seasonKey = entry.getKey();
					for(Episode ep : entry.getValue()){
						referenceVideos.child(uploadId).child("seasons").child(seasonKey).child(ep.episodeId).setValue(ep);
					}
				}
			});
			Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
			dialog.dismiss();
		});
	}

	private void updateFormVisibility( 
		LinearLayout layoutTitleFetch, 
		LinearLayout layoutMovie, 
		LinearLayout layoutEpisode, 
		LinearLayout layoutIptv, 
		TextInputLayout tilType, 
		TextInputLayout tilDesc, 
		TextInputLayout tilThumb, 
		View seasonParentLayout, 
		RecyclerView rvEpisodes, 
		String category 
	){

		if(category.equals("IPTV")){
			layoutTitleFetch.setVisibility(View.GONE);
			layoutMovie.setVisibility(View.GONE);
			layoutEpisode.setVisibility(View.GONE);
			layoutIptv.setVisibility(View.VISIBLE);
			tilType.setVisibility(View.GONE);
			tilDesc.setVisibility(View.GONE);
			tilThumb.setVisibility(View.GONE);
			seasonParentLayout.setVisibility(View.GONE);
			rvEpisodes.setVisibility(View.GONE);
		} else if(category.equals("TV series")){
			layoutTitleFetch.setVisibility(View.VISIBLE);
			layoutMovie.setVisibility(View.GONE);
			layoutEpisode.setVisibility(View.VISIBLE);
			layoutIptv.setVisibility(View.GONE);
			tilType.setVisibility(View.VISIBLE);
			tilDesc.setVisibility(View.VISIBLE);
			tilThumb.setVisibility(View.VISIBLE);
			seasonParentLayout.setVisibility(View.VISIBLE);
			rvEpisodes.setVisibility(View.VISIBLE);
		} else { // Movies
			layoutTitleFetch.setVisibility(View.VISIBLE);
			layoutMovie.setVisibility(View.VISIBLE);
			layoutEpisode.setVisibility(View.GONE);
			layoutIptv.setVisibility(View.GONE);
			tilType.setVisibility(View.VISIBLE);
			tilDesc.setVisibility(View.VISIBLE);
			tilThumb.setVisibility(View.VISIBLE);
			seasonParentLayout.setVisibility(View.GONE);
			rvEpisodes.setVisibility(View.GONE);
		}
	}
	
	//NEW FUTURE
	
	private void openGallery() {
		Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		startActivityForResult(intent, PICK_IMAGE_REQUEST);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && targetThumbField != null) {
			uploadThumbnailToCloudinary(data.getData(), targetThumbField);
		}
	}

	private void uploadThumbnailToCloudinary(Uri imageUri, TextInputEditText targetField) {
		btnUploadIptvIcon.setEnabled(false);
		btnUploadIptvIcon.setText("...");

		new Thread(() -> {
			try {
				String filePath = getRealPathFromURI(imageUri);
				if(filePath == null) {
					throw new Exception("Hindi nakuha yung file path. Baka Android 11+ issue");
				}

				File file = new File(filePath);
				if(!file.exists()) {
					throw new Exception("File not found: " + filePath);
				}

				Map uploadResult = cloudinary.uploader().unsignedUpload(file, "movieapp_unsigned_v2", ObjectUtils.asMap(
																			"folder", "movieapp_iptvicons"
																		));

				String uploadedThumbUrl = (String) uploadResult.get("secure_url");

				if(uploadedThumbUrl == null) {
					throw new Exception("Walang na return na URL si Cloudinary");
				}

				runOnUiThread(() -> {
					targetField.setText(uploadedThumbUrl);
					Toast.makeText(this, "Uploaded ✓", Toast.LENGTH_SHORT).show();
					btnUploadIptvIcon.setText("Upload");
					btnUploadIptvIcon.setEnabled(true);
				});

			} catch (Exception e) {
				e.printStackTrace();
				final String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error"; // <-- FINAL NA TO

				runOnUiThread(() -> {
					Toast.makeText(this, "Upload Failed: " + errorMsg, Toast.LENGTH_LONG).show(); // DAPAT OK NA
					btnUploadIptvIcon.setText("Upload");
					btnUploadIptvIcon.setEnabled(true);
				});
			}
		}).start();
	}

	private String getRealPathFromURI(Uri contentUri) {
		String[] proj = { MediaStore.Images.Media.DATA };
		Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
		if (cursor == null) return contentUri.getPath();
		int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();
		String path = cursor.getString(column_index);
		cursor.close();
		return path;
	}
	
	//GANG DITO



	public class EpisodeRecyclerAdapter extends RecyclerView.Adapter<EpisodeRecyclerAdapter.ViewHolder> {
		private List<Episode> episodes;
		private MainActivity activity; // Kailangan para matawag yung functions sa MainActivity
		public EpisodeRecyclerAdapter(List<Episode> episodes, MainActivity activity) {
			this.episodes = episodes;
			this.activity = activity;
		}
		public void updateList(List<Episode> newList){
			episodes.clear();
			episodes.addAll(newList);
			notifyDataSetChanged();
		}
		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_episode, parent, false);
			return new ViewHolder(view);
		}
		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			Episode ep = episodes.get(position);
			final int pos = position; // BINAGO 1
			String title = ep.episodeTitle // BINAGO 2
                .replace("Episode " + ep.episodeNumber + " - ", "")
                .replace("Ep " + ep.episodeNumber + " - ", "")
                .trim();
			final String cleanTitle = title.isEmpty() ? "Episode " + ep.episodeNumber : title; // BINAGO 3
			holder.tvEpNumber.setText("Episode " + ep.episodeNumber);
			holder.tvEpTitle.setText(cleanTitle);
			// LOAD THUMBNAIL DITO. Pag blank, default icon
			if(ep.thumbnail != null && !ep.thumbnail.isEmpty()){
				Glide.with(holder.itemView.getContext())
                    .load(ep.thumbnail)
                    .placeholder(R.drawable.ic_arrow_collapse_down)
                    .error(R.drawable.ic_arrow_collapse_down)
                    .centerCrop()
                    .into(holder.ivEpThumbnail);
			} else {
				holder.ivEpThumbnail.setImageResource(R.drawable.ic_arrow_collapse_down);
			}
			// CLICK = PLAY/EDIT
			holder.itemView.setOnClickListener(v -> {
				activity.showEditEpisodeDialog(ep); // click = edit agad
			});
			// LONG CLICK PARA DELETE
			holder.itemView.setOnLongClickListener(v -> {
				new AlertDialog.Builder(v.getContext())
                    .setTitle("Delete Episode " + ep.episodeNumber + "?")
                    .setMessage(cleanTitle)
                    .setPositiveButton("DELETE", (d, which) -> {
					if(activity.tempEpisodesMap.containsKey(activity.selectedSeason)){
						activity.tempEpisodesMap.get(activity.selectedSeason).remove(pos); // BINAGO 4: position -> pos
						activity.refreshEpisodeList();
						activity.tvEpisodeCount.setText(activity.tempEpisodesMap.get(activity.selectedSeason).size() + " Episodes");
						Toast.makeText(activity, "Deleted", Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton("CANCEL", null)
                    .show();
				return true;
			});
		}
		@Override
		public int getItemCount() {
			return episodes.size();
		}
		public class ViewHolder extends RecyclerView.ViewHolder {
			TextView tvEpNumber, tvEpTitle;
			ImageView ivEpThumbnail; // DAGDAG
			public ViewHolder(@NonNull View itemView) {
				super(itemView);
				tvEpNumber = itemView.findViewById(R.id.tv_ep_number);
				tvEpTitle = itemView.findViewById(R.id.tv_ep_title);
				ivEpThumbnail = itemView.findViewById(R.id.iv_ep_thumbnail); // DAGDAG
			}
		}
	}
	
	
    private void refreshEpisodeList(){ 
		if(selectedSeason == null || episodeAdapter == null || tvEpisodeCount == null) return; 

		List<Episode> eps = tempEpisodesMap.get(selectedSeason); 
		ArrayList<Episode> displayList = new ArrayList<>(); 

		if(eps != null){ 
			Collections.sort(eps, (e1, e2) -> { 
				try { 
					return Integer.compare(Integer.parseInt(e1.episodeNumber), Integer.parseInt(e2.episodeNumber)); 
				} catch (NumberFormatException e) { 
					return e1.episodeNumber.compareTo(e2.episodeNumber); 
				} 
			}); 
			displayList.addAll(eps); 
		} 

		// FIX: SA RECYCLER VIEW ITO NA GAMIT HINDI clear() addAll()
		episodeAdapter.updateList(displayList); 

		rvEpisodes.setVisibility(displayList.isEmpty() ? View.GONE : View.VISIBLE); // PALIT: lvEpisodes -> rvEpisodes

		updateEpisodeCount(); 

		// TOTAL LAHAT NG SEASONS
		int totalEpisodes = 0;
		for(List<Episode> seasonEps : tempEpisodesMap.values()){
			totalEpisodes += seasonEps.size();
		}
		tvEpisodeCount.setText(totalEpisodes + " Episodes added");

		if(btnAddEpisodePopup != null){
			btnAddEpisodePopup.setText("+ Add Episode to " + selectedSeason); 
		} 
	}

// ITO LANG DAPAT ANG updateEpisodeCount MO
	private void updateEpisodeCount(){
		int total = 0;
		for(List<Episode> list : tempEpisodesMap.values()){
			total += list.size();
		}
		tvEpisodeCount.setText(total + " Episodes added");
	}
	
	
	private void loadSeasonsFromFirebase(String videoId, AutoCompleteTextView acSeason){
		referenceVideos.child(videoId).child("seasons").addListenerForSingleValueEvent(new ValueEventListener() { 
				@Override 
				public void onDataChange(@NonNull DataSnapshot snapshot) { 
					ArrayList<String> seasonList = new ArrayList<>(); 
					for(DataSnapshot seasonSnap : snapshot.getChildren()){ 
						seasonList.add(seasonSnap.getKey()); // "Season 1", "Season 2" 
					} 

					if(seasonList.isEmpty()){ 
						// kung walang season, default 1-10
						for(int i=1; i<=10; i++) seasonList.add("Season " + i); 
					} else {
						// DAGDAG: I-SORT NATIN BY NUMBER
						Collections.sort(seasonList, (s1, s2) -> {
							try {
								int num1 = Integer.parseInt(s1.replaceAll("\\D+", "")); // kuha number lang
								int num2 = Integer.parseInt(s2.replaceAll("\\D+", ""));
								return Integer.compare(num1, num2);
							} catch (NumberFormatException e) {
								return s1.compareTo(s2); // fallback sa string
							}
						});
					}

					ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, R.layout.dropdown_item, seasonList); 
					acSeason.setAdapter(adapter); 

					if(!seasonList.isEmpty()){ 
						selectedSeason = seasonList.get(0); 
						acSeason.setText(selectedSeason, false); 
						btnAddEpisodePopup.setText("+ Add Episode to " + selectedSeason); 
					} 
				} 

				@Override 
				public void onCancelled(@NonNull DatabaseError error) {} 
			}); 
	}
	

    // <--- BAGO: FETCH GALING TMDB
    private void fetchSeriesFromTMDB(String title, TextInputEditText etTitle, TextInputEditText etDesc, TextInputEditText etThumb, ProgressBar pb){
		String searchUrl = "https://api.themoviedb.org/3/search/tv?api_key=" + TMDB_API_KEY + "&query=" + title.replace(" ", "%20");
		StringRequest searchReq = new StringRequest(Request.Method.GET, searchUrl, response -> {
			try {
				JSONObject obj = new JSONObject(response);
				JSONArray results = obj.getJSONArray("results");
				if(results.length() > 0){

					// KUNG IISA LANG, DIRECT NA
					if(results.length() == 1){
						setSeriesData(results.getJSONObject(0), etTitle, etDesc, etThumb);
					} else {
						// KUNG MARAMI, MAG PA-PILI TAYO
						showSeriesPickerDialog(results, etTitle, etDesc, etThumb);
					}

				} else {
					Toast.makeText(this, "Series not found", Toast.LENGTH_SHORT).show();
				}
			} catch (Exception e) { e.printStackTrace(); }
			pb.setVisibility(View.GONE);
		}, error -> { pb.setVisibility(View.GONE); Toast.makeText(this, "TMDB Error", Toast.LENGTH_SHORT).show(); });
		queue.add(searchReq);
	}

	private void setSeriesData(JSONObject series, TextInputEditText etTitle, TextInputEditText etDesc, TextInputEditText etThumb) throws Exception {
		tmdbSeriesId = series.getInt("id");
		etTitle.setText(series.getString("name"));
		etDesc.setText(series.getString("overview"));
		String poster = series.isNull("poster_path")? "" : "https://image.tmdb.org/t/p/w500" + series.getString("poster_path");
		etThumb.setText(poster);
		fetchAllSeasons(tmdbSeriesId); // DITO NA TATAWAG NG SEASONS
		Toast.makeText(this, "Fetched Series Info!", Toast.LENGTH_SHORT).show();
	}

	private void showSeriesPickerDialog(JSONArray results, TextInputEditText etTitle, TextInputEditText etDesc, TextInputEditText etThumb){
		ArrayList<String> names = new ArrayList<>();
		for(int i=0; i<results.length(); i++){
			try {
				JSONObject s = results.getJSONObject(i);
				String name = s.getString("name") + " (" + s.getString("first_air_date").substring(0,4) + ")";
				names.add(name);
			} catch (Exception ignored){}
		}

		new AlertDialog.Builder(this)
			.setTitle("Piliin ang tamang series")
			.setItems(names.toArray(new String[0]), (dialog, which) -> {
            try {
                setSeriesData(results.getJSONObject(which), etTitle, etDesc, etThumb);
            } catch (Exception e) { e.printStackTrace(); }
        }).show();
	}

    private void fetchAllSeasons(int seriesId){
		String url = "https://api.themoviedb.org/3/tv/" + seriesId + "?api_key=" + TMDB_API_KEY;

		StringRequest req = new StringRequest(Request.Method.GET, url, 
        response -> {
            try {
                JSONObject obj = new JSONObject(response);
                JSONArray seasons = obj.getJSONArray("seasons");
                ArrayList<String> seasonList = new ArrayList<>();

                tmdbEpisodesMap.clear();
                tempEpisodesMap.clear();

                for(int i=0; i<seasons.length(); i++){
                    JSONObject s = seasons.getJSONObject(i);
                    int seasonNum = s.getInt("season_number");
                    if(seasonNum > 0){
                        String seasonName = "Season " + seasonNum;
                        seasonList.add(seasonName);
                        fetchEpisodesForSeason(seriesId, seasonNum, seasonName); // tawag per season
                    }
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item, seasonList);
                    acSeasonGlobal.setAdapter(adapter);
                });

            } catch (Exception e) { e.printStackTrace(); }
        }, error -> {});
		queue.add(req);
	}

	private void fetchEpisodesForSeason(int seriesId, int seasonNum, String seasonName){ 
		String url = "https://api.themoviedb.org/3/tv/" + seriesId + "/season/" + seasonNum + "?api_key=" + TMDB_API_KEY; 

		StringRequest req = new StringRequest(Request.Method.GET, url, response -> { 
			try { 
				JSONObject obj = new JSONObject(response); 
				JSONArray episodes = obj.getJSONArray("episodes"); 
				List<Episode> epList = new ArrayList<>(); 

				for(int i = 0; i < episodes.length(); i++){ 
					JSONObject ep = episodes.getJSONObject(i); 
					String epNum = ep.getString("episode_number"); 
					String epTitle = ep.getString("name"); 
					if(epTitle.isEmpty()) epTitle = "Episode " + epNum;

					String stillPath = ep.isNull("still_path") ? "" : "https://image.tmdb.org/t/p/w300" + ep.getString("still_path"); 

					Episode newEp = new Episode( 
						UUID.randomUUID().toString(), 
						epTitle, 
						stillPath, 
						epNum, 
						"", 
						"m3u8" 
					); 
					epList.add(newEp); 
				} 

				tmdbEpisodesMap.put(seasonName, epList); 
				tempEpisodesMap.put(seasonName, new ArrayList<>(epList)); 

				runOnUiThread(() -> { 
					refreshEpisodeList(); 
					if(acSeasonGlobal.getText().toString().isEmpty()){ // DITO INAYOS KO
						acSeasonGlobal.setText(seasonName, false); 
					} 
				}); 
			} catch (Exception e) { 
				e.printStackTrace(); 
			} 
		}, error -> {}); 
		queue.add(req); 
	}

    private void fetchMovieFromOMDB(String title, TextInputEditText etTitle, TextInputEditText etDesc, TextInputEditText etThumb, ProgressBar pb){
        String url = "https://www.omdbapi.com/?t=" + title.replace(" ", "+") + "&apikey=" + OMDB_API_KEY + "&plot=full";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
		response -> {
			pb.setVisibility(View.GONE);
			try {
				JSONObject json = new JSONObject(response);
				if(json.getString("Response").equals("True")){
					etTitle.setText(json.getString("Title"));
					etDesc.setText(json.getString("Plot"));
					String poster = json.getString("Poster");
					if(!poster.equals("N/A")) etThumb.setText(poster);
					Toast.makeText(this, "Fetched Successfully!", Toast.LENGTH_SHORT).show();
				} else { Toast.makeText(this, "Not found: " + json.getString("Error"), Toast.LENGTH_SHORT).show(); }
			} catch (Exception e) { Toast.makeText(this, "Error parsing data", Toast.LENGTH_SHORT).show(); }
		}, error -> { pb.setVisibility(View.GONE); Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show(); });
        queue.add(stringRequest);
    }
	
	
	private void showBulkEpisodeDialog(TextView tvEpCount){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = LayoutInflater.from(this).inflate(R.layout.bulk_episodes, null);
		builder.setView(view);
		TextInputEditText etBulk = view.findViewById(R.id.et_bulk_episodes);
		String selectedSeason = acSeasonGlobal.getText().toString();

		// AUTO FILL GALING TMDB
		if(tmdbEpisodesMap.containsKey(selectedSeason)){
			StringBuilder sb = new StringBuilder();
			for(Episode ep : tmdbEpisodesMap.get(selectedSeason)){
				String url = ep.videoUrl!= null? ep.videoUrl : "";
				sb.append(ep.episodeTitle).append(" | ").append(url).append("\n"); // galing TMDB pa din to
			}
			etBulk.setText(sb.toString());
		}

		builder.setTitle("Bulk Add Episodes - " + selectedSeason);
		builder.setNegativeButton("CANCEL", null);
		builder.setPositiveButton("ADD ALL", null);
		AlertDialog dialog = builder.create();
		dialog.show();

		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
			String bulkText = etBulk.getText().toString().trim();
			if(bulkText.isEmpty()){
				Toast.makeText(this, "Paste ka muna boss", Toast.LENGTH_SHORT).show();
				return;
			}
			String[] lines = bulkText.split("\n");
			if(!tempEpisodesMap.containsKey(selectedSeason)){
				tempEpisodesMap.put(selectedSeason, new ArrayList<>());
			}
			int startNum = tempEpisodesMap.get(selectedSeason).size();
			int added = 0;
			for(String line : lines){
				line = line.trim();
				if(line.isEmpty()) continue;
				String[] parts = line.split("\\|");
				if(parts.length >= 2){
					String rawTitle = parts[0].trim(); // "Episode 1 - Pilot" galing TMDB
					String epUrl = parts[1].trim();
					String episodeId = UUID.randomUUID().toString();
					String epNumber = String.valueOf(startNum + added + 1); // "5"

					// 1. LINISIN YUNG TITLE. Tanggalin "Episode X - "
					String epTitle = rawTitle
						.replace("Episode " + epNumber + " - ", "")
						.replace("Ep " + epNumber + " - ", "")
						.replace("Episode " + (added+1) + " - ", "") // pang bulk auto fill
						.trim();
					if(epTitle.isEmpty()) epTitle = "Episode " + epNumber;

					// 2. KUHAIN YUNG THUMBNAIL GALING TMDB BASE SA epNumber
					String thumb = "";
					if(tmdbEpisodesMap.containsKey(selectedSeason)){
						for(Episode tmdbEp : tmdbEpisodesMap.get(selectedSeason)){
							if(tmdbEp.episodeNumber.equals(epNumber)){
								thumb = tmdbEp.thumbnail!= null? tmdbEp.thumbnail : "";
								break;
							}
						}
					}

					// 3. SAVE
					Episode newEp = new Episode(episodeId, epTitle, thumb, epNumber, epUrl, "m3u8");
					tempEpisodesMap.get(selectedSeason).add(newEp);
					added++;
				}
			}
			refreshEpisodeList();
			tvEpCount.setText(tempEpisodesMap.get(selectedSeason).size() + " Episodes");
			Toast.makeText(this, added + " Episodes Added", Toast.LENGTH_SHORT).show();
			dialog.dismiss();
		});
	}

    private void showEditEpisodeDialog(Episode ep){ 
		AlertDialog.Builder builder = new AlertDialog.Builder(this); 
		View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_episode, null); 
		builder.setView(view); 

		TextInputEditText etTitle = view.findViewById(R.id.et_edit_ep_title); 
		TextInputEditText etUrl = view.findViewById(R.id.et_edit_ep_url); 
		TextInputEditText etThumb = view.findViewById(R.id.et_edit_ep_thumb); 

		etTitle.setText(ep.episodeTitle); 
		etUrl.setText(ep.videoUrl); // FIX: episodeUrl -> videoUrl
		etThumb.setText(ep.thumbnail); // FIX: thumbnailUrl -> thumbnail

		builder.setTitle("Edit Episode " + ep.episodeNumber); 
		builder.setPositiveButton("SAVE", (d, which) -> { 
			ep.episodeTitle = etTitle.getText().toString().trim(); 
			ep.videoUrl = etUrl.getText().toString().trim(); // FIX: episodeUrl -> videoUrl
			ep.thumbnail = etThumb.getText().toString().trim(); // FIX: thumbnailUrl -> thumbnail

			refreshEpisodeList(); // MUNA. Wala pa tayong saveEpisodesToFirebase
			Toast.makeText(this, "Episode Updated", Toast.LENGTH_SHORT).show(); 
		}); 
		builder.setNegativeButton("CANCEL", null); 
		builder.show(); 
	}

    
    private void loadExistingEpisodesToTemp(String videoId){
        referenceVideos.child(videoId).child("seasons").addListenerForSingleValueEvent(new ValueEventListener() {
				@Override public void onDataChange(@NonNull DataSnapshot snapshot) {
					for(DataSnapshot seasonSnap : snapshot.getChildren()){
						String seasonKey = seasonSnap.getKey();
						List<Episode> epList = new ArrayList<>();
						for(DataSnapshot epSnap : seasonSnap.getChildren()){
							epList.add(epSnap.getValue(Episode.class));
						}
						tempEpisodesMap.put(seasonKey, epList);
					}
					refreshEpisodeList();
				}
				@Override public void onCancelled(@NonNull DatabaseError error) {}
			});
    }

    

    private void setupDropdowns(AutoCompleteTextView acCategory, AutoCompleteTextView acType, AutoCompleteTextView acSeason){
        String[] categories = {"Movies", "TV series", "IPTV"};
        acCategory.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, categories));
        String[] types = {"video", "embed", "m3u8", "mp4"};
        acType.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, types));
        String[] seasons = new String[15];
        for(int i = 0; i < 15; i++) seasons[i] = "Season " + (i+1);
        acSeason.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, seasons));
        acSeason.setText("Season 1", false);
    }

    private void confirmDelete(String videoId){
        new AlertDialog.Builder(this)
			.setTitle("Delete Video")
			.setMessage("Are you sure?")
			.setPositiveButton("Yes", (dialog, which) -> { referenceVideos.child(videoId).removeValue(); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); })
		.setNegativeButton("No", null).show();
    }
}
