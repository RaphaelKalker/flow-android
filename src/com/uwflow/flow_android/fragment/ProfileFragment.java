package com.uwflow.flow_android.fragment;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.astuetz.PagerSlidingTabStrip;
import com.facebook.*;
import com.facebook.model.GraphObject;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.uwflow.flow_android.MainFlowActivity;
import com.uwflow.flow_android.R;
import com.uwflow.flow_android.adapters.ProfilePagerAdapter;
import com.uwflow.flow_android.constant.Constants;
import com.uwflow.flow_android.db_object.User;
import com.uwflow.flow_android.loaders.UserMeLoader;
import com.uwflow.flow_android.network.FlowApiRequestCallbackAdapter;
import com.uwflow.flow_android.network.FlowApiRequests;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class ProfileFragment extends Fragment implements LoaderManager.LoaderCallbacks<List<User>> {
    private String mProfileID;
    private ProfilePagerAdapter mProfilePagerAdapter;

    protected ImageView userImage;
    protected TextView userName;
    protected TextView userProgram;
    protected ViewPager viewPager;
    protected PagerSlidingTabStrip tabs;
    private Bitmap imageBitmap;
    private ImageView mCoverPhoto;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView =  inflater.inflate(R.layout.profile_layout, container, false);
        userImage = (ImageView) rootView.findViewById(R.id.user_image);
        userName = (TextView) rootView.findViewById(R.id.user_name);
	    userProgram = (TextView) rootView.findViewById(R.id.user_program);
	    viewPager = (ViewPager) rootView.findViewById(R.id.pager);
        mCoverPhoto = (ImageView) rootView.findViewById(R.id.cover_photo);

	    mProfilePagerAdapter = new ProfilePagerAdapter(getChildFragmentManager(), getArguments());

	    viewPager.setAdapter(mProfilePagerAdapter);
	    tabs = (PagerSlidingTabStrip) rootView.findViewById(R.id.pager_tabs);
	    tabs.setViewPager(viewPager);
        // Set default tab to Schedule
        if (getArguments() != null) {
            viewPager.setCurrentItem(getArguments().getInt(Constants.TAB_ID, Constants.PROFILE_SCHEDULE_PAGE_INDEX));
        }

	    mProfileID = getArguments() != null ? getArguments().getString(Constants.PROFILE_ID_KEY) : null;

	    if (mProfileID == null) {
	    // Load logged-in users profile if an ID is unspecified.
	        getLoaderManager().initLoader(Constants.LoaderManagerId.PROFILE_LOADER_ID, null, this);
	    }
	    return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (imageBitmap != null) userImage.setImageBitmap(imageBitmap);

        fetchProfileInfo(mProfileID);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public Loader<List<User>> onCreateLoader(int i, Bundle bundle) {
        return new UserMeLoader(getActivity(), ((MainFlowActivity)getActivity()).getHelper());
    }

    @Override
    public void onLoadFinished(Loader<List<User>> listLoader, List<User> users) {
        if (!users.isEmpty()){
            User me = users.get(0);
            userName.setText(me.getName());
            userProgram.setText(me.getProgramName());
            Picasso.with(getActivity()).load(me.getProfilePicUrls().getLarge()).into(new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
                        userImage.setImageBitmap(bitmap);
                }

                @Override
                public void onBitmapFailed(Drawable drawable) {

                }

                @Override
                public void onPrepareLoad(Drawable drawable) {

                }
            });

            // fetch and set cover photo
            fetchCoverPhoto(me.getFbid());

        }
    }

    @Override
    public void onLoaderReset(Loader<List<User>> listLoader) {

    }

    private void fetchProfileInfo(String id){
        if (id == null) return;

        FlowApiRequests.getUser(
                id,
                new FlowApiRequestCallbackAdapter() {
                    @Override
                    public void getUserCallback(User user) {

                        // fetch and set cover photo
                        fetchCoverPhoto(user.getFbid());

                        // Set profile picture
                        Picasso.with(getActivity()).load(user.getProfilePicUrls().getLarge()).into(userImage);

                        userName.setText(user.getName());
                        userProgram.setText(user.getProgramName());

                    }
                });
    }


    private void fetchCoverPhoto(long fbid) {
        Bundle params = new Bundle();
        params.putString("fields", "cover");
        new Request(
                Session.getActiveSession(),
                "/" + fbid,
                params,
                HttpMethod.GET,
                new Request.Callback() {
                    public void onCompleted(Response response) {
                        GraphObject graphObject = response.getGraphObject();
                        FacebookRequestError error = response.getError();
                        if (graphObject != null) {
                            if (graphObject.getProperty("cover") != null) {
                                try {
                                    JSONObject json = graphObject.getInnerJSONObject();
                                    JSONObject coverObject =
                                            new JSONObject((String) json.getString("cover"));
                                    String url = coverObject.getString("source");
                                    Picasso.with(getActivity()).load(url).into(mCoverPhoto);
                                } catch (JSONException e) {
                                }


                            } else if (error != null) {
                            }
                        }
                    }
                }
        ).executeAsync();
    }


}
